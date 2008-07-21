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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName());
    Pair<String, String>[] params = context.getActionURL().getParameters();
%>

<labkey:errors/>
<table cellpadding="0" class="normal">
    <tr><td class="ms-searchform">Name</td><td><%=h(def.getName())%></td>
    <tr><td class="ms-searchform">Description</td><td></td>
    <tr><td class="ms-searchform">Created By</td><td></td>
    <tr><td class="ms-searchform">Created</td><td></td>
    <tr><td class="ms-searchform">Last Updated</td><td></td>
</table>

<table cellpadding="0" class="normal">
    <tr><td>&nbsp;</td></tr>
    <tr>
        <td><%=PageFlowUtil.buttonLink("Update Snapshot", PageFlowUtil.urlProvider(QueryUrls.class).urlUpdateSnapshot(context.getContainer()).addParameters(params), "return confirm('Updating will replace all current data with a fresh snapshot');")%></td>
        <td><%=PageFlowUtil.buttonLink("Edit Query", bean.getSchema().urlFor(QueryAction.sourceQuery, def.getQueryDefinition()))%></td>
        <td><%=PageFlowUtil.buttonLink("View History", bean.getSchema().urlFor(QueryAction.sourceQuery, def.getQueryDefinition()))%></td>
    </tr>
</table>
