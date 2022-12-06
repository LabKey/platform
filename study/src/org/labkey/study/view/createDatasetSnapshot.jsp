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
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
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
    <table class="lk-fields-table">
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td class="labkey-form-label">Snapshot&nbsp;Name:</td>
            <% if (bean.isEdit()) { %>
                <td><%=h(StringUtils.trimToEmpty(bean.getSnapshotName()))%></td>
            <% } else { %>
                <td><input type="text" maxlength="200" size="50" name="snapshotName" value="<%=h(StringUtils.trimToEmpty(bean.getSnapshotName()))%>"></td>
            <% } %>
        </tr>
        <% if (!filter.isEmpty()) { %>
            <tr><td class="labkey-form-label">Filters:</td><td><%= h(filter.getFilterText()) %></td></tr>
        <% } %>
        <% if (getActionURL().getParameterMap().containsKey("query.viewName")) { %>
            <tr><td class="labkey-form-label">Custom View:</td><td><%= h(StringUtils.isEmpty(getActionURL().getParameter("query.viewName")) ? "<default>" : getActionURL().getParameter("query.viewName")) %></td></tr>
        <% } %>
        <tr>
            <td class="labkey-form-label">Snapshot Refresh</td>
            <td>
                <p>
                    <i>Snapshots can be configured to be manually updated or to automatically update<br/>within an amount of time after the
                        underlying data has changed.</i>
                </p>
                <table>
                    <tr>
                        <td style="padding-right: 10px">Manual&nbsp;Refresh</td>
                        <td>
                            <input<%=disabled(!isAutoUpdateable)%><%=checked(bean.getUpdateDelay() == 0)%> type="radio" name="updateType" value="manual" id="manualUpdate" onclick="onAutoUpdate();">
                        </td>
                    </tr>
                    <tr>
                        <td style="padding-right: 10px">Automatic&nbsp;Refresh</td><td>
                            <input<%=disabled(!isAutoUpdateable)%><%=checked(bean.getUpdateDelay() != 0)%> type="radio" name="updateType" onclick="onAutoUpdate();">
                            <select name="updateDelay" id="updateDelay" style="display:<%= HtmlString.unsafe(bean.getUpdateDelay() == 0 ? "none" : "block") %>">
                                <labkey:options value="<%=String.valueOf(bean.getUpdateDelay())%>" map="<%=updateDelay%>"></labkey:options>
                            </select>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br/>
        <%
            if (!bean.isEdit())
            {
                out.println(button("Edit Dataset Definition").submit(true).onClick("this.form.action.value='" + StudyController.StudySnapshotForm.EDIT_DATASET + "'"));
            }

            out.println(button(bean.isEdit() ? "Save" : "Create Snapshot").submit(true));

            out.println(button(bean.isEdit() ? "Done" : "Cancel").submit(true).onClick("this.form.action.value='" + StudyController.StudySnapshotForm.CANCEL + "'"));
        %>
    <%  if (getActionURL().getParameter(Dataset.DATASETKEY) != null) { %>
    <input type="hidden" name="<%=h(Dataset.DATASETKEY)%>" value="<%=h(getActionURL().getParameter(Dataset.DATASETKEY))%>">
    <%  } %>
    <input type="hidden" name="action" value="<%=h(StudyController.StudySnapshotForm.CREATE_SNAPSHOT)%>" id="action">
    <input type="hidden" name="snapshotDatasetId" value="<%=bean.getSnapshotDatasetId()%>">
</labkey:form>

<%--These line brakes are for th edit snapshot case where there can be a DataRegion below this section--%>
<br/>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">

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
