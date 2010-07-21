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
<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission"%>
<%@ page import="org.labkey.api.security.permissions.Permission"%>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission"%>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>) HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    Set<Class<? extends Permission>> permissions = context.getContainer().getPolicy().getPermissions(context.getUser());
    StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
    VisitManager visitManager = StudyManager.getInstance().getVisitManager(study);
    boolean pipelineSet = null != PipelineService.get().findPipelineRoot(HttpView.currentContext().getContainer());
%>
<% if (permissions.contains(AdminPermission.class))
{
    ActionURL viewDatasetURL = new ActionURL(StudyController.DatasetAction.class, context.getContainer());
    viewDatasetURL.addParameter("datasetId", dataset.getDataSetId());

    ActionURL updateDatasetURL = new ActionURL(StudyController.UpdateDatasetVisitMappingAction.class, context.getContainer());
    updateDatasetURL.addParameter("datasetId", dataset.getDataSetId());

    ActionURL manageTypesURL = new ActionURL(StudyController.ManageTypesAction.class, context.getContainer());

    ActionURL deleteDatasetURL = new ActionURL(StudyController.DeleteDatasetAction.class, context.getContainer());
    deleteDatasetURL.addParameter("id", dataset.getDataSetId());

    %><br><%=PageFlowUtil.generateButton("View Data", viewDatasetURL)%><%
    if (study.getTimepointType() != TimepointType.CONTINUOUS)
    {
    %>&nbsp;<%=PageFlowUtil.generateButton("Edit Associated " + visitManager.getPluralLabel(), updateDatasetURL)%><%
    }
    %>&nbsp;<%=PageFlowUtil.generateButton("Manage Datasets", manageTypesURL)%><%
    %>&nbsp;<%=generateButton("Delete Dataset", deleteDatasetURL,
        "return confirm('Are you sure you want to delete this dataset?  All related data and visitmap entries will also be deleted.')")%><%
}
if (permissions.contains(UpdatePermission.class))
{
    ActionURL showHistoryURL = new ActionURL(StudyController.ShowUploadHistoryAction.class, context.getContainer());
    showHistoryURL.addParameter("id", dataset.getDataSetId());

    ActionURL editTypeURL = new ActionURL(StudyController.EditTypeAction.class, context.getContainer());
    editTypeURL.addParameter("datasetId", dataset.getDataSetId());

    %>&nbsp;<%=PageFlowUtil.generateButton("Show Import History", showHistoryURL)%><%
    %>&nbsp;<%=PageFlowUtil.generateButton("Edit Definition", editTypeURL)%><%
}
if (!pipelineSet)
{
    include(new StudyController.RequirePipelineView(study, false, (BindException) request.getAttribute("errors")), out);
}
%>

<% WebPartView.startTitleFrame(out, "Dataset Properties", null, "100%", null); %>
<table id="details" width="600px">
    <tr>
        <td class=labkey-form-label>Name</td>
        <th align=left><%= h(dataset.getName()) %></th>

        <td class=labkey-form-label>ID</td>
        <td align=left><%= dataset.getDataSetId() %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Label</td>
        <td><%= h(dataset.getLabel()) %></td>

        <td class=labkey-form-label>Category</td>
        <td><%= h(dataset.getCategory()) %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Cohort Association</td>
        <td><%= dataset.getCohort() != null ? h(dataset.getCohort().getLabel()) : "All" %></td>

        <td class=labkey-form-label><%=visitManager.getLabel()%> Date Column</td>
        <td><%= h(dataset.getVisitDatePropertyName()) %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Additional Key Column</td>
        <td><%= dataset.getKeyPropertyName() != null ? h(dataset.getKeyPropertyName()) : "None" %></td>

        <td rowspan="3" class=labkey-form-label>Description</td>
        <td rowspan="3"><%= h(dataset.getDescription()) %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Demographic Data <%=helpPopup("Demographic Data", "Demographic data appears only once for each " +
        StudyService.get().getSubjectNounSingular(getViewContext().getContainer()).toLowerCase() + 
        " in the study.")%></td>
        <td><%= dataset.isDemographicData() ? "true" : "false" %></td>
    </tr>
    <tr>
        <td class=labkey-form-label>Show In Overview</td>
        <td><%= dataset.isShowByDefault() ? "true" : "false" %></td>
    </tr>
</table>
<% WebPartView.endTitleFrame(out); %>
<p>
<%
    JspView typeSummary = new StudyController.StudyJspView<DataSetDefinition>(study, "typeSummary.jsp", dataset, (BindException)me.getErrors());
    typeSummary.setTitle("Dataset Fields");
    typeSummary.setFrame(WebPartView.FrameType.TITLE);
    me.include(typeSummary, out);
%>

<% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
<% WebPartView.startTitleFrame(out, "Visit Associations", null, "100%", null); %>
<table><%
    List<VisitDataSet> visitList = StudyManager.getInstance().getMapping(dataset);
    HashMap<Integer,VisitDataSet> visitMap = new HashMap<Integer, VisitDataSet>();
    for (VisitDataSet vds : visitList)
        visitMap.put(vds.getVisitRowId(), vds);
    boolean hasVisitAssociations = false;
    for (VisitImpl visit : study.getVisits(Visit.Order.DISPLAY))
    {
        VisitDataSet vm = visitMap.get(visit.getRowId());
        if (vm != null)
        {
            hasVisitAssociations = true;
            VisitDataSetType type = vm.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL;
            %><tr>
                <td><%= visit.getDisplayString() %></td>
                <td><%= type == VisitDataSetType.NOT_ASSOCIATED ? "&nbsp;" : type.getLabel() %></td>
            </tr><%
        }
    }
    if (!hasVisitAssociations)
    {
    %><tr><td><i>This dataset isn't explicitly associated with any visits.</i></td></tr><%
    }
%></table>
<% WebPartView.endTitleFrame(out); %>
<% } %>
