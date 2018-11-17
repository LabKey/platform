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
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.AbstractDismissibleWarningMessageImpl;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.Warnings;
import org.labkey.core.CoreController;

import javax.servlet.http.HttpSession;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CoreWarningProvider extends AbstractDismissibleWarningMessageImpl implements WarningProvider
{
    public static String SHOW_BANNER_KEY = CoreWarningProvider.class.getName() + "$SHOW_BANNER_KEY";

    @Override
    public void addStaticWarnings(Warnings warnings)
    {
        //FIX: 9683
        //show admins warning about inadequate heap size (<= 256Mb)
        MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
        long maxMem = membean.getHeapMemoryUsage().getMax();

        if (maxMem > 0 && maxMem <= 256*1024*1024)
        {
            warnings.add("The maximum amount of heap memory allocated to LabKey Server is too low (256M or less). " +
                    "LabKey recommends " +
                    new HelpTopic("configWebappMemory").getSimpleLinkHtml("setting the maximum heap to at least one gigabyte (-Xmx1024M)")
                    + ".");
        }

        // Warn if running on a deprecated database version or some other non-fatal database configuration issue
        DbScope labkeyScope = DbScope.getLabKeyScope();
        labkeyScope.getSqlDialect().addAdminWarningMessages(warnings);

        // Warn if running in production mode with an inadequate labkey db connection pool size
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

        if (ModuleLoader.getInstance().getTomcatVersion().isDeprecated())
        {
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();
            HelpTopic topic = new HelpTopic("supported");
            warnings.add("The deployed version of Tomcat, " + serverInfo + ", is not supported." +
                    " See the " + topic.getSimpleLinkHtml("Supported Technologies page") + " for more information.");
        }
    }

    @Override
    public void addDismissibleWarnings(Warnings warnings, ViewContext context)
    {
        if (showMessage(context))
        {
            String messageHtml = getMessageHtml(context);
            if (!StringUtils.isEmpty(messageHtml))
                warnings.add(messageHtml);
        }
    }

    @Override
    protected String getDismissActionUrl(ViewContext viewContext)
    {
        return new ActionURL(CoreController.DismissCoreWarningsAction.class, viewContext.getContainer()).toString();
    }

    @Override
    protected String getMessageText(ViewContext viewContext)
    {
        List<String> messages = new ArrayList<>();
        User user = viewContext.getUser();

        if (null != user && user.isInSiteAdminGroup())
        {
            //admin-only mode--show to admins
            if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
            {
                messages.add("This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode via the <a href=\""
                        + PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL()
                        + "\">"
                        + "site settings page</a>.");
            }

            //module failures during startup--show to admins
            Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
            if (null != moduleFailures && moduleFailures.size() > 0)
            {
                messages.add("The following modules experienced errors during startup: "
                        + "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(viewContext.getContainer()) + "\">"
                        + PageFlowUtil.filter(moduleFailures.keySet())
                        + "</a>");
            }

            //upgrade message--show to admins
            String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
            if (StringUtils.isNotEmpty(upgradeMessage))
            {
                messages.add(upgradeMessage);
            }
        }

        if (AppProps.getInstance().isDevMode())
        {
            int leakCount = ConnectionWrapper.getProbableLeakCount();
            if (leakCount > 0)
            {
                int count = ConnectionWrapper.getActiveConnectionCount();
                String connectionsInUse = "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getMemTrackerURL() + "\">" + count + " DB connection" + (count == 1 ? "" : "s") + " in use.";
                connectionsInUse += " " + leakCount + " probable leak" + (leakCount == 1 ? "" : "s") + ".</a>";
                messages.add(connectionsInUse);
            }
        }

        if (AppProps.getInstance().isShowRibbonMessage() && !StringUtils.isEmpty(AppProps.getInstance().getRibbonMessageHtml()))
        {
            String message = AppProps.getInstance().getRibbonMessageHtml();
            message = ModuleHtmlView.replaceTokens(message, viewContext);
            messages.add(message);
        }

        if (messages.isEmpty())
            return null;
        if (messages.size() > 1)
        {
            StringBuilder ret = new StringBuilder();
            ret.append("<ul>");
            messages.forEach(m -> ret.append("<li>").append(m).append("</li>"));
            ret.append("</ul>");
            return ret.toString();
        }
        return messages.get(0);
    }

    @Override
    public boolean showMessage(ViewContext viewContext)
    {
        if (viewContext != null && viewContext.getRequest() != null)
        {
            HttpSession session = viewContext.getRequest().getSession(true);
            if (session.getAttribute(SHOW_BANNER_KEY) == null)
                session.setAttribute(SHOW_BANNER_KEY, true);

            return (boolean) session.getAttribute(SHOW_BANNER_KEY);

        }

        return false;
    }
}
