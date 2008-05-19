<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.report.QueryReport"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<QueryReport.HeaderBean> me = (JspView<QueryReport.HeaderBean>) HttpView.currentView();
    QueryReport.HeaderBean bean = me.getModelBean();
%>
<%--
<%= textLink("Reports and Views", "begin.view") %>
--%>

<%
    if (bean.showCustomizeLink())
    {
%>
        [<a href="<%= bean.getCustomizeURL().getEncodedLocalURIString() %>">Customize View</a>]
<%
    }
%>