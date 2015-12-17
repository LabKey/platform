<%--
/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.GWTView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.template.PrintTemplate" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page session="true" %>
<%
    PrintTemplate me = (PrintTemplate) HttpView.currentView();
    PageConfig bean = me.getModelBean();
    ActionURL url = getActionURL();
    Set<String> gwtModules = GWTView.getModulesForRootContext();
    Container c = getContainer();
    User user = getUser();
    ThemeFont themeFont = ThemeFont.getThemeFont(c);

    if (bean.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", bean.getFrameOption().name());

    String onLoad = "";
    if (StringUtils.isNotEmpty(bean.getFocus()))
    {
        onLoad += "(document." + bean.getFocus() + "?document." + bean.getFocus() + ".focus():null);";
    }
    if (bean.getShowPrintDialog())
    {
        onLoad += "window.print(); ";
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="X-UA-Compatible" content="IE=Edge" />

    <%
        if (bean.getFrameOption() == PageConfig.FrameOption.DENY)
        {
    %>
    <script type="text/javascript">if (top != self) top.location.replace(self.location.href);</script>
    <%
        }
    %>
    <title><%=h(bean.getTitle()) %></title>
    <!-- <%=h(url.getURIString())%> -->
<%= text(bean.getMetaTags(url)) %>
<%= PageFlowUtil.getStandardIncludes(getViewContext(), bean.getClientDependencies()) %>
    <%
        if (null != bean.getRssUrl())
        {
    %>
    <link href="<%=bean.getRssUrl().getEncodedLocalURIString()%>" type="application/rss+xml" title="<%=h(bean.getRssTitle())%>" rel="alternate"/>
    <%
        }
    %>
    <% if (bean.getAllowTrackingScript())
    {
        String script = AnalyticsService.getTrackingScript();
        if (StringUtils.isNotEmpty(script))
        {
            if (user.isSiteAdmin())
            {
    %>      <!-- see <%=new ActionURL("analytics","begin",ContainerManager.getRoot()).getURIString()%> -->
    <%
            }
    %>
    <%=script%>
    <%
        }
    }
    %>

<!-- TODO -->
    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css" integrity="sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7" crossorigin="anonymous">
    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css" integrity="sha384-fLW2N01lMqjakBkx3l/M9EahuwpSfeNvV63J5ezn3uZzapT0u7EYsXMjQV+0En5r" crossorigin="anonymous">
    <script src="//maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js" integrity="sha384-0mSbJDEHialfmuBBQP6A4Qrprq5OVfW37PRR3j5ELqxss1yVqOtnepnHVP9aJ7xS" crossorigin="anonymous"></script>
<!-- TODO -->

</head>

<body id="bodyElement" onload="<%=h(onLoad)%>">
<%
    if (null != gwtModules && gwtModules.size() > 0)
    {   //Only include first js file??
%>
<iframe id="__gwt_historyFrame" style="width:0;height:0;border:0"></iframe>
<%
    }
%>
<div>
    <%
        if (bean.showHeader() != PageConfig.TrueFalse.False)
        {
            out.println("<div>");
            me.include(me.getView("header"),out);

            if (null != me.getView("topmenu"))
                me.include(me.getView("topmenu"),out);

            if (null != me.getView("appbar"))
                me.include(me.getView("appbar"), out);
            out.println("</div>");
        }
    %>
     <!--content area-->
    <div>
        <table class="labkey-proj">
            <tr>
                <%
                    if (null != me.getView("moduleNav"))
                    {
                %>
                <td align=left valign=top width="200px" height="100%" style="padding:5;">
                    <%
                        me.include(me.getView("moduleNav"), out);
                    %>
                </td>
                <%
                    }
                %>
                <td id="bodypanel" style="min-width:<%=bean.getMinimumWidth()%>px;">
                    <img height=1 width=<%=bean.getMinimumWidth()%> src="<%=getWebappURL("/_.gif")%>">
                    <br />
                    <!-- BODY -->
                    <% me.include(me.getBody(),out); %>
                    <!-- /BODY -->
                </td>
                <%
                    if (me.getView("right") instanceof HttpView && ((HttpView)me.getView("right")).isVisible())
                    {
                %>
                <!-- RIGHT -->
                <td style="min-width:240px;">
                    <img height=1 width=240 src="<%=getWebappURL("_.gif")%>"><br>
                    <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT),out); %>
                </td>
                <!-- /RIGHT -->
                <%
                    }
                %>
            </tr>
        </table>
    </div>
    <!--/content area-->
        <%
            if (null != me.getView("footer"))
            {
                out.println("<div>");
                me.include(me.getView("footer"),out);
                out.println("</div>");
            }
        %>
</div>
<%
    String anchor = bean.getAnchor();
    if (null == StringUtils.trimToNull(anchor))
        anchor = StringUtils.trimToNull(request.getParameter("_anchor"));

    if (null != anchor)
    {
%>
<script type="text/javascript" for="window" event="onload">window.location.href = "#<%=h(anchor)%>"</script>
<%
    }
%>

<script type="text/javascript">LABKEY.loadScripts(); LABKEY.showNavTrail();</script>
<!--
<%= h(request.getHeader("User-Agent")) %>-->
<%
    ActionURL permaLink = getViewContext().cloneActionURL();
    permaLink.setExtraPath("__r" + Integer.toString(c.getRowId()));
%>
<a href="<%=permaLink%>" id="permalink" style="display: none;"></a>
</body>
</html>
