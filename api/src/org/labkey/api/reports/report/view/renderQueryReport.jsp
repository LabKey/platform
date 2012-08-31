<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Report report = (Report)getModelBean();
    ViewContext context = getViewContext();

    String schemaName = report.getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
    String queryName = report.getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
    String viewName = report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName);

    String renderId = "queryReport-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<script>

    Ext.onReady(function() {
        var qwp = new LABKEY.QueryWebPart({
            renderTo    : <%=q(renderId)%>,
            schemaName  : <%=q(schemaName)%>,
            queryName   : <%=q(queryName)%>,
            viewName    : <%=(viewName != null) ? q(viewName) : null%>,
            showReports : false,
            frame       : 'none',
            allowChooseQuery : false
        });
    });
</script>

<div id="<%= renderId%>" style="width:100%"></div>
