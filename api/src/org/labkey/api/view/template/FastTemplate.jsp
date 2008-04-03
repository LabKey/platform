<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    HttpView me = HttpView.currentView();
    PageConfig bean = (PageConfig) me.getModelBean();
    String contextPath = request.getContextPath();
    String headerLineColor = "#" + WebTheme.getTheme().getHeaderLineColor();
%>
<html>
<head>
<title><%=h(bean.getTitle())%></title>
<%= PageFlowUtil.getStandardIncludes() %>
<%
if (null != bean.getStyleSheet())
    {
    %>
    <link href="<%=contextPath%><%=bean.getStyleSheet()%>" type="text/css" rel="stylesheet"/><%
    }
if (null != bean.getStyles() && 0 < bean.getStyles().length())
    {
    %>
    <style type="text/css"><!--
    <%=bean.getStyles()%>
    --></style><%
    }
%>
<script type="text/javascript" src="<%=contextPath%>/labkey.js?<%=AppProps.getInstance().getServerSessionGUID()%>"></script>
<script type="text/javascript" language="javascript">
    LABKEY.init(<%=PageFlowUtil.jsInitObject()%>);
    LABKEY.requiresScript('util.js');
</script>
<% if (null != bean.getScript() && 0 < bean.getScript().length())
	out.print(bean.getScript());
%>


<script type="text/javascript">
function sizeNavBar()
	{
    // when running in the test harness, we may not have a document.body:
    if (!document.body) return;
    var navBar = document.getElementById("navBar");
    if (!navBar || null == navBar) return;
    var width = window.innerWidth ? window.innerWidth - 15 : document.body.offsetWidth - 20;
    navBar.width = width - 146 - 7;
	}
</script>
</head>
<body onLoad=sizeNavBar() onResize=sizeNavBar() style="margin-top:0px; margin-left:0px; margin-right:0px;">
<%
me.include(me.getView("header"),out);
%><TABLE border=0 style="width:146; height:100%;" cellspacing=0 cellpadding=0 align=left>
<tr>
<td id="leftmenupanel" align=left valign=top class=ms-navframe style="border-top:solid 1px <%=headerLineColor%>; border-right:solid 1px <%=headerLineColor%>;">
<img src=<%=contextPath%>/_.gif width=146 height=1><br><%
	me.include(me.getView("menu"),out);
%></td></tr>
</TABLE><%
if (me.getView("nav") instanceof HttpView && ((HttpView)me.getView("nav")).isVisible())
    {
	me.include(me.getView("nav"),out);
    }
%><DIV id=divContent style="padding-top:5px;">
<%me.include(me.getBody(),out);%>
</DIV>
<script type="text/javascript">LABKEY.loadScripts();</script>
</body>
</html>