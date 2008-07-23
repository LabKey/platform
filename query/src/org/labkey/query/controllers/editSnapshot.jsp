<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotDefinition" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.apache.commons.lang.BooleanUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName());
    Pair<String, String>[] params = context.getActionURL().getParameters();

    boolean showHistory = BooleanUtils.toBoolean(context.getActionURL().getParameter("showHistory"));
    String historyLabel = showHistory ? "Hide History" : "Show History";
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
<%  } %>
    </tr>
</table>
