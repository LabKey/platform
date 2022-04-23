<%--
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
--%>
<%@ page buffer="none" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.analytics.AnalyticsService" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.core.view.template.bootstrap.PageTemplate" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    PageTemplate me = (PageTemplate) HttpView.currentView();
    PageConfig model = me.getModelBean();
    ActionURL url = getActionURL();

    if (model.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", model.getFrameOption().name());
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta http-equiv="X-UA-Compatible" content="IE=edge" />
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%= model.getMetaTags(url) %>
    <%= model.getPreloadTags() %>
    <title><%= h(model.getTitle()) %></title>
    <% if (me.isAppTemplate()) { %>
    <%= PageFlowUtil.getAppIncludes(getViewContext(), model.getClientDependencies()) %>
    <% } else { %>
    <%= PageFlowUtil.getStandardIncludes(getViewContext(), model) %>
    <% } %>
    <% if (null != model.getRssUrl()) { %>
    <link href="<%=h(model.getRssUrl())%>" type="application/rss+xml" title="<%=h(model.getRssTitle())%>" rel="alternate"/>
    <% } %>
    <% if (model.getAllowTrackingScript())
       {
           String script = AnalyticsService.getTrackingScript();
           if (StringUtils.isNotEmpty(script))
           {
               if (null != getContainer() && getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
               {
                    %><!-- see <%= text(new ActionURL("analytics", "begin", ContainerManager.getRoot()).getURIString())%> --><%
               }
               %><%=text(script)%><%
           }
       }
%>
</head>
<body class="<%=h(PageTemplate.getTemplatePrefix(model) + "-template-body")%>">
<%
    if (model.showHeader() != PageConfig.TrueFalse.False && null != me.getView("header"))
    {
%>
<div class="lk-header-ct">
<%
        me.include(me.getView("header"), out);

        if (null != me.getView("navigation"))
            me.include(me.getView("navigation"),out);
%>
</div>
<%
    }
%>
<div class="lk-body-ct">
    <% me.include(me.getView("bodyTemplate"), out); %>
</div>
<% if (null != me.getView("footer")) { %>
<footer class="footer-block">
    <div class="footer-content">
    <% me.include(me.getView("footer"), out); %>
    </div>
</footer>
<% }
if (null != me.getViewContext().getContainer()) {
%><a href="<%=h(me.getPermaLink())%>" id="permalink" name="permalink" style="display: none;"></a><%
}
%><!-- <%= h(request.getHeader("User-Agent")) %> -->
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    LABKEY.loadScripts();
    LABKEY.showNavTrail();<%
    if (!me.isAppTemplate()) { %>
    if (LABKEY.WebSocket) LABKEY.WebSocket.initWebSocket();
<%  }
    model.endOfBodyScript(out); %>
</script>
</body>
</html>