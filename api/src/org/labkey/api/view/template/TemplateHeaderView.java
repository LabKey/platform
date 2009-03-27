/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.view.JspView;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.VersionNumber;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.settings.AppProps;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * User: adam
 * Date: Nov 29, 2007
 * Time: 9:51:54 AM
 */
public class TemplateHeaderView extends JspView<TemplateHeaderView.TemplateHeaderBean>
{
    public static String SHOW_WARNING_MESSAGES_SESSION_PROP = "hideWarningMessages";

    private List<String> _warningMessages = new ArrayList<String>();

    public TemplateHeaderView(List<String> containerLinks, String upgradeMessage, Map<String, Throwable> moduleErrors, PageConfig page)
    {
        super("/org/labkey/api/view/template/header.jsp", new TemplateHeaderBean(containerLinks, upgradeMessage, moduleErrors, page));
        buildWarningMessageList();
    }

    public TemplateHeaderView(PageConfig page)
    {
        this(null, null, null, page);
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

        //admin-only mode--show to admins
        if(null != user && user.isAdministrator() && AppProps.getInstance().isUserRequestedAdminOnlyMode())
        {
            _warningMessages.add("This site is configured so that only administrators may sign in. To allow other users to sign in, turn off admin-only mode in the <a href=\""
                    + PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL()
                    + "\">"
                    + "site-settings</a>.");
        }

        //module failures during startup--show to admins
        if(null != user && user.isAdministrator() && null != bean.moduleFailures && bean.moduleFailures.size() > 0)
        {
            _warningMessages.add("The following modules experienced errors during startup: "
                    + "<a href=\"" + PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(container) + "\">"
                    + PageFlowUtil.filter(bean.moduleFailures.keySet())
                    + "</a>");
        }

        //upgrade message--show to admins
        if(null != user && user.isAdministrator() && null != bean.upgradeMessage && bean.upgradeMessage.length() > 0)
        {
            _warningMessages.add(bean.upgradeMessage);
        }

        //FIX: 7502
        //show admins warning for postgres versions < 8.3 that we no longer support this
        DbScope coreScope = CoreSchema.getInstance().getSchema().getScope();
        if(null != user && user.isAdministrator() && "PostgreSQL".equalsIgnoreCase(coreScope.getDatabaseProductName()))
        {
            VersionNumber dbVersion = new VersionNumber(coreScope.getDatabaseProductVersion());
            if(dbVersion.getMajor() <= 8 && dbVersion.getMinor() < 3)
            {
                HelpTopic topic = new HelpTopic("postgresUpgrade", HelpTopic.Area.SERVER);
                _warningMessages.add("Support for PostgreSQL Version 8.2 and earlier has been deprecated. Please <a href=\""
                        + topic.getHelpTopicLink() + "\">upgrade to version 8.3 or later</a>.");
            }
        }
    }

    public List<String> getWarningMessages()
    {
        return _warningMessages;
    }


    public static class TemplateHeaderBean
    {
        public List<String> containerLinks;
        public String upgradeMessage;
        public Map<String, Throwable> moduleFailures;
        public PageConfig pageConfig;

        private TemplateHeaderBean(List<String> containerLinks, String upgradeMessage, Map<String, Throwable> moduleFailures, PageConfig page)
        {
            this.containerLinks = containerLinks;
            this.upgradeMessage = upgradeMessage;
            this.moduleFailures = moduleFailures;
            this.pageConfig = page;
        }
    }
}
