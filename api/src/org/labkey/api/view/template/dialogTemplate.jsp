<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.DialogTemplate" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    DialogTemplate me = (DialogTemplate) HttpView.currentView();
    PageConfig pageConfig = me.getModelBean();
    String contextPath = request.getContextPath();
%>
<html>
<head>
    <title><%=h(pageConfig.getTitle())%></title>
<%= PageFlowUtil.getStandardIncludes() %>
    <script type="text/javascript" src="<%=contextPath%>/labkey.js?<%=AppProps.getInstance().getServerSessionGUID()%>"></script>
    <script type="text/javascript" language="javascript">
        LABKEY.init(<%=PageFlowUtil.jsInitObject()%>);
    </script>
</head>

<body style="margin:0"<%= null != pageConfig.getFocus() ? " onload=\"document." + pageConfig.getFocus() + ".focus();\"" : "" %>>
    <table class="ms-main" style="height:100%;width:100%;border:0" border="0" cellspacing="0" cellpadding="0"><%

    if (pageConfig.shouldIncludeHeader())
    { %>
    <tr style="height:56px;">
        <td><% me.include(me.getView("header"), out); %></td>
    </tr>
    <tr>
        <td class="ms-titlearealine" height=1><IMG height=1 alt="" src="<%=contextPath%>/_.gif" width=1></td>
    </tr><%
    } %>
    <tr>
        <td class="fullScreenTable">
            <table cellpadding=0 cellspacing=0 style="height:100%; width:100%; background-color:#ffffff;">
                <tr><td height="20" style="background-color:#e5e5cc;"><img src="<%=contextPath%>/_.gif" width=400 height=1></td></tr>
                <tr>
                    <td id="dialogBody" height="100%" valign="top" align="left" class="normal" style="padding:10px;"><% me.include(me.getBody(), out);%>

                    </td></tr>
                <tr><td height="20" style="background-color:#e5e5cc;" >&nbsp;</td></tr>
            </table>
        </td>
    </tr>
    </table>
<script type="text/javascript">LABKEY.loadScripts();</script>
</body>
</html>