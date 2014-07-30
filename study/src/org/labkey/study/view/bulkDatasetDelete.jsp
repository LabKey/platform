<%
/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.study.DataSet"%>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.DatasetController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>

<p>Please select the datasets you want to delete:</p>
<form action="<%=h(buildURL(DatasetController.BulkDatasetDeleteAction.class))%>" name="bulkDatasetDelete" method="POST">
<table class="labkey-data-region labkey-show-borders">
    <tr>
        <th><input type="checkbox" onchange="toggleAllRows(this);"></th>
        <th>ID</th>
        <th>Label</th>
        <th>Category</th>
        <th>Type</th>
        <th>Number of data rows</th>
    </tr>

    <%
    Study study = getStudy();

    ActionURL cancelURL = new ActionURL(StudyController.ManageTypesAction.class, study.getContainer());

    for (DataSet def : study.getDataSetsByType(DataSet.TYPE_STANDARD, DataSet.TYPE_PLACEHOLDER))
    {
        ActionURL detailsURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, study.getContainer());
        detailsURL.addParameter("datasetId", def.getDataSetId());
        String detailsLink = detailsURL.getLocalURIString();
    %>

    <tr>
        <td><input type="checkbox" name="datasetIds" value="<%=def.getDataSetId()%>"></td>
        <td><a href="<%=detailsLink%>"><%=def.getDataSetId()%></a></td>
        <td><a href="<%=detailsLink%>"><%= h(def.getLabel()) %></a></td>
        <td><%= h(def.getViewCategory() != null ? def.getViewCategory().getLabel() : null) %></td>
        <td><%= def.getType() %></td>
        <td align="right"><%=StudyManager.getInstance().getNumDatasetRows(getUser(), def)%></td>
    </tr>
    <%
        
    }
        
%>
</table>
<%= button("Delete Selected").id("delete_btn").submit(true).onClick(
        "if (confirm('Delete selected datasets?')){" +
            "Ext4.get(this).replaceCls('labkey-button', 'labkey-disabled-button');" +
            "return true;" +
        "} " +
            "else return false;")%>
<%= button("Cancel").href(cancelURL) %>

</form>

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
