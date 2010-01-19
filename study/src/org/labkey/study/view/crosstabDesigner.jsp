<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.query.FieldKey"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.reports.ReportsController"%>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.study.StudyService" %>
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
            String subjectNoun = StudyService.get().getSubjectColumnName(getViewContext().getContainer());
            if (cols.containsKey(subjectNoun))
            {
                sb.append("<option value=\"").append(subjectNoun).append("\"");
                if (null != selected && selected.equalsIgnoreCase(subjectNoun))
                    sb.append(" selected");
                sb.append(">").append(StudyService.get().getSubjectColumnName(getViewContext().getContainer())).append("</option>");
            }
        }
        FieldKey ptid = new FieldKey(null,StudyService.get().getSubjectColumnName(getViewContext().getContainer()));
        FieldKey seqNum = new FieldKey(null, "SequenceNum");

        for (ColumnInfo col : cols.values())
        {
            if (numericOnly && !(Number.class.isAssignableFrom(col.getJavaClass()) || col.getJavaClass().isPrimitive()))
                continue;

            if (ptid.equals(col.getFieldKey()) || seqNum.equals(col.getFieldKey().encode()))
                continue;

            if (null != selected && selected.equalsIgnoreCase(col.getFieldKey().encode()))
                sb.append("<option selected value=\"");
            else
                sb.append("<option value=\"");

            sb.append(h(col.getFieldKey().encode()));
            sb.append("\">");
            sb.append(h(col.getLabel()));
            sb.append("</option>\n");
        }
        sb.append("</select>");
        return sb.toString();
    }
%>
