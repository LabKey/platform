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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.query.QueryParam"%>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor"%>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.query.DatasetQueryView" %>
<%@ page import="org.labkey.study.reports.StudyQueryReport" %>
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
    JspView<ReportsController.CreateQueryReportBean> me = (JspView<org.labkey.study.controllers.reports.ReportsController.CreateQueryReportBean>) HttpView.currentView();
    ReportsController.CreateQueryReportBean bean = me.getModelBean();
    Container container = getContainer();

    String paramStart = QueryParam.schemaName.toString() + "=" + StudySchema.getInstance().getSchemaName();
%>
<script type="text/javascript">
    function verifyLabel()
    {

        var labelElem = Ext4.DomQuery.selectNode('#label');
        var viewName = Ext4.DomQuery.selectNode('#viewName');
        var labelValue = '';

        if (labelElem.value)
            labelValue =  labelElem.value.replace(/^\s+|\s+$/g, '');  //regexp to match leading and trailing whitespace, same as the missing String.trim() method

        if (labelValue.length === 0)
        {
            alert("Label cannot be empty");
            return false;
        }
        viewName.value = labelValue;
        return true;
    }

    function onUpdateDataset()
    {
        var selection = Ext4.DomQuery.selectNode('#datasetSelection');
        var value = selection.value;
        if (value)
        {
            var redirect = document.getElementById('redirectToReport');
            var redirectToDataset = document.getElementById('redirectToDataset');
            var showWithDataset = document.getElementById('showWithDataset');

            var idx = value.indexOf("&datasetId=");
            if (idx == -1)
            {
                redirect.value = "true";
                redirectToDataset.value = "-1";
                showWithDataset.value = "0";
            }
            else
            {
                var dataset = value.substr(idx + 11, value.length);
                redirectToDataset.value = dataset;
                showWithDataset.value = dataset;
                redirect.value = "false";
            }
        }
    }

    Ext4.onReady(onUpdateDataset);

</script>

<labkey:form action="<%=new ActionURL(ReportsController.SaveReportAction.class, container)%>" method="POST">
<input type="hidden" name="<%=ReportDescriptor.Prop.reportType%>" value="<%=h(StudyQueryReport.TYPE)%>">
<input type="hidden" id="redirectToReport" name="redirectToReport" value="true">
<input type="hidden" id="redirectToDataset" name="redirectToDataset" value="-1">
<input type="hidden" id="showWithDataset" name="showWithDataset" value="0">
<input type="hidden" name="<%=h(ActionURL.Param.returnUrl.name())%>" value="<%=h(bean.getSrcURL().getLocalURIString())%>">
<input type="hidden" id="viewName" name="<%=h(QueryParam.viewName.toString())%>" value="">
<input type="hidden" name="dataRegionName" value="<%=h(DatasetQueryView.DATAREGION)%>">
<table>
    <tr>
        <th align="right">Label for View</th>
        <td><input type="text" maxlength="50" size="30" id="label" name="label"></td>
    </tr>
    <tr>
        <th align="right">Base Dataset</th>
        <td>
            <select id="datasetSelection" name="params" onchange="onUpdateDataset();">
                <%
                    Map<String, DatasetDefinition> datasetMap = bean.getDatasetDefinitions();
                    for (String name : bean.getTableAndQueryNames())
                    {
                        Dataset def = datasetMap.get(name);
                        if (def != null) {
                            String label = !def.getLabel().equals(def.getName()) ? def.getName() + " (" + def.getLabel() + ")" : def.getLabel();
                %>
                        <option value="<%= h(paramStart + '&' + QueryParam.queryName.toString() + '=' + u(name) + "&datasetId=" + def.getDatasetId()) %>"<%=selected(name.equals(bean.getQueryName()))%>><%= h(label) %></option>
                <%
                        }
                    }
                %>
            </select>&nbsp;<%= textLink("Modify Dataset List (Advanced)", bean.getQueryCustomizeURL()) %>

        </td>
    </tr>
    <tr>
        <td></td>
        <td>
            <%= button("Create View").submit(true).onClick("return verifyLabel();") %>
            <br>After creating the new view, you will have the chance to customize its appearance.
        </td>
    </tr>
</table>
</labkey:form>
