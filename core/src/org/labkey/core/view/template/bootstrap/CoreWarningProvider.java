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
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.JavaVersion;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.impersonation.AbstractImpersonationContextFactory;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.HttpsUtil;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;
import org.labkey.core.metrics.WebSocketConnectionManager;
import org.labkey.core.user.LimitActiveUsersSettings;

import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.labkey.api.view.template.WarningService.SESSION_WARNINGS_BANNER_KEY;

public class CoreWarningProvider implements WarningProvider
{
    private static final boolean SHOW_ALL_WARNINGS = WarningService.get().showAllWarnings();

    public CoreWarningProvider()
    {
        AbstractImpersonationContextFactory.registerSessionAttributeToStash(SESSION_WARNINGS_BANNER_KEY);
    }

    @Override
    public void addStaticWarnings(@NotNull Warnings warnings)
    {
        // Warn if running on a deprecated database version or some other non-fatal database configuration issue
        DbScope labkeyScope = DbScope.getLabKeyScope();
        labkeyScope.getSqlDialect().addAdminWarningMessages(warnings);

        getHeapSizeWarnings(warnings);

        getConnectionPoolSizeWarnings(warnings, labkeyScope);

        getJavaWarnings(warnings);

        getTomcatWarnings(warnings);
    }

    @Override
    public void addDynamicWarnings(@NotNull Warnings warnings, @NotNull ViewContext context)
    {
        if (context.getUser().hasSiteAdminPermission())
        {
            getUserRequestedAdminOnlyModeWarnings(warnings);

            getModuleErrorWarnings(warnings, context);

            getProbableLeakCountWarnings(warnings);

            getWebSocketConnectionWarnings(warnings);

            //upgrade message--show to admins
            HtmlString upgradeMessage = UsageReportingLevel.getUpgradeMessage();

            if (null == upgradeMessage && SHOW_ALL_WARNINGS)
            {
                // Mock upgrade message for testing
                upgradeMessage = HtmlStringBuilder.of("You really ought to upgrade this server! ").append(new LinkBuilder("Click here!").href(AppProps.getInstance().getHomePageActionURL()).clearClasses()).getHtmlString();
            }

            if (null != upgradeMessage)
            {
                warnings.add(upgradeMessage);
            }
        }

        HtmlString warning = LimitActiveUsersSettings.getWarningMessage(context.getContainer(), context.getUser(), SHOW_ALL_WARNINGS);
        if (null != warning)
            warnings.add(warning);

        if (AppProps.getInstance().isShowRibbonMessage() && !StringUtils.isEmpty(AppProps.getInstance().getRibbonMessageHtml()))
        {
            String message = AppProps.getInstance().getRibbonMessageHtml();
            message = ModuleHtmlView.replaceTokens(message, context);
            warnings.add(HtmlString.unsafe(message));  // We trust that the site admin has provided valid HTML
        }
        else if (SHOW_ALL_WARNINGS)
        {
            warnings.add(HtmlString.of("Here is a sample ribbon message."));
        }
    }

    private void getHeapSizeWarnings(Warnings warnings)
    {
        // Issue 9683 - show admins warning about inadequate heap size (< 2GB)
        MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
        long maxMem = membean.getHeapMemoryUsage().getMax();

        // Issue 45171 - have a little slop since -Xmx2G ends up with slightly different sized heaps on different VMs
        if (SHOW_ALL_WARNINGS || maxMem > 0 && maxMem < 2_000_000_000L)
        {
            HtmlStringBuilder html = HtmlStringBuilder.of("The maximum amount of heap memory allocated to LabKey Server is too low (less than 2GB). LabKey recommends ");
            html.append(new HelpTopic("configWebappMemory").getSimpleLinkHtml("setting the maximum heap to at least 2 gigabytes (-Xmx2G) on test/evaluation servers and at least 4 gigabytes (-Xmx4G) on production servers"));
            html.append(".");
            warnings.add(html);
        }
    }

    // Warn if running in production mode with an inadequate labkey db connection pool size
    private void getConnectionPoolSizeWarnings(Warnings warnings, DbScope labkeyScope)
    {
        if (SHOW_ALL_WARNINGS || !AppProps.getInstance().isDevMode())
        {
            Integer maxTotal = labkeyScope.getDataSourceProperties().getMaxTotal();

            if (null == maxTotal)
            {
                warnings.add(HtmlString.of("Could not determine the connection pool size for the labkeyDataSource; verify that the connection pool is properly configured in labkey.xml"));
            }
            else if (SHOW_ALL_WARNINGS || maxTotal < 20)
            {
                addStandardWarning(warnings, "The configured labkeyDataSource connection pool size (" + maxTotal + ") is too small for a production server. Update the configuration and restart the server.", "troubleshootingAdmin#pool", "Connection Pool Size section of the Troubleshooting page");
            }
        }
    }

