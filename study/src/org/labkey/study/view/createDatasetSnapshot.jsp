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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<StudyController.StudySnapshotForm> me = (JspView<StudyController.StudySnapshotForm>) HttpView.currentView();
    StudyController.StudySnapshotForm bean = me.getModelBean();

    SimpleFilter filter = new SimpleFilter(me.getViewContext().getActionURL(), QueryView.DATAREGIONNAME_DEFAULT);

    boolean isAutoUpdateable = QuerySnapshotService.get(bean.getSchemaName()) instanceof QuerySnapshotService.AutoUpdateable;

    Map<String, String> updateDelay = new LinkedHashMap<>();
    updateDelay.put("30", "30 seconds");
    updateDelay.put("60", "1 minute");
    updateDelay.put("300", "5 minutes");
    updateDelay.put("600", "10 minutes");
    updateDelay.put("1800", "30 minutes");
    updateDelay.put("3600", "1 hour");
    updateDelay.put("7200", "2 hours");

%>

<labkey:errors/>



<labkey:form action="" method="post" onsubmit="validateForm();">
    <table>
        <tr><th colspan="10" class="labkey-header">Snapshot Name</th></tr>
        <tr><td colspan="10" class="labkey-title-area-line"></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td>Snapshot&nbsp;Name:</td><td><input type="text" maxlength="200" size="50" name="<%= text(bean.isEdit() ? "" : "snapshotName") %>" <%= text(bean.isEdit() ? "readonly" : "") %> value="<%=h(StringUtils.trimToEmpty(bean.getSnapshotName()))%>"></td></tr>
        <% if (!filter.isEmpty()) { %>
            <tr><td>Filters:</td><td><%= h(filter.getFilterText()) %></td></tr>
        <% } %>
        <% if (getActionURL().getParameterMap().containsKey("query.viewName")) { %>
            <tr><td>Custom View:</td><td><%= h(getActionURL().getParameter("query.viewName") == null ? "<default>" : getActionURL().getParameter("query.viewName")) %></td></tr>
        <% } %>
        <tr><td>&nbsp;</td></tr>

        <tr><th colspan="10" class="labkey-header">Snapshot Refresh</th></tr>
        <tr><td colspan="10" class="labkey-title-area-line"></td></tr>
        <tr><td colspan="2"><i>Snapshots can be configured to be manually updated or to automatically update<br/>within an amount of time after the
            underlying data has changed.</i></td></tr>
        <tr><td>&nbsp;</td></tr>

        <tr><td>Manual&nbsp;Refresh</td><td><input<%=disabled(!isAutoUpdateable)%><%=checked(bean.getUpdateDelay() == 0)%> type="radio" name="updateType" value="manual" id="manualUpdate" onclick="onAutoUpdate();"></td></tr>
        <tr><td>Automatic&nbsp;Refresh</td><td><input<%=disabled(!isAutoUpdateable)%><%=checked(bean.getUpdateDelay() != 0)%> type="radio" name="updateType" onclick="onAutoUpdate();"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><select name="updateDelay" id="updateDelay" style="display:<%= text(bean.getUpdateDelay() == 0 ? "none" : "block") %>"><labkey:options value="<%=text(String.valueOf(bean.getUpdateDelay()))%>" map="<%=updateDelay%>"></labkey:options></select></td></tr>
        <tr><td colspan="10" class="labkey-title-area-line"></td></tr>
        <tr><td colspan="10">
                <%
            if (!bean.isEdit())
            {
                out.println(button("Edit Dataset Definition").submit(true).onClick("this.form.action.value='" + StudyController.StudySnapshotForm.EDIT_DATASET + "'"));
                out.print(text("&nbsp;"));
            }

            out.println(button(bean.isEdit() ? "Save" : "Create Snapshot").submit(true));
            out.print(text("&nbsp;"));

            out.println(button(bean.isEdit() ? "Done" : "Cancel").submit(true).onClick("this.form.action.value='" + StudyController.StudySnapshotForm.CANCEL + "'"));

        %>
    </table>
    <%  if (getActionURL().getParameter(DatasetDefinition.DATASETKEY) != null) { %>
    <input type="hidden" name="<%=h(DatasetDefinition.DATASETKEY)%>" value="<%=h(getActionURL().getParameter(DatasetDefinition.DATASETKEY))%>">
    <%  } %>
    <input type="hidden" name="action" value="<%=h(StudyController.StudySnapshotForm.CREATE_SNAPSHOT)%>" id="action">
    <input type="hidden" name="snapshotDatasetId" value="<%=bean.getSnapshotDatasetId()%>">
</labkey:form>

<script type="text/javascript">

    var manualUpdate = document.querySelector('#manualUpdate');
    var updateDelay = document.querySelector('#updateDelay');

    function onAutoUpdate()
    {
        updateDelay.style.display = manualUpdate.checked ? "none" : "";
    }

    function validateForm()
    {
        if (manualUpdate.checked)
            updateDelay.value = "0";
    }

</script>
