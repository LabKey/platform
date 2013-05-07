<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminReadPermission" %>
<%@ page import="org.labkey.api.view.PopupAdminView" %>
<%@ page import="org.labkey.api.view.PopupDeveloperView" %>
<%@ page import="org.labkey.api.view.PopupHelpView" %>
<%@ page import="org.labkey.api.view.PopupUserView" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.menu.HeaderMenu" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HeaderMenu me = ((HeaderMenu) HttpView.currentView());
    PageConfig pageConfig = me.getModelBean();
    ViewContext currentContext = HttpView.currentContext();
    User user = currentContext.getUser();
    ActionURL currentURL = currentContext.getActionURL();
    PageConfig.Template template = pageConfig.getTemplate();
    boolean templateCls = PageConfig.Template.Home != template && PageConfig.Template.None != template;
%>
<div class="<%=templateCls ? pageConfig.getTemplate().toString().toLowerCase() + "-" : ""%>headermenu">
    <%
        boolean needSeparator = false;

        if (currentContext.hasPermission(AdminPermission.class) || ContainerManager.getRoot().hasPermission("header.jsp popupadminview", user, AdminReadPermission.class))
        {
            include(new PopupAdminView(currentContext), out);
            needSeparator = true;
        }
        else if (currentContext.getUser().isDeveloper())
        {
            include(new PopupDeveloperView(currentContext), out);
            needSeparator = true;
        }

        PopupHelpView helpMenu = new PopupHelpView(getViewContext().getContainer(), user, pageConfig.getHelpTopic());
        if (helpMenu.hasChildren())
        {
            if (needSeparator)
                out.write(" | ");
            include(helpMenu, out);
            needSeparator = true;
        }

        if (null != user && !user.isGuest())
        {
            if (needSeparator)
                out.write(" | ");
            include(new PopupUserView(currentContext), out);
        }
        else if (pageConfig.shouldIncludeLoginLink())
        {
            if (needSeparator)
                out.write(" | ");

            String authLogoHtml = AuthenticationManager.getHeaderLogoHtml(currentURL);

            if (null != authLogoHtml)
                out.print(authLogoHtml + "&nbsp;");

    %>
    <a class="labkey-menu-text-link" href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">Sign&nbsp;In</a>
    <%
        }
    %>
</div>