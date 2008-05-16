<%@ page import="org.labkey.api.security.AuthenticationManager"%><%@ page import="org.labkey.api.security.User" %><%@ page import="org.labkey.api.security.UserManager" %><%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.TemplateHeaderView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TemplateHeaderView.TemplateHeaderBean bean = ((TemplateHeaderView) HttpView.currentView()).getModelBean();
    ViewContext currentContext = org.labkey.api.view.HttpView.currentContext();
    String contextPath = currentContext.getContextPath();
    ActionURL currentUrl = currentContext.getActionURL();
    AppProps app = AppProps.getInstance();
%>
<table width="100%" cellspacing=0 cellpadding=0><tr>
<td align="center" valign="middle" height=56><a href="<%=h(app.getLogoHref())%>"><img src="<%=contextPath%>/logo.image?revision=<%=app.getLookAndFeelRevision()%>" alt="<%=h(app.getSystemShortName())%>"><br><img alt="<%=h(app.getLogoHref())%>" src="<%=contextPath%>/_.gif" width="146" height="1" border="0"></a></td>
<td align="left" valign="bottom" style="padding:5px; width:100%"><span class="ms-pagetitle"><a href="<%= app.getHomePageUrl() %>" style="color:#666666;"><%=h(app.getSystemShortName())%></a></span><br><span class="normal"><a href="<%= app.getHomePageUrl() %>"><%= h(app.getSystemDescription())%></a><%
if (bean.containerLinks != null)
    {
    for (String containerLink : bean.containerLinks)
        {
        %> &gt;&nbsp;<%= containerLink %><%
        }
    }
%></span></td>
<td class="ms-nav" style="padding:5px;" align="right" valign="bottom" nowrap><%

    User user = (User) request.getUserPrincipal();

    if (null != user && !user.isGuest())
    {
        out.print(user.getFriendlyName() + "<br>");%>
        <a href="<%=h(UserManager.getUserDetailsUrl(user.getUserId()))%>">My&nbsp;Account&nbsp;&nbsp;</a><a href="<%=h(AuthenticationManager.getLogoutURL())%>">Sign&nbsp;out</a><%
    }
    else if (bean.pageConfig.shouldIncludeLoginLink())
    {
        String authLogoHtml = AuthenticationManager.getHeaderLogoHtml(currentUrl);

        if (null != authLogoHtml)
            out.print(authLogoHtml + "&nbsp;");

        %>
        <a href="<%=AuthenticationManager.getLoginURL(currentUrl)%>">Sign&nbsp;in</a><%
    }

%></td></tr>
<%
    if (user != null && user.isAdministrator())
    {
        if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
        {
%><tr><td>&nbsp;</td><td class="ms-nav; labkey-error" style="padding:5px;">Admin only mode: only administrators can log in. When finished, turn this off in the <a href="<%= contextPath %>/admin/showCustomizeSite.view">Admin Console</a>.</td></tr><%
    }

    if (bean.moduleFailures != null && bean.moduleFailures.size() > 0)
    {
    %><tr><td>&nbsp;</td><td class="ms-nav"style="padding:5px;">
    <p class="labkey-error">There were errors loading the following modules during server startup:</p>
    <%= bean.moduleFailures.keySet() %>
    <p><a href="<%=PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(currentContext.getContainer()) %>">Error details</a></p>
</td></tr><%
    }

    // Do not HTML encode this message, as it is HTML so it can contain a link to
    // download a new version, for example.
    if (bean.upgradeMessage != null)
    {
    %><tr><td>&nbsp;</td><td class="ms-nav"style="padding:5px;"><%= bean.upgradeMessage %></td></tr><%
    }
}
%>
</table>