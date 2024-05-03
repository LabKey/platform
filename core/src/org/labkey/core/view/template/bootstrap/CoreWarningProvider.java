/*
 * Copyright (c) 2018-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.view.template.bootstrap;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.TableXmlUtils;
import org.labkey.api.admin.sitevalidation.SiteValidationResult;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.JavaVersion;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.DbLoginService;
import org.labkey.api.security.impersonation.AbstractImpersonationContextFactory;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.security.permissions.TroubleshooterPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.Warnings;
import org.labkey.core.admin.AdminController;
import org.labkey.core.metrics.WebSocketConnectionManager;
import org.labkey.core.user.LimitActiveUsersSettings;

import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.labkey.api.view.template.WarningService.SESSION_WARNINGS_BANNER_KEY;

public class CoreWarningProvider implements WarningProvider
{
    /** Schema name -> problem list */
    private final Map<String, List<SiteValidationResult>> _dbSchemaWarnings = new ConcurrentHashMap<>();
    private static Set<String> _deployedApps;

    public CoreWarningProvider()
    {
        AbstractImpersonationContextFactory.registerSessionAttributeToStash(SESSION_WARNINGS_BANNER_KEY);
    }

    public void startSchemaCheck(int delaySeconds)
    {
        // Issue 46264 - proactively check all DB schemas against the schema XML
        JobRunner.getDefault().execute(TimeUnit.SECONDS.toMillis(delaySeconds), () ->
        {
            _dbSchemaWarnings.clear();

            for (DbSchema schema : DbSchema.getAllSchemasToTest())
            {
                var schemaWarnings = TableXmlUtils.compareXmlToMetaData(schema, false, false, true);
                if (schemaWarnings.hasErrors())
                {
                    _dbSchemaWarnings.put(schema.getName(), schemaWarnings.getResults());
                }
            }
        });
    }

    @Override
    public void addStaticWarnings(@NotNull Warnings warnings, boolean showAllWarnings)
    {
        // Warn if running on a deprecated database version or some other non-fatal database configuration issue
        DbScope labkeyScope = DbScope.getLabKeyScope();
        labkeyScope.getSqlDialect().addAdminWarningMessages(warnings, showAllWarnings);

        getHeapSizeWarnings(warnings, showAllWarnings);

        getConnectionPoolSizeWarnings(warnings, labkeyScope, showAllWarnings);

        getJavaWarnings(warnings, showAllWarnings);

        getTomcatWarnings(warnings, showAllWarnings);
    }

    @Override
    public void addDynamicWarnings(@NotNull Warnings warnings, @Nullable ViewContext context, boolean showAllWarnings)
    {
        if (MothershipReport.shouldReceiveMarketingUpdates(MothershipReport.getDistributionName()))
        {
            if (UsageReportingLevel.getMarketingUpdate() != null)
                warnings.add(UsageReportingLevel.getMarketingUpdate());
        }

        if (context == null || context.getUser().hasRootPermission(TroubleshooterPermission.class))
        {
            getUserRequestedAdminOnlyModeWarnings(warnings, showAllWarnings, context == null || context.getUser().hasSiteAdminPermission());

            getModuleErrorWarnings(warnings, showAllWarnings);

            getProbableLeakCountWarnings(warnings, showAllWarnings);

            getWebSocketConnectionWarnings(warnings, showAllWarnings);

            getDbSchemaWarnings(warnings, showAllWarnings);

            getPasswordRuleWarnings(warnings, showAllWarnings);
        }

        // Issue 50015 - only show upgrade message to full site admins
        if (context == null || context.getUser().hasRootPermission(SiteAdminPermission.class))
        {
            HtmlString upgradeMessage = UsageReportingLevel.getUpgradeMessage();

            if (null == upgradeMessage && showAllWarnings)
            {
                // Mock upgrade message for testing
                upgradeMessage = HtmlStringBuilder.of("You really ought to upgrade this server! ").append(new LinkBuilder("Click here!").href(AppProps.getInstance().getHomePageActionURL()).clearClasses()).getHtmlString();
            }

            if (null != upgradeMessage)
            {
                warnings.add(upgradeMessage);
            }
        }

        if (context != null)
        {
            HtmlString warning = LimitActiveUsersSettings.getWarningMessage(context.getContainer(), context.getUser(), showAllWarnings);
            if (null != warning)
                warnings.add(warning);

            if (AppProps.getInstance().isShowRibbonMessage() && !StringUtils.isEmpty(AppProps.getInstance().getRibbonMessage()))
            {
                String message = AppProps.getInstance().getRibbonMessage();
                message = ModuleHtmlView.replaceTokens(message, context);
                warnings.add(HtmlString.unsafe(message));  // We trust that the site admin has provided valid HTML
            }
            else if (showAllWarnings)
            {
                warnings.add(HtmlString.of("Here is a sample ribbon message."));
            }
        }
    }

    private static final int MAX_SCHEMA_PROBLEMS_TO_SHOW = 3;

    private void getDbSchemaWarnings(Warnings warnings, boolean showAllWarnings)
    {
        Map<String, List<SiteValidationResult>> schemaProblems = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        schemaProblems.putAll(_dbSchemaWarnings);
        if (showAllWarnings)
        {
            schemaProblems.put("WorstSchemaEver", List.of(SiteValidationResult.Level.ERROR.create("Your schema is very, very bad")));
        }

        int count = 0;
        for (var entry : schemaProblems.entrySet())
        {
            for (SiteValidationResult result : entry.getValue())
            {
                if (count == 0)
                {
                    addStandardWarning(warnings, (schemaProblems.size() == 1 ? "One database schema is" : (schemaProblems.size() + " database schemas are")) + " not as expected. This indicates there was a serious problem with the upgrade process. Adjust the module's schema or metadata, or contact LabKey support.", "sqlScripts", "docs for help with upgrade scripts");
                }
                if (++count <= MAX_SCHEMA_PROBLEMS_TO_SHOW)
                {
                    warnings.add(HtmlString.of("Problem with '" + entry.getKey() + "' schema. " + result.getMessage()));
                }
            }
        }

        if (count > MAX_SCHEMA_PROBLEMS_TO_SHOW)
        {
            addStandardWarning(warnings, (count - MAX_SCHEMA_PROBLEMS_TO_SHOW) + " additional schema problems.", "View full consistency check", new ActionURL(AdminController.DoCheckAction.class, ContainerManager.getRoot()));
        }
    }

    private void getPasswordRuleWarnings(Warnings warnings, boolean showAllWarnings)
    {
        if (showAllWarnings || (!AppProps.getInstance().isDevMode() && DbLoginService.get().getPasswordRule().isDeprecated()))
            warnings.add(HtmlString.of("Database authentication is configured with \"" + DbLoginService.get().getPasswordRule().name() + "\" strength, which is not appropriate for production deployments. This option will be removed in the next major release."));
    }

    private void getHeapSizeWarnings(Warnings warnings, boolean showAllWarnings)
    {
        // Issue 9683 - show admins warning about inadequate heap size (< 2GB)
        MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
        long maxMem = membean.getHeapMemoryUsage().getMax();

        // Issue 45171 - have a little slop since -Xmx2G ends up with slightly different sized heaps on different VMs
        if (showAllWarnings || maxMem > 0 && maxMem < 2_000_000_000L)
        {
            HtmlStringBuilder html = HtmlStringBuilder.of("The maximum amount of heap memory allocated to LabKey Server is too low (less than 2GB). LabKey recommends ");
            html.append(new HelpTopic("configWebappMemory").getSimpleLinkHtml("setting the maximum heap to at least 2 gigabytes (-Xmx2G) on test/evaluation servers and at least 4 gigabytes (-Xmx4G) on production servers"));
            html.append(".");
            warnings.add(html);
        }
    }

    // Warn if running in production mode with an inadequate labkey db connection pool size
    private void getConnectionPoolSizeWarnings(Warnings warnings, DbScope labkeyScope, boolean showAllWarnings)
    {
        if (showAllWarnings || !AppProps.getInstance().isDevMode())
        {
            Integer maxTotal = labkeyScope.getDataSourceProperties().getMaxTotal();

            if (null == maxTotal)
            {
                warnings.add(HtmlString.of("Could not determine the connection pool size for the labkeyDataSource; verify that the connection pool is properly configured in application.properties"));
            }
            else if (showAllWarnings || maxTotal < 20)
            {
                addStandardWarning(warnings, "The configured labkeyDataSource connection pool size (" + maxTotal + ") is too small for a production server. Update the configuration and restart the server.", "troubleshootingAdmin#pool", "Connection Pool Size section of the Troubleshooting page");
            }
        }
    }

    private void getJavaWarnings(Warnings warnings, boolean showAllWarnings)
    {
        if (showAllWarnings || ModuleLoader.getInstance().getJavaVersion().isDeprecated())
        {
            String javaInfo = JavaVersion.getJavaVersionDescription();
            addStandardWarning(warnings, "The deployed version of Java, " + javaInfo + ", is not supported. We recommend installing " + JavaVersion.getRecommendedJavaVersion() + ".", "supported", "Supported Technologies page");
        }
    }

    public static Set<String> collectAllDeployedApps()
    {
        if (_deployedApps == null)
        {
            var result = new HashSet<String>();
            try
            {
                var servers = MBeanServerFactory.findMBeanServer(null);
                for (var server : servers)
                {
                    for (var domain : server.getDomains())
                    {
                        if (!domain.equals("Catalina"))
                            continue;
                        final var instances = server.queryNames(new ObjectName("Tomcat:j2eeType=WebModule,*"), null);
                        for (ObjectName each : instances)
                            result.add(substringAfterLast(each.getKeyProperty("name"), '/'));
                    }
                }
                _deployedApps = Collections.unmodifiableSet(result);
            }
            catch (MalformedObjectNameException x)
            {
                // pass
            }
        }
        return _deployedApps;
    }

    private void getTomcatWarnings(Warnings warnings, boolean showAllWarnings)
    {
        if (showAllWarnings || ModuleLoader.getInstance().getTomcatVersion().isDeprecated())
        {
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();
            addStandardWarning(warnings, "The deployed version of Tomcat, " + serverInfo + ", is not supported.", "supported", "Supported Technologies page");
        }

        try
        {
            Set<String> deployedWebapps = new HashSet<>(collectAllDeployedApps());
            deployedWebapps.remove(StringUtils.strip(AppProps.getInstance().getContextPath(),"/"));
            boolean defaultTomcatWebappFound = deployedWebapps.stream().anyMatch(webapp ->
                StringUtils.startsWithIgnoreCase(webapp,"docs") ||
                StringUtils.startsWithIgnoreCase(webapp,"host-manager") ||
                StringUtils.startsWithIgnoreCase(webapp,"examples") ||
                StringUtils.startsWithIgnoreCase(webapp,"manager")
            );

            if (showAllWarnings || (defaultTomcatWebappFound && !AppProps.getInstance().isDevMode()))
                addStandardWarning(warnings, "This server appears to be running with one or more default Tomcat web applications that should be removed. These may include 'docs', 'examples', 'host-manager', and 'manager'.", "configTomcat", "Tomcat Configuration");
        }
        catch (Exception x)
        {
            LogHelper.getLogger(CoreWarningProvider.class, "core warning provider").warn("Exception encountered while verifying Tomcat configuration", x);
        }
    }

    private void getModuleErrorWarnings(Warnings warnings, boolean showAllWarnings)
    {
        //module failures during startup--show to admins
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        if (showAllWarnings || null != moduleFailures && !moduleFailures.isEmpty())
        {
            if (showAllWarnings && moduleFailures.isEmpty())
            {
                // Mock failures for testing purposes
                moduleFailures = Map.of("core", new Throwable(), "flow",  new Throwable());
            }
            addStandardWarning(warnings, "The following modules experienced errors during startup:", moduleFailures.keySet().toString(), PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL());
        }

        // Issue 46922 - check for and warn about unknown modules
        int unknownModules = ModuleLoader.getInstance().getUnknownModuleCount();

        if ((!AppProps.getInstance().isDevMode() && unknownModules > 0) || showAllWarnings)
            addStandardWarning(warnings, "This server is running with " +
                StringUtilsLabKey.pluralize(unknownModules, "unknown module") +
                ", modules that were previously installed but are no longer present. Unknown modules, particularly " +
                "those that create database schemas, can cause upgrade problems in the future. These modules and their " +
                "schemas should be deleted via the ", "Module Details page", PageFlowUtil.urlProvider(AdminUrls.class).getModulesDetailsURL());
    }

    private void getWebSocketConnectionWarnings(Warnings warnings, boolean showAllWarnings)
    {
        if (showAllWarnings || WebSocketConnectionManager.getInstance().showWarning())
            addStandardWarning(warnings, "The WebSocket connection failed. LabKey Server uses WebSockets to send notifications and alert users when their session ends.", "configTomcat#websocket", "Tomcat Configuration");
    }

    private void getUserRequestedAdminOnlyModeWarnings(Warnings warnings, boolean showAllWarnings, boolean isSiteAdmin)
    {
        //admin-only mode--show to admins
        if (showAllWarnings || AppProps.getInstance().isUserRequestedAdminOnlyMode())
        {
            String extraText = isSiteAdmin ? "" : " a site administrator will need to";
            addStandardWarning(warnings, "This site is configured so that only administrators and troubleshooters may sign in. To allow other users to sign in," + extraText + " turn off admin-only mode via the", "site settings page", PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL());
        }
    }

    private void getProbableLeakCountWarnings(Warnings warnings, boolean showAllWarnings)
    {
        if (AppProps.getInstance().isDevMode())
        {
            int leakCount = ConnectionWrapper.getProbableLeakCount();
            if (showAllWarnings || leakCount > 0)
            {
                int count = ConnectionWrapper.getActiveConnectionCount();
                HtmlStringBuilder html = HtmlStringBuilder.of(new LinkBuilder(count + " DB connection" + (count == 1 ? "" : "s") + " in use.").href(PageFlowUtil.urlProvider(AdminUrls.class).getMemTrackerURL()).clearClasses());
                html.append(" " + leakCount + " probable leak" + (leakCount == 1 ? "" : "s") + ".");
                warnings.add(html);
            }
        }
    }

    // Standard warning with a link to another page on the server
    private void addStandardWarning(Warnings warnings, String prefix, String linkText, ActionURL url)
    {
        HtmlStringBuilder html = HtmlStringBuilder.of(prefix + " ");
        html.append(new LinkBuilder(linkText).href(url).clearClasses());
        html.append(".");
        warnings.add(html);
    }

    // Standard warning with a link to a LabKey documentation page
    private void addStandardWarning(Warnings warnings, String prefix, String helpTopic, String helpText)
    {
        HtmlStringBuilder html = HtmlStringBuilder.of(prefix + " See the ");
        HelpTopic topic = new HelpTopic(helpTopic);
        html.append(topic.getSimpleLinkHtml(helpText));
        html.append(" for more information.");
        warnings.add(html);
    }
}
