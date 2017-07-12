<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Dataset"%>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.DatasetController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>

<p>Please select the datasets you want to delete:</p>
<labkey:form action="<%=h(buildURL(DatasetController.BulkDatasetDeleteAction.class))%>" name="bulkDatasetDelete" method="POST">
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header"><input type="checkbox" onchange="toggleAllRows(this);"></td>
        <td class="labkey-column-header">ID</td>
        <td class="labkey-column-header">Label</td>
        <td class="labkey-column-header">Category</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">Number of data rows</td>
    </tr>

    <%
    Study study = getStudy();
    int rowCount = 0;
    ActionURL cancelURL = new ActionURL(StudyController.ManageTypesAction.class, study.getContainer());

    for (Dataset def : StudyManager.getInstance().getDatasetDefinitionsLocal(study, null, Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER))
    {
        ActionURL detailsURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, study.getContainer());
        detailsURL.addParameter("datasetId", def.getDatasetId());
        String detailsLink = detailsURL.getLocalURIString();
        rowCount++;
    %>

    <tr class="<%=h(rowCount % 2 == 1 ? "labkey-alternate-row" : "labkey-row")%>">
        <td><input type="checkbox" name="datasetIds" value="<%=def.getDatasetId()%>"></td>
        <td><a href="<%=detailsLink%>"><%=def.getDatasetId()%></a></td>
        <td><a href="<%=detailsLink%>"><%= h(def.getLabel()) %></a></td>
        <td><%= h(def.getViewCategory() != null ? def.getViewCategory().getLabel() : null) %></td>
        <td><%= def.getType() %></td>
        <td align="right"><%=StudyManager.getInstance().getNumDatasetRows(getUser(), def)%></td>
    </tr>
    <%
        
    }
        
%>
</table>
<br/>
<%= button("Delete Selected").id("delete_btn").submit(true).onClick(
        "if (confirm('Are you sure you want to delete the selected datasets? This action cannot be undone.')){" +
            "Ext4.get(this).replaceCls('labkey-button', 'labkey-disabled-button');" +
            "return true;" +
        "} " +
            "else return false;")%>
<%= button("Cancel").href(cancelURL) %>

</labkey:form>

<script type="text/javascript">
    function toggleAllRows(checkbox)
    {
        var i;
        var checked = checkbox.checked;
        var elements = document.getElementsByName("datasetIds");
        for (i in elements)
        {
            var e = elements[i];
            e.checked = checked;
        }
    }
</script>
