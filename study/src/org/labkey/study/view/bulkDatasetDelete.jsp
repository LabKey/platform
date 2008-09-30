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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<p>Please select the datasets you want to delete:</p>
<form action="bulkDatasetDelete.post" name="bulkDatasetDelete" method="POST">
<table class="labkey-data-region labkey-show-borders">
    <tr>
        <th>&nbsp;</th>
        <th>ID</th>
        <th>Label</th>
        <th>Category</th>
        <th>Number of data rows</th>
    </tr>

    <%
    Study study = getStudy();

    String cancelURL = new ActionURL(StudyController.ManageTypesAction.class, study.getContainer()).getLocalURIString();

    DataSetDefinition[] datasets = study.getDataSets();

    for (DataSetDefinition def : datasets)
    {
        ActionURL detailsURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, study.getContainer());
        detailsURL.addParameter("datasetId", def.getDataSetId());
        String detailsLink = detailsURL.getLocalURIString();
    %>

    <tr>
        <td><input type="checkbox" name="datasetIds" value="<%=def.getDataSetId()%>"></td>
        <td><a href="<%=detailsLink%>"><%=def.getDataSetId()%></a></td>
        <td><a href="<%=detailsLink%>"><%= h(def.getLabel()) %></a></td>
        <td><%= def.getCategory() != null ? h(def.getCategory()) : "&nbsp;" %></td>
        <td align="right"><%=StudyManager.getInstance().getNumDatasetRows(def)%></td>
    </tr>
    <%
        
    }
        
%>
</table>
<%=PageFlowUtil.generateSubmitButton("Delete Selected", "return confirm(\"Delete selected datasets?\");")%>
<%=PageFlowUtil.generateButton("Cancel", cancelURL)%>    

</form>
