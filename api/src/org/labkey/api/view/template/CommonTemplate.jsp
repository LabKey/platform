<%--
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
--%>
<%@ page buffer="none" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.analytics.AnalyticsService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.GWTView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.template.PrintTemplate" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %><%
    PrintTemplate me = (PrintTemplate) HttpView.currentView();
    PageConfig bean = me.getModelBean();
    String contextPath = request.getContextPath();
    ActionURL url = HttpView.getRootContext().getActionURL();
    ActionURL base = url.clone();
    base.setAction((String)null);
    base.deleteParameters();
    Set<String> gwtModules = GWTView.getModulesForRootContext();
    Container c = me.getViewContext().getContainer();
%>
<html>
<head>
    <title><%= h(bean.getTitle()) %></title>
    <!-- <%=url.getURIString()%> -->
    <!-- <base href="<%=h(base.getURIString())%>" /> -->
<%= PageFlowUtil.getStandardIncludes(c) %>
    <%
if (null != bean.getStyleSheet())
    {
    %>
    <link href="<%=bean.getStyleSheet() %>" type="text/css" rel="stylesheet"/><%
    }
if (null != bean.getRssUrl())
    {
    %>
    <link href="<%=bean.getRssUrl().getEncodedLocalURIString()%>" type="application/rss+xml" title="<%=bean.getRssTitle()%>" rel="alternate"/><%
    }
if (null != bean.getStyles())
    {
    %>
    <style type="text/css"><!--<%= bean.getStyles() %>--></style><%
    }
%>
    <script type="text/javascript" src="<%=contextPath%>/labkey.js?<%=AppProps.getInstance().getServerSessionGUID()%>"></script>
    <script type="text/javascript" language="javascript">
        LABKEY.init(<%=PageFlowUtil.jsInitObject()%>);
        LABKEY.requiresScript('util.js');
    </script>
<%= null != bean.getScript() && 0 < bean.getScript().length() ? bean.getScript() + "\n": ""
%>
</head>

<body id="bodyElement" <%
    if (null != bean.getFocus() || bean.getShowPrintDialog())
    {%>onload="<%
        if (null != bean.getFocus()) {%>document.<%=bean.getFocus()%>.focus();<%}
        if (bean.getShowPrintDialog()) {%>window.print();<%}
    %>"<%}%>><%

if (null != gwtModules && gwtModules.size() > 0)
{   //Only include first js file?? %>
    <iframe id="__gwt_historyFrame" style="width:0;height:0;border:0"></iframe><%
}
%>
    <table class="labkey-main"><%

if (bean.showHeader() != PageConfig.TrueFalse.False)
{
%>
        <tr id="headerpanel" class="labkey-header-panel">
            <td colspan=3>
                <!-- HEADER -->
                <% me.include(me.getView("header"),out); %>
                <!-- /HEADER -->
            </td>
        </tr><%
    if (null != me.getView("topmenu"))
    {%>
        <tr id="topmenu" class="labkey-header-panel">
            <td colspan=3>
                <!-- HEADER -->
                <% me.include(me.getView("topmenu"),out); %>
                <!-- /HEADER -->
            </td>
        </tr><%
     }%>
        <tr>
            <td id="leftmenupanel" class="labkey-site-nav-panel">
<%            if (HttpView.currentContext().isShowFolders())
                {%>
    		    <img src="<%= request.getContextPath() %>/_.gif" class="labkey-site-nav-expander" height=1><br>
            <%  } %>
                <% me.include(me.getView("menu") ,out); %>
            </td>
            <td class="labkey-proj">
                <!--content area-->
                <table class="labkey-proj">
                    <% if (null != me.getView("appbar"))
                    {%>
                    <tr>
                        <td id="appbar" class="labkey-proj-nav-panel" colspan="2">
                            <%me.include(me.getView("appbar"), out); %>
                        </td>
                    </tr>
                  <%}%>
<%    if (me.getView("nav") instanceof HttpView && ((HttpView)me.getView("nav")).isVisible())
          { %>
        <tr>
            <td id="navpanel" class="labkey-proj-nav-panel" colspan="2">
<%
            me.include(me.getView("nav"),out);
%>
            </td>
        </tr>
<%        } %>
<% } %>
        <tr>
<%

if (null != me.getView("moduleNav"))
{
    %><td align=left valign=top class=normal width="200px" height="100%" style="padding:5;"><%
        me.include(me.getView("moduleNav"), out);
    %></td><%
}

    %>
            <td id="bodypanel" class="labkey-body-panel">
                <img height=1 width=<%=bean.getMinimumWidth()%> src="<%= contextPath %>/_.gif"><br>
                <!-- BODY -->
                <% me.include(me.getBody(),out); %>
                <!-- /BODY -->
            </td><%

            if (me.getView("right") instanceof HttpView && ((HttpView)me.getView("right")).isVisible())
				{ %>
            <!-- RIGHT -->
            <td class="labkey-side-panel">
                <img height=1 width=240 src="<%= contextPath %>/_.gif"><br>
                <% me.include(me.getView("right"),out); %>
            </td>
            <!-- /RIGHT -->
<%				} %>
        </tr><%
    if (bean.showHeader() != PageConfig.TrueFalse.False)
    {  %>
                </table>
                <!--/content area-->
            </td>
        </tr><%
    }
%>
    </table><%
    // Need hack below so we don't include anchor javascript when running DRT; the framework responds by redirecting with no parameters
    String anchor = bean.getAnchor();
    if (null == StringUtils.trimToNull(anchor))
        anchor = StringUtils.trimToNull(request.getParameter("_anchor"));

    if (null != anchor && !"httpunit/1.5".equals(request.getHeader("user-agent")))
    {%>
    <script type="text/javascript" for="window" event="onload">window.location.href = "#<%=h(anchor)%>"</script><%
    }
%>
    <script type="text/javascript">LABKEY.loadScripts(); LABKEY.showNavTrail();</script>
    <%=AnalyticsService.getTrackingScript()%>
    <!--<%= request.getHeader("User-Agent") %>--><%

    if (null != request.getAttribute(ViewServlet.REQUEST_STARTTIME))
    { %>
    <!--<%= "time " + (System.currentTimeMillis() - (Long)request.getAttribute(ViewServlet.REQUEST_STARTTIME)) + "ms" %> --><%
    }
%>
</body>
</html>
