<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.query.QueryDefinition" %>
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

    final Study study = StudyManager.getInstance().getStudy(getContainer());
    final Dataset dsDef = StudyManager.getInstance().getDatasetDefinitionByName(study, bean.getSnapshotName());
%>

<%  if (def != null) { %>
<table class="lk-fields-table">
    <tr><td class="labkey-form-label">Name</td><td><%=h(def.getName())%></td>
    <tr><td class="labkey-form-label">Created By</td><td><%=h(def.getCreatedBy().getDisplayName(getUser()))%></td>
    <tr><td class="labkey-form-label">Modified By</td><td><%=h(def.getModifiedBy().getDisplayName(getUser()))%></td>
    <tr><td class="labkey-form-label">Created</td><td><%=formatDateTime((def.getCreated()))%></td>
    <tr><td class="labkey-form-label">Last Updated</td><td><%=formatDateTime(def.getLastUpdated())%></td>
    <tr><td class="labkey-form-label">Query Source</td><td><textarea rows="20" cols="65" readonly="true"><%=h(queryDef != null ? queryDef.getSql() : null)%></textarea></td>
</table>
<%  } %>
<br/>
<labkey:form action="" method="POST">
    <input type="hidden" name="updateSnapshot" value="true">
    <%= button("Update Snapshot").submit(true).onClick("this.form.action='';return confirm('Updating will replace all existing data with a new set of data. Continue?');")%>
    <% if (def != null && dsDef != null) {
        ActionURL deleteSnapshotURL = new ActionURL(StudyController.DeleteDatasetAction.class, getContainer()).addParameter("id", dsDef.getDatasetId());
        ActionURL editDatasetURL = new ActionURL(StudyController.EditTypeAction.class, getContainer()).addParameter("datasetId", dsDef.getDatasetId()).addReturnURL(getActionURL());
    %>
        <%= button("Delete Snapshot")
                .href(deleteSnapshotURL)
                .submit(true)
                .onClick("this.form.action='"+deleteSnapshotURL+"'; return confirm('Are you sure you want to delete this snapshot?  All related data will also be deleted.')")
        %>
        <%= button(historyLabel).href(context.cloneActionURL().replaceParameter("showHistory", String.valueOf(!showHistory))) %>
        <%= button("Edit Dataset Definition").href(editDatasetURL) %>
    <% } %>
</labkey:form>
