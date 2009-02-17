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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.query.QueryParam"%>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.Visit" %>
<%@ page import="org.labkey.study.query.DataSetQueryView" %>
<%@ page import="org.labkey.study.reports.StudyCrosstabReport" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReportsController.CrosstabDesignBean> me = (JspView<ReportsController.CrosstabDesignBean>) HttpView.currentView();
    ReportsController.CrosstabDesignBean bean = me.getModelBean();

    List stats = Arrays.asList(bean.getStats());

%>

<form action="" method="post">
    Design crosstab view.

    <table>
        <tr>
            <td></td>
            <td class="labkey-bordered" style="font-weight:bold">Column Field<%=fieldDropDown("colField", bean.getColumns(), bean.getColField(), false, true)%></td>

        </tr>
    <tr>
        <td class="labkey-bordered" style="font-weight:bold">Row<br>Field<br><%=fieldDropDown("rowField", bean.getColumns(), bean.getRowField(), false, true)%>
        </td>
        <td class="labkey-bordered" style="font-weight:bold" width="100%">Compute Statistics for Field<br><%=fieldDropDown("statField", bean.getColumns(), bean.getStatField(), true, false)%><br>
            <br>
            Compute<br>
            <table><tr><td>
            <input type=checkbox name=stats value=Count <%=stats.contains("Count") ? "CHECKED" : ""%> > Count <br>
            <input type=checkbox name=stats value=Sum <%=stats.contains("Sum") ? "CHECKED" : ""%> > Sum <br>
            </td>
                <td>
                <input type=checkbox name=stats value=StdDev  <%=stats.contains("StdDev") ? "CHECKED" : ""%> > StdDev <br>
            <input type=checkbox name=stats value=Mean <%=stats.contains("Mean") ? "CHECKED" : ""%> > Mean <br>
            </td>
                <td>
                <input type=checkbox name=stats value=Min <%=stats.contains("Min") ? "CHECKED" : ""%> > Min <br>
            <input type=checkbox name=stats value=Max <%=stats.contains("Max") ? "CHECKED" : ""%> > Max <br>
                    </td>
                <td valign=top>
            <input type=checkbox name=stats value=Median <%=stats.contains("Median") ? "CHECKED" : ""%> > Median<br>
            </td>
                </tr></table>
        </td>
    </tr>
    </table>

    <input type="hidden" name="<%=ReportDescriptor.Prop.reportType%>" value="<%=StudyCrosstabReport.TYPE%>">
<%--
    <input type="hidden" name="<%=DataSetDefinition.DATASETKEY%>" value="<%=bean.getDatasetId()%>">
    <input type="hidden" name="<%=Visit.VISITKEY%>" value="<%=bean.getVisitRowId()%>">
--%>
    <input type="hidden" name="<%=QueryParam.queryName%>" value="<%=bean.getQueryName()%>">
    <input type="hidden" name="<%=QueryParam.viewName%>" value="<%=StringUtils.trimToEmpty(bean.getViewName())%>">
    <input type="hidden" name="<%=QueryParam.schemaName%>" value="<%=bean.getSchemaName()%>">
    <input type="hidden" name="<%=QueryParam.dataRegionName%>" value="<%=DataSetQueryView.DATAREGION%>">

    <%=PageFlowUtil.generateSubmitButton("Submit")%>
</form>

<%!
    public String fieldDropDown(String name, Map<String, ColumnInfo> cols, String selected, boolean numericOnly, boolean allowBlank)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<select name=\"").append(name).append("\">\n");
        if (allowBlank)
            sb.append("<option></option>");

        if (cols.containsKey("SequenceNum"))
        {
            sb.append("<option value=\"SequenceNum\"");
            if ("SequenceNum".equalsIgnoreCase(selected))
                sb.append(" selected");
            sb.append(">Visit Id</option>");
        }
        if (!numericOnly)
        {
            if (cols.containsKey("ParticipantId"))
            {
                sb.append("<option value=\"ParticipantId\"");
                if (null != selected && selected.equalsIgnoreCase("ParticipantId"))
                    sb.append(" selected");
                sb.append(">Participant Id</option>");
            }
        }
        for (ColumnInfo col : cols.values())
        {
            if (numericOnly && !(Number.class.isAssignableFrom(col.getJavaClass()) || col.getJavaClass().isPrimitive()))
                continue;

            if ("ParticipantId".equalsIgnoreCase(col.getAlias()) || "SequenceNum".equalsIgnoreCase(col.getAlias()))
                continue;

            if (null != selected && selected.equalsIgnoreCase(col.getAlias()))
                sb.append("<option selected value=\"");
            else
                sb.append("<option value=\"");

            sb.append(h(col.getAlias()));
            sb.append("\">");
            sb.append(h(col.getCaption()));
            sb.append("</option>\n");
        }
        sb.append("</select>");
        return sb.toString();
    }
%>
