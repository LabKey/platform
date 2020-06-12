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
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.JavaVersion;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.impersonation.AbstractImpersonationContextFactory;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;

import static org.labkey.api.view.template.WarningService.SESSION_WARNINGS_BANNER_KEY;

public class CoreWarningProvider implements WarningProvider
{
    private static final boolean SHOW_ALL_WARNINGS = WarningService.get().showAllWarnings();

    public CoreWarningProvider()
    {
        AbstractImpersonationContextFactory.registerSessionAttributeToStash(SESSION_WARNINGS_BANNER_KEY);
    }

    @Override
    public void addStaticWarnings(Warnings warnings)
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
    public void addDynamicWarnings(Warnings warnings, ViewContext context)
    {
        if (context != null && context.getRequest() != null)
        {
            User user = context.getUser();
            if (null != user && user.hasSiteAdminPermission())
            {
                getUserRequestedAdminOnlyModeWarnings(warnings);

                getModuleErrorWarnings(warnings, context);

                getProbableLeakCountWarnings(warnings);

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

            if (AppProps.getInstance().isShowRibbonMessage() && !StringUtils.isEmpty(AppProps.getInstance().getRibbonMessageHtml()))
            {
                String message = AppProps.getInstance().getRibbonMessageHtml();
                message = ModuleHtmlView.replaceTokens(message, context);
                warnings.add(HtmlString.unsafe(message));  // We trust that the site admin has provided valid HTML
            }
        }
    }

    private void getHeapSizeWarnings(Warnings warnings)
    {
        // Issue 9683 - show admins warning about inadequate heap size (< 2GB)
        MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
        long maxMem = membean.getHeapMemoryUsage().getMax();

        if (SHOW_ALL_WARNINGS || maxMem > 0 && maxMem < 2*1024*1024*1024L)
        {
            HtmlStringBuilder html = HtmlStringBuilder.of("The maximum amount of heap memory allocated to LabKey Server is too low (less than 2GB). LabKey recommends ");
            html.append(new HelpTopic("configWebappMemory").getSimpleLinkHtml("setting the maximum heap to at least 2 gigabytes (-Xmx2G) on test/evaluation servers and at least 4 gigabytes (-Xmx4G) on production servers"));
            html.append(".");
            warnings.add(html.getHtmlString());
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

    private void getTomcatWarnings(Warnings warnings)
    {
        if (SHOW_ALL_WARNINGS || ModuleLoader.getInstance().getTomcatVersion().isDeprecated())
        {
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();
            addStandardWarning(warnings, "The deployed version of Tomcat, " + serverInfo + ", is not supported.", "supported", "Supported Technologies page");
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
                warnings.add(html.getHtmlString());
            }
        }
    }

    // Standard warning with a link to another page on the server
    private void addStandardWarning(Warnings warnings, String prefix, String linkText, ActionURL url)
    {
        HtmlStringBuilder html = HtmlStringBuilder.of(prefix + " ");
        html.append(new LinkBuilder(linkText).href(url).clearClasses());
        html.append(".");
        warnings.add(html.getHtmlString());
    }

    // Standard warning with a link to a LabKey documentation page
    private void addStandardWarning(Warnings warnings, String prefix, String helpTopic, String helpText)
    {
        HtmlStringBuilder html = HtmlStringBuilder.of(prefix + " See the ");
        HelpTopic topic = new HelpTopic(helpTopic);
        html.append(topic.getSimpleLinkHtml(helpText));
        html.append(" for more information.");
        warnings.add(html.getHtmlString());
    }
}
