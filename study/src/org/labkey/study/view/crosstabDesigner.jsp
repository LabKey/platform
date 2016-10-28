<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.template.ClientDependencies"%>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<ReportsController.CrosstabDesignBean> me = (JspView<ReportsController.CrosstabDesignBean>) HttpView.currentView();
    ReportsController.CrosstabDesignBean bean = me.getModelBean();

    List stats = Arrays.asList(bean.getStats());
%>

<labkey:form action="" method="post">
    Design crosstab report.

    <table>
        <tr>
            <td></td>
            <td class="labkey-bordered" style="padding:3px;font-weight:bold">Column Field&nbsp;<%=fieldDropDown("colField", "colSelection", bean.getColumns(), bean.getColField(), false, true, null)%></td>

        </tr>
    <tr>
        <td class="labkey-bordered" style="padding:3px;font-weight:bold">Row&nbsp;Field<br><%=fieldDropDown("rowField", "rowSelection", bean.getColumns(), bean.getRowField(), false, true, null)%>
        </td>
        <td class="labkey-bordered" style="padding:3px;font-weight:bold" width="100%">Measurement Field<br>
            <%=fieldDropDown("statField", "statSelection", bean.getColumns(), bean.getStatField(), true, false, "updateSelection();")%><br>
            <br>
            Compute<br>
            <table><tr>
                <td>
                    <input id="cbCount" type=checkbox name=stats value=Count<%=checked(stats.contains("Count"))%>><span>&nbsp;Count of Records</span><br>
                    <input id="cbSum" type=checkbox name=stats value=Sum <%=checked(stats.contains("Sum"))%>><span>&nbsp;Sum</span>
                </td>
                <td>
                    <input id="cbStdDev" type=checkbox name=stats value=StdDev<%=checked(stats.contains("StdDev"))%>><span>&nbsp;StdDev</span><br>
                    <input id="cbMean" type=checkbox name=stats value=Mean<%=checked(stats.contains("Mean"))%>><span>&nbsp;Mean</span>
                </td>
                <td>
                    <input id="cbMin" type=checkbox name=stats value=Min<%=checked(stats.contains("Min"))%>><span>&nbsp;Min</span><br>
                    <input id="cbMax" type=checkbox name=stats value=Max<%=checked(stats.contains("Max"))%>><span>&nbsp;Max</span>
                </td>
                <td valign=top>
                    <input id="cbMedian" type=checkbox name=stats value=Median<%=checked(stats.contains("Median"))%>><span>&nbsp;Median</span><br>
                </td>
            </tr></table>
        </td>
    </tr>
    </table>
    <%= button("Submit").submit(true) %>
</labkey:form>

<%!
    public String fieldDropDown(String name, String id, Map<String, ColumnInfo> cols, String selected,
                                boolean isStatField, boolean allowBlank, String changeHandler)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<select name=\"").append(name).append("\" id=\"").append(id).append("\"");
        if (changeHandler != null)
            sb.append(" onChange=\"").append(changeHandler).append("\"");
        sb.append(">\n");
        if (allowBlank)
            sb.append("<option></option>");

        if (cols.containsKey("SequenceNum"))
        {
            sb.append("<option value=\"SequenceNum\"");
            if ("SequenceNum".equalsIgnoreCase(selected))
                sb.append(" selected");
            sb.append(">Visit Id</option>");
        }

        String subjectNoun = StudyService.get().getSubjectColumnName(getContainer());
        if (cols.containsKey(subjectNoun))
        {
            sb.append("<option value=\"").append(subjectNoun).append("\"");
            if (null != selected && selected.equalsIgnoreCase(subjectNoun))
                sb.append(" selected");
            sb.append(">").append(StudyService.get().getSubjectColumnName(getContainer())).append("</option>");
        }
        FieldKey ptid = new FieldKey(null,StudyService.get().getSubjectColumnName(getContainer()));
        FieldKey seqNum = new FieldKey(null, "SequenceNum");

        for (ColumnInfo col : cols.values())
        {
            if (isStatField && !isValidStatColumn(col))
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

    boolean isValidStatColumn(ColumnInfo col)
    {
        Class cls = col.getJavaClass();
        if (Number.class.isAssignableFrom(cls) || cls.isPrimitive())
            return true;

        if (String.class.isAssignableFrom(cls))
            return true;

        return false;
    }
%>

<script type="text/javascript">

    // create a map of column to type
    var columnTypeMap = {};
<%  for (ColumnInfo col : bean.getColumns().values())
    {
        Class cls = col.getJavaClass();
        if (Number.class.isAssignableFrom(cls) || cls.isPrimitive()) { %>
            columnTypeMap['<%=col.getFieldKey().encode()%>'] = 'numeric';
<%      }
    } %>

    Ext4.onReady(function()
    {
        updateSelection();
    });

    function updateSelection()
    {
        var statSel = Ext4.get('statSelection').getValue();
        var enabled = false;

        if (statSel && statSel in columnTypeMap)
            enabled = true;

        setEnabled('cbSum', enabled);
        setEnabled('cbMean', enabled);
        setEnabled('cbMedian', enabled);
        setEnabled('cbStdDev', enabled);
    }

    function setEnabled(id, enabled)
    {
        Ext4.onReady(function() {
            var el = Ext4.get(id);
            if (el) {

                el.dom.disabled = !enabled;
                if (!enabled)
                {
                    el.dom.checked = false;
                    var span = el.next('//span');
                    if (span)
                        span.addCls('labkey-disabled');
                }
                else
                {
                    var span = el.next('//span');
                    if (span)
                        span.removeCls('labkey-disabled');
                }
            }
        });
    }
</script>
