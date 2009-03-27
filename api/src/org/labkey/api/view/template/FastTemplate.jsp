<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    HttpView me = HttpView.currentView();
    PageConfig bean = (PageConfig) me.getModelBean();
    String contextPath = request.getContextPath();
    Container c = me.getViewContext().getContainer();
%>
<html>
<head>
<title><%=h(bean.getTitle())%></title>
<%= PageFlowUtil.getStandardIncludes(c) %>
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
%><TABLE width="146" align=left>
<tr>
<td id="leftmenupanel" class="labkey-site-nav-panel">
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