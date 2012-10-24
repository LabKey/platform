<%
/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.BooleanUtils" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotDefinition" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
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
        <td><%=generateButton("Update Snapshot", urlProvider(QueryUrls.class).urlUpdateSnapshot(context.getContainer()).addParameters(params), "return confirm('Updating will replace all current data with a fresh snapshot');")%></td>
<%  if (def != null) { %>
        <td><%=generateButton("Source Query", bean.getSchema().urlFor(QueryAction.sourceQuery, def.getQueryDefinition(context.getUser())))%></td>
        <td><%=generateButton(historyLabel, context.cloneActionURL().replaceParameter("showHistory", String.valueOf(!showHistory)))%></td>
<%  } %>
    </tr>
</table>
