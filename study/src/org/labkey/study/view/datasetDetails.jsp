<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.util.AppProps"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.OldStudyController" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>) HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    int permissions = context.getContainer().getAcl().getPermissions(context.getUser());
    Study study = StudyManager.getInstance().getStudy(context.getContainer());
    String contextPath = AppProps.getInstance().getContextPath();
    boolean pipelineSet = false;
    try
    {
        pipelineSet = null != PipelineService.get().findPipelineRoot(HttpView.currentContext().getContainer());
    }
    catch (SQLException x)
    {
    }
%>
<table>
    <tr><td class=ms-searchform>Name</td><th class=normal align=left><%= h(dataset.getName()) %></th></tr>
    <tr><td class=ms-searchform>Label</td><td class=normal><%= h(dataset.getLabel()) %></td></tr>
    <tr><td class=ms-searchform>Display String</td><td class=normal><%= h(dataset.getDisplayString()) %></td></tr>
    <tr><td class=ms-searchform>Category</td><td class=normal><%= h(dataset.getCategory()) %></td></tr>
    <tr><td class=ms-searchform>Cohort</td><td class=normal><%= dataset.getCohort() != null ? h(dataset.getCohort().getLabel()) : "All" %></td></tr>
    <tr><td class="ms-searchform">Demographic Data <%=helpPopup("Demographic Data", "Demographic data appears only once for each participant in the study.")%></td><td class=normal><%= dataset.isDemographicData() ? "true" : "false" %></td></tr>
    <tr><td class=ms-searchform>Visit Date Column</td><td class=normal><%= h(dataset.getVisitDatePropertyName()) %></td></tr>
    <tr><td class=ms-searchform>Show By Default</td><td class=normal><%= dataset.isShowByDefault() ? "true" : "false" %></td></tr>
    <tr><td class=ms-searchform>Description</td><td class=normal><%= h(dataset.getDescription()) %></td></tr>
</table>
<% if (0 != (permissions & ACL.PERM_ADMIN))
{
    %><br><a href="updateDatasetForm.view?datasetId=<%=dataset.getDataSetId()%>"><%=PageFlowUtil.buttonImg("Dataset Visits")%></a><%
    %>&nbsp;<a href="manageTypes.view"><%=PageFlowUtil.buttonImg("Done")%></a><%
    %>&nbsp;<a href="confirmDeleteDataset.view?id=<%=dataset.getDataSetId()%>"><%=PageFlowUtil.buttonImg("Delete Dataset")%></a><%
}
if (0 != (permissions & ACL.PERM_UPDATE))
{
    if (pipelineSet)
    {
        %>&nbsp;<a href="showImportDataset.view?datasetId=<%=dataset.getDataSetId()%>"><%=PageFlowUtil.buttonImg("Upload Data")%></a><%
    }
    %>&nbsp;<a href="showUploadHistory.view?id=<%=dataset.getDataSetId()%>"><%=PageFlowUtil.buttonImg("Show Upload History")%></a><%
    %>&nbsp;<a href="editType.view?datasetId=<%=dataset.getDataSetId()%>"><%=PageFlowUtil.buttonImg("Edit Dataset")%></a><%
}
if (!pipelineSet)
{
    include(new OldStudyController.RequirePipelineView(study, false), out);
}
%>

<p />
<table>
<tr>
<td valign=top><%
    JspView typeSummary = new OldStudyController.StudyJspView<DataSetDefinition>(study, "typeSummary.jsp", dataset);
    typeSummary.setTitle("Dataset Schema");
    typeSummary.setFrame(WebPartView.FrameType.TITLE);
    me.include(typeSummary, out);
    %>
</td>
<td><img src="<%=contextPath%>/_.gif" height=1 width=10 alt=""></td>
<td valign=top>
<% WebPartView.startTitleFrame(out, "Visit Map", null, null, null); %>
<table><%
    List<VisitDataSet> visitList = StudyManager.getInstance().getMapping(dataset);
    HashMap<Integer,VisitDataSet> visitMap = new HashMap<Integer, VisitDataSet>();
    for (VisitDataSet vds : visitList)
        visitMap.put(vds.getVisitRowId(), vds);
    for (Visit visit : study.getVisits())
    {
        VisitDataSet vm = visitMap.get(visit.getRowId());
        VisitDataSetType type = vm == null ? VisitDataSetType.NOT_ASSOCIATED : vm.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL;
        %><tr>
            <td><%= visit.getDisplayString() %></td>
            <td><%= type == VisitDataSetType.NOT_ASSOCIATED ? "&nbsp;" : type.name() %></td>
        </tr><%
    }
%></table>
<% WebPartView.endTitleFrame(out); %>
</td></tr>
</table>
