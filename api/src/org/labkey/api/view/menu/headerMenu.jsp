<%
/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.AdminReadPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.PopupAdminView" %>
<%@ page import="org.labkey.api.view.PopupDeveloperView" %>
<%@ page import="org.labkey.api.view.PopupHelpView" %>
<%@ page import="org.labkey.api.view.PopupUserView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.menu.HeaderMenu" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.core.notification.NotificationMenuView" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HeaderMenu me = ((HeaderMenu) HttpView.currentView());
    PageConfig pageConfig = me.getModelBean();
    ViewContext currentContext = getViewContext();
    User user = getUser();
    ActionURL currentURL = getActionURL();
    PageConfig.Template template = pageConfig.getTemplate();
    boolean templateCls = PageConfig.Template.Home != template && PageConfig.Template.None != template;
%>

<labkey:scriptDependency/>
<div class="<%=templateCls ? pageConfig.getTemplate().toString().toLowerCase() + "-" : ""%>headermenu">
    <%
        boolean needSeparator = false;

        if (currentContext.hasPermission(AdminPermission.class) || ContainerManager.getRoot().hasPermission("header.jsp popupadminview", user, AdminReadPermission.class))
        {
            include(new PopupAdminView(currentContext), out);
            needSeparator = true;
        }
        else if (getUser().isDeveloper())
        {
            include(new PopupDeveloperView(currentContext), out);
            needSeparator = true;
        }

        PopupHelpView helpMenu = new PopupHelpView(getContainer(), user, pageConfig.getHelpTopic());
        if (helpMenu.hasChildren())
        {
            if (needSeparator)
                out.write(" &nbsp; ");
            include(helpMenu, out);
            needSeparator = true;
        }

        if (null != user && !user.isGuest())
        {
            if (needSeparator)
                out.write(" &nbsp; ");
            include(new PopupUserView(currentContext), out);
        }
        else if (pageConfig.shouldIncludeLoginLink())
        {
            if (needSeparator)
                out.write(" &nbsp; ");

            String authLogoHtml = AuthenticationManager.getHeaderLogoHtml(currentURL);

            if (null != authLogoHtml)
                out.print(authLogoHtml + "&nbsp;");

    %>
    <a class="labkey-nomenu-text-link" href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">Sign&nbsp;In</a>
    <%
        }

        boolean showNotifications = AppProps.getInstance().isExperimentalFeatureEnabled(NotificationMenuView.EXPERIMENTAL_NOTIFICATIONMENU);
        if (showNotifications)
        {
            HttpView notificationMenu = NotificationMenuView.createView(currentContext);
            if (null != notificationMenu)
                include(notificationMenu,out);
        }
    %>
</div>