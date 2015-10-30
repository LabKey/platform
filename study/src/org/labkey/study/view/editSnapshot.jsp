<%
/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotDefinition" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = getViewContext();

    QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), bean.getSchemaName(), bean.getSnapshotName());
    QueryDefinition queryDef = def != null ? def.getQueryDefinition(getUser()) : null;

    boolean showHistory = BooleanUtils.toBoolean(getActionURL().getParameter("showHistory"));
    String historyLabel = showHistory ? "Hide History" : "Show History";

    boolean showDataset = BooleanUtils.toBoolean(getActionURL().getParameter("showDataset"));
    String datasetLabel = showDataset ? "Hide Dataset Definition" : "Edit Dataset Definition";

    final Study study = StudyManager.getInstance().getStudy(getContainer());
    final Dataset dsDef = StudyManager.getInstance().getDatasetDefinitionByName(study, bean.getSnapshotName());
    ActionURL deleteSnapshotURL = new ActionURL(StudyController.DeleteDatasetAction.class, getContainer());
%>

<%  if (def != null) { %>
<table>
    <tr><td class="labkey-form-label">Name</td><td><%=h(def.getName())%></td>
    <tr><td class="labkey-form-label">Created By</td><td><%=h(def.getCreatedBy().getDisplayName(getUser()))%></td>
    <tr><td class="labkey-form-label">Modified By</td><td><%=h(def.getModifiedBy().getDisplayName(getUser()))%></td>
    <tr><td class="labkey-form-label">Created</td><td><%=formatDateTime((def.getCreated()))%></td>
    <tr><td class="labkey-form-label">Last Updated</td><td><%=formatDateTime(def.getLastUpdated())%></td>
    <tr><td class="labkey-form-label">Query Source</td><td><textarea rows="20" cols="65" readonly="true"><%=h(queryDef != null ? queryDef.getSql() : null)%></textarea></td>
</table>
<%  } %>

<labkey:form action="" method="post" onsubmit="return confirm('Updating will replace all existing data with a new set of data. Continue?');">
    <input type="hidden" name="updateSnapshot" value="true">
    <table>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td><%= button("Update Snapshot").submit(true) %></td>
<%      if (def != null && dsDef != null) { %>
            <td><%= button(historyLabel).href(context.cloneActionURL().replaceParameter("showHistory", String.valueOf(!showHistory))) %></td>
            <td><%= button(datasetLabel).href(context.cloneActionURL().replaceParameter("showDataset", String.valueOf(!showDataset))) %></td>
            <td><%= button("Delete Snapshot")
                    .href(deleteSnapshotURL.addParameter("id", dsDef.getDatasetId()))
                    .onClick("return confirm('Are you sure you want to delete this snapshot?  All related data will also be deleted.')") %></td>
<%      } %>
        </tr>
    </table>
</labkey:form>
