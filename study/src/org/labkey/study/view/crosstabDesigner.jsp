<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HtmlString"%>
<%@ page import="org.labkey.api.util.element.Option"%>
<%@ page import="org.labkey.api.util.element.Select.SelectBuilder"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
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

    List<String> stats = Arrays.asList(bean.getStats());
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
                    <label>
                        <input id="cbCount" type=checkbox name=stats value=Count<%=checked(stats.contains("Count"))%>><span>&nbsp;Count of Records</span>
                    </label>
                    <br>
                    <label>
                        <input id="cbSum" type=checkbox name=stats value=Sum <%=checked(stats.contains("Sum"))%>><span>&nbsp;Sum</span>
                    </label>
                </td>
                <td>
                    <label>
                        <input id="cbStdDev" type=checkbox name=stats value=StdDev<%=checked(stats.contains("StdDev"))%>><span>&nbsp;StdDev</span>
                    </label>
                    <br>
                    <label>
                        <input id="cbMean" type=checkbox name=stats value=Mean<%=checked(stats.contains("Mean"))%>><span>&nbsp;Mean</span>
                    </label>
                </td>
                <td>
                    <label>
                        <input id="cbMin" type=checkbox name=stats value=Min<%=checked(stats.contains("Min"))%>><span>&nbsp;Min</span>
                    </label>
                    <br>
                    <label>
                        <input id="cbMax" type=checkbox name=stats value=Max<%=checked(stats.contains("Max"))%>><span>&nbsp;Max</span>
                    </label>
                </td>
                <td valign=top>
                    <label>
                        <input id="cbMedian" type=checkbox name=stats value=Median<%=checked(stats.contains("Median"))%>><span>&nbsp;Median</span>
                    </label>
                    <br>
                </td>
            </tr></table>
        </td>
    </tr>
    </table>
    <%= button("Submit").submit(true) %>
</labkey:form>

<%!
    public HtmlString fieldDropDown(String name, String id, Map<String, ColumnInfo> cols, String selected,
                                    boolean isStatField, boolean allowBlank, String changeHandler)
    {
        SelectBuilder builder = new SelectBuilder().name(name).id(id).className(null);
        if (null != changeHandler)
            builder.onChange(changeHandler);
        if (allowBlank)
            builder.addOption("", "");
        if (cols.containsKey("SequenceNum"))
            builder.addOption(new Option.OptionBuilder("Visit Id", "SequenceNum")
                    .selected("SequenceNum".equalsIgnoreCase(selected)));

        String subjectNoun = StudyService.get().getSubjectColumnName(getContainer());
        if (cols.containsKey(subjectNoun))
            builder.addOption(subjectNoun, subjectNoun).selected(null != selected && selected.equalsIgnoreCase(subjectNoun));

        FieldKey ptid = new FieldKey(null, subjectNoun);
        FieldKey seqNum = new FieldKey(null, "SequenceNum");

        for (ColumnInfo col : cols.values())
        {
            if (isStatField && !isValidStatColumn(col))
                continue;

            if (ptid.equals(col.getFieldKey()) || seqNum.equals(col.getFieldKey().encode()))
                continue;

            builder.addOption(col.getLabel(), col.getFieldKey().encode())
                    .selected(null != selected && selected.equalsIgnoreCase(col.getFieldKey().encode()));
        }
        return builder.getHtmlString();
    }

    boolean isValidStatColumn(ColumnInfo col)
    {
        Class cls = col.getJavaClass();
        if (Number.class.isAssignableFrom(cls) || cls.isPrimitive())
            return true;

        return String.class.isAssignableFrom(cls);
    }
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

    // create a map of column to type
    var columnTypeMap = {};
<%  for (ColumnInfo col : bean.getColumns().values())
    {
        Class cls = col.getJavaClass();
        if (Number.class.isAssignableFrom(cls) || cls.isPrimitive()) { %>
            columnTypeMap[<%=q(col.getFieldKey().encode())%>] = 'numeric';
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

                var span = el.next('//span');
                el.dom.disabled = !enabled;
                if (!enabled)
                {
                    el.dom.checked = false;
                    if (span)
                        span.addCls('labkey-disabled');
                }
                else
                {
                    if (span)
                        span.removeCls('labkey-disabled');
                }
            }
        });
    }
</script>