    private void getJavaWarnings(Warnings warnings)
    {
        if (SHOW_ALL_WARNINGS || ModuleLoader.getInstance().getJavaVersion().isDeprecated())
        {
            String javaInfo = JavaVersion.getJavaVersionDescription();
            addStandardWarning(warnings, "The deployed version of Java, " + javaInfo + ", is not supported. We recommend installing " + JavaVersion.getRecommendedJavaVersion() + ".", "supported", "Supported Technologies page");
        }
    }

    private Set<String> collectAllDeployedApps()
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
                    final var instances = server.queryNames(new ObjectName("Catalina:j2eeType=WebModule,*"), null);
                    for (ObjectName each : instances)
                        result.add(substringAfterLast(each.getKeyProperty("name"), '/'));
                }
            }
        }
        catch (MalformedObjectNameException x)
        {
            // pass
        }
        return result;
    }

    private void getTomcatWarnings(Warnings warnings)
    {
        if (SHOW_ALL_WARNINGS || ModuleLoader.getInstance().getTomcatVersion().isDeprecated())
        {
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();
            addStandardWarning(warnings, "The deployed version of Tomcat, " + serverInfo + ", is not supported.", "supported", "Supported Technologies page");
        }

        try
        {
            Set<String> deployedWebapps = collectAllDeployedApps();
            deployedWebapps.remove(StringUtils.strip(AppProps.getInstance().getContextPath(),"/"));
            boolean defaultTomcatWebappFound = deployedWebapps.stream().anyMatch(webapp ->
                StringUtils.startsWithIgnoreCase(webapp,"docs") ||
                StringUtils.startsWithIgnoreCase(webapp,"host-manager") ||
                StringUtils.startsWithIgnoreCase(webapp,"examples") ||
                StringUtils.startsWithIgnoreCase(webapp,"manager")
            );

            if (SHOW_ALL_WARNINGS || (defaultTomcatWebappFound && !AppProps.getInstance().isDevMode()))
                addStandardWarning(warnings, "This server appears to be running with one or more default Tomcat web applications that should be removed. These may include 'docs', 'examples', 'host-manager', and 'manager'.", "configTomcat", "Tomcat Configuration");
        }
        catch (Exception x)
        {
            LogHelper.getLogger(CoreWarningProvider.class, "core warning provider").warn("Exception encountered while verifying Tomcat configuration", x);
        }
    }

    private void getModuleErrorWarnings(Warnings warnings, ViewContext context)
    {
        //module failures during startup--show to admins
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        if (SHOW_ALL_WARNINGS || null != moduleFailures && moduleFailures.size() > 0)
        {
            if (SHOW_ALL_WARNINGS && moduleFailures.isEmpty())
            {
                // Mock failures for testing purposes
                moduleFailures = Map.of("core", new Throwable(), "flow",  new Throwable());
            }
            addStandardWarning(warnings, "The following modules experienced errors during startup:", moduleFailures.keySet().toString(), PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(context.getContainer()));
        }
    }

    private void getWebSocketConnectionWarnings(Warnings warnings)
    {
        if (SHOW_ALL_WARNINGS || WebSocketConnectionManager.getInstance().showWarning())
            addStandardWarning(warnings, "The WebSocket connection failed. LabKey Server uses WebSockets to send notifications and alert users when their session ends.", "configTomcat#websocket", "Tomcat Configuration");
    }

    private void getUserRequestedAdminOnlyModeWarnings(Warnings warnings)
    {
        //admin-only mode--show to admins
        if (SHOW_ALL_WARNINGS || AppProps.getInstance().isUserRequestedAdminOnlyMode())
        {
            addStandardWarning(warnings, "This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode via the", "site settings page", PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL());
        }
    }

    private void getProbableLeakCountWarnings(Warnings warnings)
    {
        if (AppProps.getInstance().isDevMode())
        {
            int leakCount = ConnectionWrapper.getProbableLeakCount();
            if (SHOW_ALL_WARNINGS || leakCount > 0)
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
