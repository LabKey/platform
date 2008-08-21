<%
/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.admin.AdminUrls"%><%@ page import="org.labkey.api.security.AuthenticationManager" %><%@ page import="org.labkey.api.security.User" %><%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.TemplateHeaderView" %>
<%@ page import="org.labkey.api.settings.LookAndFeelAppProps" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TemplateHeaderView.TemplateHeaderBean bean = ((TemplateHeaderView) HttpView.currentView()).getModelBean();
    ViewContext currentContext = org.labkey.api.view.HttpView.currentContext();
    Container c = currentContext.getContainer();
    String contextPath = currentContext.getContextPath();
    ActionURL currentURL = currentContext.getActionURL();
    AppProps app = AppProps.getInstance();
    LookAndFeelAppProps laf = LookAndFeelAppProps.getInstance(currentContext.getContainer());
%>
<table id="header"><tr>
<td class="labkey-main-icon"><a href="<%=h(laf.getLogoHref())%>"><img src="<%=h(TemplateResourceHandler.LOGO.getURL(c))%>" alt="<%=h(laf.getSystemShortName())%>"><br><img alt="<%=h(laf.getLogoHref())%>" src="<%=contextPath%>/_.gif" width="146" height="1" border="0"></a></td>
<td class="labkey-main-title-area"><span><a id="labkey-main-title" class="labkey-main-title" href="<%= app.getHomePageUrl() %>"><%=h(laf.getSystemShortName())%></a></span><br><span class="normal"><a href="<%= app.getHomePageUrl() %>"><%= h(laf.getSystemDescription())%></a><%
if (bean.containerLinks != null)
    {
    for (String containerLink : bean.containerLinks)
        {
        %> &gt;&nbsp;<%= containerLink %><%
        }
    }
%></span></td>
<td class="labkey-main-nav" align="right"><%

    User user = (User) request.getUserPrincipal();

    if (null != user && !user.isGuest())
    {
        out.print(user.getFriendlyName() + "<br>");%>
        <a href="<%=h(urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId()))%>">My&nbsp;Account&nbsp;&nbsp;</a><a href="<%=h(urlProvider(LoginUrls.class).getLogoutURL())%>"><%=user.isImpersonated() ? "Stop&nbsp;Impersonating" : "Sign&nbsp;out"%></a><%
    }
    else if (bean.pageConfig.shouldIncludeLoginLink())
    {
        String authLogoHtml = AuthenticationManager.getHeaderLogoHtml(currentURL);

        if (null != authLogoHtml)
            out.print(authLogoHtml + "&nbsp;");

        %>
        <a href="<%=h(urlProvider(LoginUrls.class).getLoginURL(currentURL))%>">Sign&nbsp;in</a><%
    }

%></td></tr>
<%
    if (user != null && user.isAdministrator())
    {
        if (AppProps.getInstance().isUserRequestedAdminOnlyMode())
        {
%><tr><td>&nbsp;</td><td class="labkey-error" style="padding:5px;">Admin only mode: only administrators can log in. When finished, turn this off in the <a href="<%=urlProvider(AdminUrls.class).getCustomizeSiteURL()%>">Admin Console</a>.</td></tr><%
    }

    if (bean.moduleFailures != null && bean.moduleFailures.size() > 0)
    {
    %><tr><td>&nbsp;</td><td style="padding:5px;">
    <p class="labkey-error">There were errors loading the following modules during server startup:</p>
    <%= bean.moduleFailures.keySet() %>
    <p><a href="<%=PageFlowUtil.urlProvider(AdminUrls.class).getModuleErrorsURL(currentContext.getContainer()) %>">Error details</a></p>
</td></tr><%
    }

    // Do not HTML encode this message, as it is HTML so it can contain a link to
    // download a new version, for example.
    if (bean.upgradeMessage != null)
    {
    %><tr><td>&nbsp;</td><td style="padding:5px;"><%= bean.upgradeMessage %></td></tr><%
    }
}
%>
    <tr style="display:none" id="firebug-warning">
        <td>&nbsp;</td>
        <td class="labkey-error" style="padding:5px">
        Firebug is known to cause this site to run slowly. Please disable Firebug.
        [<a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=firebug">instructions</a>]
        </td>
    </tr>
</table>
<script type="text/javascript">
    if(window.console && window.console.firebug)
    {
        var elem = document.getElementById("firebug-warning");
        if(elem)
            elem.style.display = "";
    }
</script>
