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

<body <%= null != pageConfig.getFocus() ? " onload=\"document." + pageConfig.getFocus() + ".focus();\"" : "" %>>
    <table class="labkey-main"><%

    if (pageConfig.shouldIncludeHeader())
    { %>
    <tr id="headerpanel" class="labkey-header-panel" height="56px">
        <td><% me.include(me.getView("header"), out); %></td>
    </tr>
    <tr>
        <td class="labkey-title-area-line"><IMG height=1 alt="" src="<%=contextPath%>/_.gif" width=1></td>
    </tr><%
    } %>
    <tr>
        <td class="labkey-full-screen-background">
            <table class="labkey-full-screen-table">
                <tr><td class="labkey-full-screen-table-panel"><img src="<%=contextPath%>/_.gif" width=400 height=1></td></tr>
                <tr>
                    <td id="dialogBody" class="labkey-dialog-body"><% me.include(me.getBody(), out);%>

                    </td></tr>
                <tr><td class="labkey-full-screen-table-panel">&nbsp;</td></tr>
            </table>
        </td>
    </tr>
    </table>
<script type="text/javascript">LABKEY.loadScripts();</script>
</body>
</html>
