<%
/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.DisplayColumn" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<QuerySnapshotForm> me = (JspView<QuerySnapshotForm>) HttpView.currentView();
    QuerySnapshotForm bean = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    Map<String, String> columnMap = new HashMap<String, String>();
    for (String name : bean.getSnapshotColumns())
        columnMap.put(name, name);

    boolean isAutoUpdateable = QuerySnapshotService.get(bean.getSchemaName()) instanceof QuerySnapshotService.AutoUpdateable;
    boolean isEdit = QueryService.get().getSnapshotDef(context.getContainer(), bean.getSchemaName(), bean.getSnapshotName()) != null;

    Map<String, String> updateDelay = new LinkedHashMap<String, String>();
    updateDelay.put("30", "30 seconds");
    updateDelay.put("60", "1 minute");
    updateDelay.put("300", "5 minutes");
    updateDelay.put("600", "10 minutes");
    updateDelay.put("1800", "30 minutes");
    updateDelay.put("3600", "1 hour");
    updateDelay.put("7200", "2 hours");
%>

<labkey:errors/>

<script type="text/javascript">

    function onAutoUpdate()
    {
        var manualUpdate = Ext.DomQuery.selectNode('#manualUpdate');
        var updateDelay = Ext.DomQuery.selectNode('#updateDelay');

        if (manualUpdate.checked)
            updateDelay.style.display = "none";
        else
            updateDelay.style.display = "";
    }
</script>

<form action="" method="post">
    <table cellpadding="0" class="normal">
        <tr><td colspan="10" class="labkey-announcement-title"><span>Snapshot Name and Type</span></td></tr>
        <tr><td colspan="10" width="100%" class="labkey-title-area-line"></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td>Snapshot Name:</td><td><input type="text" name="snapshotName" <%=isEdit ? "readonly" : ""%> value="<%=StringUtils.trimToEmpty(bean.getSnapshotName())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Manual Refresh</td><td><input <%=isAutoUpdateable ? "" : "disabled"%> checked type="radio" name="manualRefresh" id="manualUpdate" onclick="onAutoUpdate();"></td></tr>
        <tr><td>Automatic Refresh</td><td><input disabled="<%=isAutoUpdateable ? "" : "disabled"%>" onclick="onAutoUpdate();" type="radio" name="automaticRefresh"></td>
            <td><select id="updateDelay" style="display:none"><labkey:options value="<%=bean.getUpdateDelay()%>" map="<%=updateDelay%>"></labkey:options></select></td>
        </tr>
        <tr><td colspan="10" class="labkey-announcement-title"><span>Snapshot Column Selection</span></td></tr>
        <tr><td colspan="10" width="100%" class="labkey-title-area-line"></td></tr>

        <tr><td></td><td><table class="normal">
            <tr><td></td><th>Name</th><th>Label</th><th>Type</th><th>Description</th></tr>
    <%
        for (DisplayColumn col : QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean, null))
        {
            ColumnInfo info = col.getColumnInfo();
    %>
            <tr>
                <td><input type="checkbox" name="snapshotColumns" <%=columnMap.containsKey(col.getName()) ? "checked" : ""%> value="<%=col.getName()%>"></td>
                <td><%=h(col.getName())%></td>
                <td><%=h(info.getLabel())%></td>
                <td><%=h(info.getFriendlyTypeName())%></td>
                <td><%=h(info.getDescription())%></td>
            </tr>
    <%
        }
    %>
        </table></td></tr>
        <tr><td><%=generateSubmitButton("Submit")%></td></tr>
    </table>
</form>
