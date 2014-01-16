<%--
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.template.PrintTemplate" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %><%
    PrintTemplate me = (PrintTemplate) HttpView.currentView();
    PageConfig bean = me.getModelBean();
    String contextPath = request.getContextPath();
    ActionURL url = getActionURL();
    ActionURL base = url.clone();
    base.setAction((String)null);
    base.deleteParameters();
    Set<String> gwtModules = GWTView.getModulesForRootContext();
    Container c = getContainer();
    User user = getUser();
//    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    ThemeFont themeFont = ThemeFont.getThemeFont(c);
    boolean isPrint = bean.getTemplate() == PageConfig.Template.Print;

    if (bean.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", bean.getFrameOption().name());

    String onLoad = "";
    if (null != bean.getFocus())
    {
        onLoad += "document." + bean.getFocus() + ".focus(); ";
    }
    if (bean.getShowPrintDialog())
    {
        onLoad += "window.print(); ";
    }

    String userAgent = StringUtils.defaultString(request.getHeader("User-Agent"),"Mozilla");
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="X-UA-Compatible" content="IE=Edge" /><%
        if (bean.getFrameOption() == PageConfig.FrameOption.DENY)
        { %>
    <script type="text/javascript">if (top != self) top.location.replace(self.location.href);</script><%
        } %>
    <title><%=h(bean.getTitle()) %></title>
    <!-- <%=h(url.getURIString())%> -->
    <!-- <base href="<%=h(base.getURIString())%>" /> -->
<%= bean.getMetaTags(url) %>
<%= PageFlowUtil.getStandardIncludes(c, user, userAgent, bean.getClientDependencies()) %><%
if(user.isSiteAdmin())
{
    String webPartPermissionsource = contextPath + "/WebPartPermissionsPanel.js?" + PageFlowUtil.getServerSessionHash();
    %>
    <script type="text/javascript" src="<%=text(webPartPermissionsource)%>"></script><%
}
if (null != bean.getStyleSheet())
{
    %>
    <link href="<%=bean.getStyleSheet() %>" type="text/css" rel="stylesheet"/><%
}
if (null != bean.getRssUrl())
{
    %>
    <link href="<%=bean.getRssUrl().getEncodedLocalURIString()%>" type="application/rss+xml" title="<%=h(bean.getRssTitle())%>" rel="alternate"/><%
}
if (null != bean.getStyles())
{
    %>
    <style type="text/css"><!--<%= bean.getStyles() %>--></style><%
}
%>
<%= null != bean.getScript() && 0 < bean.getScript().length() ? bean.getScript() + "\n": "" %>
<% if (bean.getAllowTrackingScript())
{
    String script = AnalyticsService.getTrackingScript();
    if (StringUtils.isNotEmpty(script))
    {
        if (user.isSiteAdmin())
            {%><!-- see <%=new ActionURL("analytics","begin",ContainerManager.getRoot()).getURIString()%> --><%}
        %><%=script%><%
    }
}
%>
</head>

<body id="bodyElement" onload="<%=h(onLoad)%>" class="<%=themeFont.getClassName()%><%=isPrint?" print":""%>">
<%
if (null != gwtModules && gwtModules.size() > 0)
{   //Only include first js file?? %>
    <iframe id="__gwt_historyFrame" style="width:0;height:0;border:0"></iframe><%
}
%>
    <table class="labkey-main <%=themeFont.getClassName()%><%=isPrint?" print":""%>" cellpadding="0" cellspacing="0" <% if (isPrint) { %>style="padding: 5px;"<% } %>><%

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
            <td class="labkey-proj">

                <% if (null != me.getView("appbar"))
                {%>
                        <%me.include(me.getView("appbar"), out); %>
              <%}%>
                <!--content area-->
                <table class="labkey-proj">
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
            <td id="bodypanel" class="labkey-body-panel" style="min-width:<%=bean.getMinimumWidth()%>px;">
                <img height=1 width=<%=bean.getMinimumWidth()%> src="<%=getWebappURL("/_.gif")%>"><br>
                <!-- BODY -->
                <% me.include(me.getBody(),out); %>
                <!-- /BODY -->
            </td><%

            if (me.getView("right") instanceof HttpView && ((HttpView)me.getView("right")).isVisible())
				{ %>
            <!-- RIGHT -->
            <td class="labkey-side-panel" style="min-width:240px;">
                <img height=1 width=240 src="<%=getWebappURL("_.gif")%>"><br>
                <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT),out); %>
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
    String anchor = bean.getAnchor();
    if (null == StringUtils.trimToNull(anchor))
        anchor = StringUtils.trimToNull(request.getParameter("_anchor"));

    if (null != anchor)
    {
        %><script type="text/javascript" for="window" event="onload">window.location.href = "#<%=h(anchor)%>"</script><%
    }
%>
    <script type="text/javascript">LABKEY.loadScripts(); LABKEY.showNavTrail();</script>
    <script type="text/javascript">
    Ext4.onReady(function(){Ext4.DomHelper.insertHtml("beforeend",document.body,"<input id=seleniumExtReady name=seleniumExtReady type=hidden>");});
    // $(document.body).ready(function() { $('body').append('<input id="seleniumExtReady" name="seleniumExtReady" type="hidden">'); });
    </script>
    <!--<%= h(request.getHeader("User-Agent")) %>--><%
    if (null != request.getAttribute(ViewServlet.REQUEST_STARTTIME))
    { %>
    <!--<%= text("time " + (System.currentTimeMillis() - (Long)request.getAttribute(ViewServlet.REQUEST_STARTTIME)) + "ms") %> --><%
    }

    ActionURL permaLink = getViewContext().cloneActionURL();
    permaLink.setExtraPath("__r" + Integer.toString(c.getRowId()));
%>
<a href="<%=permaLink%>" id="permalink" style="display: none;"></a>
</body>
</html>
