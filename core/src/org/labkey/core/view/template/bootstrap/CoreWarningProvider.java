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
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.Warnings;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;

public class CoreWarningProvider implements WarningProvider
{
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

        if (!ModuleLoader.getInstance().getTomcatVersion().isSupported())
        {
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();
            HelpTopic topic = new HelpTopic("supported");
            warnings.add("The deployed version of Tomcat, " + serverInfo + ", is not supported." +
                    " See the " + topic.getSimpleLinkHtml("Supported Technologies page") + " for more information.");
        }
    }

    @Override
    public void addWarnings(Warnings warnings, ViewContext context)
    {
        User user = context.getUser();

        if (null != user && user.isInSiteAdminGroup())
        {
            //admin-only mode--show to admins
            if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
            {
                warnings.add("This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode via the <a href=\""
                        + PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL()
                        + "\">"
                        + "site settings page</a>.");
            }

            //module failures during startup--show to admins
            Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
            if (null != moduleFailures && moduleFailures.size() > 0)
            {
                warnings.add("The following modules experienced errors during startup: "
                        + "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(context.getContainer()) + "\">"
                        + PageFlowUtil.filter(moduleFailures.keySet())
                        + "</a>");
            }

            //upgrade message--show to admins
            String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
            if (StringUtils.isNotEmpty(upgradeMessage))
            {
                warnings.add(upgradeMessage);
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
                warnings.add(connectionsInUse);
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
