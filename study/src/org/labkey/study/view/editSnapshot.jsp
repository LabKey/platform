<%@ page import="org.apache.commons.lang.BooleanUtils" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.DisplayColumn" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotDefinition" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotService" %>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName());
    Pair<String, String>[] params = context.getActionURL().getParameters();

    Map<String, String> columnMap = new HashMap<String, String>();
    for (String name : bean.getSnapshotColumns())
        columnMap.put(name, name);

    boolean isAutoUpdateable = false;//QuerySnapshotService.get(bean.getSchemaName()) instanceof QuerySnapshotService.AutoUpdateable;
    boolean isEdit = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName()) != null;

    boolean showHistory = BooleanUtils.toBoolean(context.getActionURL().getParameter("showHistory"));
    String historyLabel = showHistory ? "Hide History" : "Show History";

    boolean showDataset = BooleanUtils.toBoolean(context.getActionURL().getParameter("showDataset"));
    String datasetLabel = showDataset ? "Hide Dataset Props" : "Show Dataset Props";
%>

<labkey:errors/>

<table cellpadding="0" class="normal">
<%  if (def != null) { %>
    <tr><td class="ms-searchform">Name</td><td><%=h(def.getName())%></td>
<%  } %>
    <tr><td class="ms-searchform">Description</td><td></td>
    <tr><td class="ms-searchform">Created By</td><td></td>
    <tr><td class="ms-searchform">Created</td><td></td>
    <tr><td class="ms-searchform">Last Updated</td><td></td>
</table>

<table cellpadding="0" class="normal">
    <tr><td>&nbsp;</td></tr>
    <tr>
        <td><%=PageFlowUtil.buttonLink("Update Snapshot", PageFlowUtil.urlProvider(QueryUrls.class).urlUpdateSnapshot(context.getContainer()).addParameters(params), "return confirm('Updating will replace all current data with a fresh snapshot');")%></td>
<%  if (def != null) { %>
        <td><%=PageFlowUtil.buttonLink("Source Query", bean.getSchema().urlFor(QueryAction.sourceQuery, def.getQueryDefinition()))%></td>
        <td><%=PageFlowUtil.buttonLink(historyLabel, context.cloneActionURL().replaceParameter("showHistory", String.valueOf(!showHistory)))%></td>
        <td><%=PageFlowUtil.buttonLink(datasetLabel, context.cloneActionURL().replaceParameter("showDataset", String.valueOf(!showDataset)))%></td>
<%  } %>
    </tr>
</table>

<form action="" method="post">
    <table cellpadding="0" class="normal">
        <tr><td colspan="10" style="padding-top:14; padding-bottom:2"><span class="ms-announcementtitle">Snapshot Name and Type</span></td></tr>
        <tr><td colspan="10" width="100%" class="ms-titlearealine"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td>Snapshot Name:</td><td><input type="text" name="snapshotName" <%=isEdit ? "readonly" : ""%> value="<%=StringUtils.trimToEmpty(bean.getSnapshotName())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Manual Refresh</td><td><input <%=isAutoUpdateable ? "" : "disabled"%> checked type="radio" name="manualRefresh"></td></tr>
        <tr><td>Automatic Refresh</td><td><input disabled="<%=isAutoUpdateable ? "" : "disabled"%>" type="radio" name="automaticRefresh"></td></tr>

<%--
        <tr><td><input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>"></td></tr>
--%>
        <tr><td></td><td><table class="normal">
    <%  for (DisplayColumn col : QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean)) { %>
            <tr><td><input type="hidden" name="snapshotColumns" value="<%=col.getName()%>"></td></tr>
    <%  } %>
        </table></td></tr>
    </table>
</form>
