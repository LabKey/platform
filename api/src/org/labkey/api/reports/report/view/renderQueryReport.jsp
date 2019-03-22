<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.action.NullSafeBindException" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.report.QueryReport" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
    }
%>
<%
    Report report = (Report)getModelBean();
    ViewContext context = getViewContext();

    String schemaName = report.getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
    String queryName = report.getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
    String viewName = report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName);

    String renderId = "queryReport-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    StringBuilder sb = new StringBuilder();

    if (report instanceof QueryReport)
    {
        BindException errors = new NullSafeBindException(this, "form");
        QueryView view = ((QueryReport)report).createQueryView(context, errors);

        if (errors.hasErrors())
        {
            sb.append("Unable to display the report");
            for (ObjectError error : errors.getAllErrors())
            {
                sb.append("\n");
                sb.append(error.getDefaultMessage());
            }
        }

        if (view != null && view.getTable() == null)
            sb.append("Unable to create table: " + view.getSettings().getQueryName() + ", you may not have access to that data.");
    }
%>

<%
    if (sb.length() > 0)
    {
%>
    <span class="labkey-error"><%=h(sb.toString(), true)%></span>
<%
    }
    else
    {
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

    <div id="<%= h(renderId)%>" style="width:100%"></div>
<%
    }
%>
