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
