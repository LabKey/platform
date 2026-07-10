<%--
/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.OptionalFeatureService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.core.view.template.bootstrap.PageTemplate" %>
<%@ page import="static org.labkey.core.view.template.bootstrap.PageTemplate.EXPERIMENTAL_SHORT_CIRCUIT_ROBOTS" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    PageTemplate me = HttpView.currentView();
    PageConfig model = me.getModelBean();
    ActionURL url = getActionURL();
    ViewContext context = getViewContext();

    boolean isExplicitNoIndex = null != url && "1".equals(url.getParameter(ActionURL.Param._noindex.name()));
    if (isExplicitNoIndex)
        model.setRobotsNone();

    // set robots header in addition to <meta> (it is easier to test)
    if (model.getTemplate() == PageConfig.Template.Print)
        model.setNoIndex();
    String robotsTag = model.getMetaTag("robots");
    if (StringUtils.isNotBlank(robotsTag))
        response.setHeader("X-Robots-Tag", robotsTag);
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
    <%= PageFlowUtil.getAppIncludes(getViewContext(), model) %>
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
                    %><!-- see <%= unsafe(new ActionURL("analytics", "begin", ContainerManager.getRoot()).getURIString())%> --><%
               }
               %><%=unsafe(script)%><%
           }
       }
%>
</head>
<body class="<%=h(PageTemplate.getTemplatePrefix(model) + "-template-body")%>">
<%
    if (context.isRobot() && StringUtils.contains(robotsTag, "noindex") && OptionalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_SHORT_CIRCUIT_ROBOTS))
    {
        %></body></html><%
        return;
    }
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
%><a href="<%=h(me.getPermaLink())%>" id="permalink" name="permalink" style="display: none;"><span class="sr-only">Page link</span></a><%
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
