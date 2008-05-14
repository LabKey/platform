<%@ page import="org.labkey.api.pipeline.PipelineService"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.util.AppProps"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.springframework.validation.BindException" %>
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
    ActionURL updateDatasetURL = new ActionURL(StudyController.UpdateDatasetFormAction.class, context.getContainer());
    updateDatasetURL.addParameter("datasetId", dataset.getDataSetId());

    ActionURL manageTypesURL = new ActionURL(StudyController.ManageTypesAction.class, context.getContainer());

    ActionURL deleteDatasetURL = new ActionURL(StudyController.DeleteDatasetAction.class, context.getContainer());
    deleteDatasetURL.addParameter("id", dataset.getDataSetId());

    %><br><a href="<%=updateDatasetURL.getLocalURIString()%>"><%=PageFlowUtil.buttonImg("Dataset Visits")%></a><%
    %>&nbsp;<a href="<%=manageTypesURL.getLocalURIString()%>"><%=PageFlowUtil.buttonImg("Done")%></a><%
    %>&nbsp;<%=buttonLink("Delete Dataset", deleteDatasetURL,
        "return confirm('Are you sure you want to delete this dataset?  All related data and visitmap entries will also be deleted.')")%><%
}
if (0 != (permissions & ACL.PERM_UPDATE))
{
    if (pipelineSet)
    {
        ActionURL showImportDatasetURL = new ActionURL(StudyController.ShowImportDatasetAction.class, context.getContainer());
        showImportDatasetURL.addParameter("datasetId", dataset.getDataSetId());
        %>&nbsp;<a href="<%=showImportDatasetURL.getLocalURIString()%>"><%=PageFlowUtil.buttonImg("Import Data")%></a><%
    }
    ActionURL showHistoryURL = new ActionURL(StudyController.ShowUploadHistoryAction.class, context.getContainer());
    showHistoryURL.addParameter("id", dataset.getDataSetId());

    ActionURL editTypeURL = new ActionURL(StudyController.EditTypeAction.class, context.getContainer());
    editTypeURL.addParameter("datasetId", dataset.getDataSetId());

    %>&nbsp;<a href="<%=showHistoryURL.getLocalURIString()%>"><%=PageFlowUtil.buttonImg("Show Import History")%></a><%
    %>&nbsp;<a href="<%=editTypeURL.getLocalURIString()%>"><%=PageFlowUtil.buttonImg("Edit Dataset")%></a><%
}
if (!pipelineSet)
{
    include(new StudyController.RequirePipelineView(study, false, (BindException) request.getAttribute("errors")), out);
}
%>

<p />
<table>
<tr>
<td valign=top><%
    JspView typeSummary = new StudyController.StudyJspView<DataSetDefinition>(study, "typeSummary.jsp", dataset, (BindException)me.getErrors());
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
