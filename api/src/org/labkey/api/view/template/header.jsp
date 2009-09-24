<%
/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.TemplateHeaderView" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TemplateHeaderView me = ((TemplateHeaderView) HttpView.currentView());
    TemplateHeaderView.TemplateHeaderBean bean = me.getModelBean();
    ViewContext currentContext = org.labkey.api.view.HttpView.currentContext();
    Container c = currentContext.getContainer();
    String contextPath = currentContext.getContextPath();
    ActionURL currentURL = currentContext.getActionURL();
    AppProps app = AppProps.getInstance();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(currentContext.getContainer());
%>
<table id="header"><tr>
<td class="labkey-main-icon"><a href="<%=h(laf.getLogoHref())%>"><img src="<%=h(TemplateResourceHandler.LOGO.getURL(c))%>" alt="<%=h(laf.getShortName())%>"><br><img alt="<%=h(laf.getLogoHref())%>" src="<%=contextPath%>/_.gif" width="146" height="1" border="0"></a></td>
        <td class="labkey-main-title-area"><span><a id="labkey-main-title" class="labkey-main-title" href="<%= app.getHomePageUrl() %>"><%=h(laf.getShortName())%></a></span>
            </td>

<td class="labkey-main-nav" align="right">
<%

    User user = (User) request.getUserPrincipal();

    if (null != user && !user.isGuest())
    {
%>
        <span id="header.user.friendlyName"><%=user.getFriendlyName()%></span><br/><a href="<%=h(urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId()))%>">My&nbsp;Account</a>&nbsp;|&nbsp;<a href="<%=h(user.isImpersonated() ? urlProvider(LoginUrls.class).getStopImpersonatingURL(c, request) : urlProvider(LoginUrls.class).getLogoutURL(c))%>"><%=user.isImpersonated() ? "Stop&nbsp;Impersonating" : "Sign&nbsp;Out"%></a>
<%
    }
    else if (bean.pageConfig.shouldIncludeLoginLink())
    {
        String authLogoHtml = AuthenticationManager.getHeaderLogoHtml(currentURL);

        if (null != authLogoHtml)
            out.print(authLogoHtml + "&nbsp;");

        %>
        <a href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">Sign&nbsp;In</a><%
    }

%>
</td>
<td id="labkey-warning-message-icon" class="labkey-main-nav" <%=me.isUserHidingWarningMessages() ? "" : "style=display:none;"%>>
    <img src="<%=getViewContext().getContextPath()%>/_images/warning-small.png" alt="!"
         title="Click to view warning messages."
         style="cursor: pointer;"
         onclick="labkeyShowWarningMessages(true);"/>
</td>
</tr>
<%
    if(me.getWarningMessages().size() > 0) {
%>
    <tr id="labkey-warning-messages-area" <%=me.isUserHidingWarningMessages() ? "style=display:none;" : ""%>>
        <td colspan="4" style="padding: 2px;">
            <div class="labkey-warning-messages">
                <img src="<%=getViewContext().getContextPath()%>/_images/partdelete.gif" alt="x"
                     style="float: right;cursor:pointer;" onclick="labkeyShowWarningMessages(false);">
                <ul>
                <% for(String warningMessage : me.getWarningMessages()) { %>
                    <li><%=warningMessage%></li>
                <% } //for each warning message %>
                </ul>
            </div>
        </td>
    </tr>
<%  } //if warning messages %>
</table>
<script type="text/javascript">
    LABKEY.requiresExtJs(false);
</script>
<script type="text/javascript">
    function labkeyShowWarningMessages(show)
    {
        if(undefined === show)
            show = true;

        var elem = Ext.get("labkey-warning-messages-area");
        if(elem)
            elem.setDisplayed(show, true);
        elem = Ext.get("labkey-warning-message-icon");
        if(elem)
            elem.setDisplayed(!show, true);

        Ext.Ajax.request({
            url: '<%=getViewContext().getContextPath()%>/user/setShowWarningMessages.api',
            method: 'GET',
            params: {showMessages: show}
        });
    }
</script>