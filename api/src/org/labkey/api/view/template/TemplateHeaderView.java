/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.api.view.template;

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.JspView;

import javax.servlet.http.HttpSession;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 29, 2007
 * Time: 9:51:54 AM
 */
public class TemplateHeaderView extends JspView<TemplateHeaderView.TemplateHeaderBean>
{
    public static String SHOW_WARNING_MESSAGES_SESSION_PROP = "hideWarningMessages";

    private List<String> _warningMessages = new ArrayList<>();

    public TemplateHeaderView(@Nullable String upgradeMessage, @Nullable Map<String, Throwable> moduleErrors, PageConfig page)
    {
        super("/org/labkey/api/view/template/header.jsp", new TemplateHeaderBean(upgradeMessage, moduleErrors, page));
        buildWarningMessageList();
    }

    public TemplateHeaderView(PageConfig page)
    {
        this(null, null, page);
    }

    public boolean isUserHidingWarningMessages()
    {
        HttpSession session = getViewContext().getRequest().getSession(false);
        return null != session && Boolean.FALSE.equals(session.getAttribute(SHOW_WARNING_MESSAGES_SESSION_PROP));
    }

    private void buildWarningMessageList()
    {
        User user = getViewContext().getUser();
        Container container = getViewContext().getContainer();
        TemplateHeaderBean bean = getModelBean();

        if (null != user && user.isSiteAdmin())
        {
            //admin-only mode--show to admins
            if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
            {
                _warningMessages.add("This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode via the <a href=\""
                        + PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL()
                        + "\">"
                        + "site settings page</a>.");
            }

            //module failures during startup--show to admins
            if (null != bean.moduleFailures && bean.moduleFailures.size() > 0)
            {
                _warningMessages.add("The following modules experienced errors during startup: "
                        + "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(container) + "\">"
                        + PageFlowUtil.filter(bean.moduleFailures.keySet())
                        + "</a>");
            }

            //upgrade message--show to admins
            if (null != bean.upgradeMessage && !bean.upgradeMessage.isEmpty())
            {
                _warningMessages.add(bean.upgradeMessage);
            }

            // We have deprecated support for Java 7... warn
            if (!SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_8))
                _warningMessages.add("LabKey Server no longer supports Java 7. Please upgrade your runtime to Java 8.");
/*
            String serverInfo = ModuleLoader.getServletContext().getServerInfo();

            if (serverInfo.startsWith("Apache Tomcat/"))
            {
                String[] versionParts = serverInfo.substring(14).split("\\.");
                int majorVersion = Integer.valueOf(versionParts[0]);

                if (majorVersion < 7)
                    _warningMessages.add("Tomcat 6.0.x (and earlier) is no longer supported. Please upgrade your Tomcat installation to 7.0.x.");
            }
*/
            //FIX: 9683
            //show admins warning about inadequate heap size (<= 256Mb)
            MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
            long maxMem = membean.getHeapMemoryUsage().getMax();

            if (maxMem > 0 && maxMem <= 268435456)
            {
                HelpTopic topic = new HelpTopic("configWebappMemory");
                _warningMessages.add("The maximum amount of heap memory allocated to LabKey Server is too low (256M or less). " +
                        "LabKey recommends " + topic.getSimpleLinkHtml("setting the maximum heap to at least one gigabyte (-Xmx1024M)")
                        + ".");
            }

            String dialectWarning = DbScope.getLabkeyScope().getSqlDialect().getAdminWarningMessage();

            // Warn if running on a deprecated database version or some other non-fatal database configuration issue
            if (null != dialectWarning)
                _warningMessages.add(dialectWarning);
        }

        if (AppProps.getInstance().isDevMode())
        {
            int leakCount = ConnectionWrapper.getProbableLeakCount();
            if (leakCount > 0)
            {
                int count = ConnectionWrapper.getActiveConnectionCount();
                String connectionsInUse = "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getMemTrackerURL() + "\">" + count + " DB connection" + (count == 1 ? "" : "s") + " in use.";
                connectionsInUse += " " + leakCount + " probable leak" + (leakCount == 1 ? "" : "s") + ".</a>";
                _warningMessages.add(connectionsInUse);
            }
        }

        if (AppProps.getInstance().isShowRibbonMessage() && !StringUtils.isEmpty(AppProps.getInstance().getRibbonMessageHtml()))
        {
            String message = AppProps.getInstance().getRibbonMessageHtml();
            message = ModuleHtmlView.replaceTokens(message, getViewContext());
            _warningMessages.add(message);
        }
    }

    public List<String> getWarningMessages()
    {
        return _warningMessages;
    }

    // for testing only
    public void addTestMessage()
    {
        _warningMessages.add("TESTING");
    }

    public static class TemplateHeaderBean
    {
        public @Nullable String upgradeMessage;
        public @Nullable Map<String, Throwable> moduleFailures;
        public PageConfig pageConfig;

        private TemplateHeaderBean(@Nullable String upgradeMessage, @Nullable Map<String, Throwable> moduleFailures, PageConfig page)
        {
            this.upgradeMessage = upgradeMessage;
            this.moduleFailures = moduleFailures;
            this.pageConfig = page;
        }
    }
}
