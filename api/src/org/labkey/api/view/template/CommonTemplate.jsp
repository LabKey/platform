<%@ page buffer="none" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.analytics.AnalyticsService" %>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
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
%>
<html>
<head>
    <title><%= h(bean.getTitle()) %></title>
    <!-- <%=url.getURIString()%> -->
    <!-- <base href="<%=h(base.getURIString())%>" /> -->
<%= PageFlowUtil.getStandardIncludes() %>
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

<body id="bodyElement" style="margin-top:0; margin-left:0; margin-right:0;" <%
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
    <table class=ms-main border=0 style="width:100%; height:100%;" cellspacing=0 cellpadding=0><%

if (bean.showHeader())
{
    String headerLineColor = "#" + WebTheme.getTheme().getHeaderLineColor();
%>
        <tr style="height:56px;">
            <td colspan=3>
                <!-- HEADER -->
                <% me.include(me.getView("header"),out); %>
                <!-- /HEADER -->
            </td>
        </tr>
        <tr>
            <td id="leftmenupanel" align=left valign=top class=ms-navframe style="border-top:solid 1px <%=headerLineColor%>; border-right:solid 1px <%=headerLineColor%>;">
<%            if (HttpView.currentContext().isShowFolders())
                {%>
    		    <img src="<%= request.getContextPath() %>/_.gif" width="<%=AppProps.getInstance().getNavigationBarWidth()%>" height=1><br>
            <%  } %>
                <% me.include(me.getView("menu") ,out); %>
            </td>
            <td style="width:100%; height:100%;" valign=top>
                <!--content area-->
                <table style="height:100%; width:100%;" cellpadding=0 cellspacing=0 border=0>
<%    if (me.getView("nav") instanceof HttpView && ((HttpView)me.getView("nav")).isVisible())
          { %>
        <tr>
            <td id="navpanel" valign="top" colspan=2 style="width:100%; padding:0;">
<%
            me.include(me.getView("nav"),out);
%>
            </td>
        </tr>
<%        } %>
<% } %>
        <tr>
            <td id="bodypanel" align=left valign=top class=normal style="width:100%; height:100%; padding:5;">
                <img height=1 width=<%=bean.getMinimumWidth()%> src="<%= contextPath %>/_.gif"><br>
                <!-- BODY -->
                <% me.include(me.getBody(),out); %>
                <!-- /BODY -->
            </td><%

            if (me.getView("right") instanceof HttpView && ((HttpView)me.getView("right")).isVisible())
				{ %>
            <!-- RIGHT -->
            <td align=left valign=top class=normal style="padding:5; height:100%; width:240;">
                <img height=1 width=240 src="<%= contextPath %>/_.gif"><br>
                <% me.include(me.getView("right"),out); %>
            </td>
            <!-- /RIGHT -->
<%				} %>
        </tr><%
    if (bean.showHeader())
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
    <script type="text/javascript">LABKEY.loadScripts();</script>
    <%=AnalyticsService.getTrackingScript()%>
    <!--<%= request.getHeader("User-Agent") %>--><%

    if (null != request.getAttribute(ViewServlet.REQUEST_STARTTIME))
    { %>
    <!--<%= "time " + (System.currentTimeMillis() - (Long)request.getAttribute(ViewServlet.REQUEST_STARTTIME)) + "ms" %> --><%
    }
%>
</body>
</html>
