<%
/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryParam"%>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor"%>
<%@ page import="org.labkey.api.reports.report.ReportUrls"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController.CreateCrosstabBean" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.reports.StudyCrosstabReport" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CreateCrosstabBean> me = (JspView<ReportsController.CreateCrosstabBean>) HttpView.currentView();
    CreateCrosstabBean bean = me.getModelBean();

    ActionURL returnURL = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getContainer());
%>
<labkey:form action="<%=h(buildURL(ReportsController.ParticipantCrosstabAction.class))%>" method="GET">
<input type="hidden" name="<%=QueryParam.schemaName%>" value="<%=h(StudySchema.getInstance().getSchemaName())%>">
<input type="hidden" name="<%=ReportDescriptor.Prop.reportType%>" value="<%=h(StudyCrosstabReport.TYPE)%>">
<input type="hidden" name="redirectUrl" value="<%=h(returnURL)%>">
<table>
    <tr>
        <td>Dataset</td>
        <td>
            <select name="<%=QueryParam.queryName%>">
                <%
                    for (DatasetDefinition dataset : bean.getDatasets())
                    {
                %>
                <option value="<%=h(dataset.getName())%>"><%= h(dataset.getDisplayString()) %></option>
                <%
                    }
                %>
            </select>
        </td>
    </tr>
    <tr>
        <td>Visit</td>
        <td>
            <select name="<%=h(VisitImpl.VISITKEY)%>">
                <option value="0">All Visits</option>
                <%
                    for (VisitImpl visit : bean.getVisits())
                    {
                %>
                <option value="<%= visit.getRowId() %>"><%= h(visit.getDisplayString()) %></option>
                <%
                    }
                %>
            </select>
        </td>
    </tr>
    <tr>
        <td></td>
        <td>
            <%= button("Next").submit(true) %>
            <%= button("Cancel").href(returnURL) %>
        </td>
    </tr>
</table>
</labkey:form>