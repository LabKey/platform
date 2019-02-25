/*
 * Copyright (c) 2018 LabKey Corporation
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.Warnings;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;

import static org.labkey.api.view.template.PageConfig.SESSION_WARNINGS_BANNER_KEY;

public class CoreWarningProvider implements WarningProvider
{
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
    public void addDismissibleWarnings(Warnings warnings, ViewContext context)
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
                String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
                if (StringUtils.isNotEmpty(upgradeMessage))
                {
                    warnings.add(upgradeMessage);
                }
            }

            if (AppProps.getInstance().isShowRibbonMessage() && !StringUtils.isEmpty(AppProps.getInstance().getRibbonMessageHtml()))
            {
                String message = AppProps.getInstance().getRibbonMessageHtml();
                message = ModuleHtmlView.replaceTokens(message, context);
                warnings.add(message);
            }
        }
    }

    private void getHeapSizeWarnings(Warnings warnings)
    {
        //FIX: 9683
        //show admins warning about inadequate heap size (<= 1GB)
        MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
        long maxMem = membean.getHeapMemoryUsage().getMax();

        if (maxMem > 0 && maxMem < 1024*1024*1024)
        {
            warnings.add("The maximum amount of heap memory allocated to LabKey Server is too low (less than 1GB). " +
                    "LabKey recommends " +
                    new HelpTopic("configWebappMemory").getSimpleLinkHtml("setting the maximum heap to at least 2 gigabytes (-Xmx2G) on test/evaluation servers and at least 4 gigabytes (-Xmx4G) on production servers")
                    + ".");
        }
    }

    // Warn if running in production mode with an inadequate labkey db connection pool size
    private void getConnectionPoolSizeWarnings(Warnings warnings, DbScope labkeyScope)
    {
        if (!AppProps.getInstance().isDevMode())
        {
            Integer maxTotal = labkeyScope.getDataSourceProperties().getMaxTotal();

            if (null == maxTotal)
            {
                warnings.add("Could not determine the connection pool size for the labkeyDataSource; verify that the connection pool is properly configured in labkey.xml");
            }
            else if (maxTotal < 20)
            {
                HelpTopic topic = new HelpTopic("troubleshootingAdmin#pool");
                warnings.add("The configured labkeyDataSource connection pool size (" + maxTotal + ") is too small for a production server. Update the configuration and restart the server. " +
                        "See the " + topic.getSimpleLinkHtml("Connection Pool Size section of the Troubleshooting page") + " for more information.");
            }
        }
    }

    private void getJavaWarnings(Warnings warnings)
    {
        if (ModuleLoader.getInstance().getJavaVersion().isDeprecated())
        {
            String javaInfo = JavaVersion.getJavaVersionDescription();
            HelpTopic topic = new HelpTopic("supported");
            warnings.add("The deployed version of Java, " + javaInfo + ", is not supported. We recommend installing " + JavaVersion.getRecommendedJavaVersion() +
                    ". See the " + topic.getSimpleLinkHtml("Supported Technologies page") + " for more information.");
        }
    }

    private void getTomcatWarnings(Warnings warnings)
    {
        if (ModuleLoader.getInstance().getTomcatVersion().isDeprecated())
        {
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();
            HelpTopic topic = new HelpTopic("supported");
            warnings.add("The deployed version of Tomcat, " + serverInfo + ", is not supported." +
                    " See the " + topic.getSimpleLinkHtml("Supported Technologies page") + " for more information.");
        }
    }

    private void getModuleErrorWarnings(Warnings warnings, ViewContext context)
    {
        //module failures during startup--show to admins
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        if (null != moduleFailures && moduleFailures.size() > 0)
        {
            warnings.add("The following modules experienced errors during startup: "
                    + "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(context.getContainer()) + "\">"
                    + PageFlowUtil.filter(moduleFailures.keySet())
                    + "</a>");
        }
    }

    private void getUserRequestedAdminOnlyModeWarnings(Warnings warnings)
    {
        //admin-only mode--show to admins
        if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
        {
            warnings.add("This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode via the <a href=\""
                    + PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL()
                    + "\">"
                    + "site settings page</a>.");
        }
    }

    private void getProbableLeakCountWarnings(Warnings warnings)
    {
        if (AppProps.getInstance().isDevMode())
        {
            int leakCount = ConnectionWrapper.getProbableLeakCount();
            if (leakCount > 0)
            {
                int count = ConnectionWrapper.getActiveConnectionCount();
                String connectionsInUse = "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getMemTrackerURL() + "\">" + count + " DB connection" + (count == 1 ? "" : "s") + " in use.";
                connectionsInUse += " " + leakCount + " probable leak" + (leakCount == 1 ? "" : "s") + ".</a>";
                warnings.add(connectionsInUse);
            }
        }
    }
}
