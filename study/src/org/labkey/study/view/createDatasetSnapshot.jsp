<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.DisplayColumn" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotService" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
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

    Map<String, String> updateDelay = new LinkedHashMap<String, String>();
    updateDelay.put("30", "30 seconds");
    updateDelay.put("60", "1 minute");
    updateDelay.put("300", "5 minutes");
    updateDelay.put("600", "10 minutes");
    updateDelay.put("1800", "30 minutes");
    updateDelay.put("3600", "1 hour");
    updateDelay.put("7200", "2 hours");
%>

<script type="text/javascript">LABKEY.requiresYahoo("yahoo");</script>
<script type="text/javascript">LABKEY.requiresYahoo("event");</script>
<script type="text/javascript">LABKEY.requiresYahoo("dom");</script>

<labkey:errors/>

<form action="" method="post" onsubmit="validateForm();">
<table>
        <tr><th colspan="10" class="labkey-header">Snapshot Name</th></tr>
        <tr><td colspan="10" class="labkey-title-area-line"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>

        <tr><td>&nbsp;</td></tr>
        <tr><td>Snapshot&nbsp;Name:</td><td><input type="text" name="<%=bean.isEdit() ? "" : "snapshotName"%>" <%=bean.isEdit() ? "readonly" : ""%> value="<%=StringUtils.trimToEmpty(bean.getSnapshotName())%>"></td></tr>
        <tr><td>&nbsp;</td></tr>

        <tr><th colspan="10" class="labkey-header">Snapshot Refresh</th></tr>
        <tr><td colspan="10" class="labkey-title-area-line"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>
        <tr><td colspan="2"><i>Snapshots can be configured to be manually updated or to automatically update<br/>within an amount of time after the
            underlying data has changed.</i></td></tr>
        <tr><td>&nbsp;</td></tr>

        <tr><td>Manual&nbsp;Refresh</td><td><input <%=isAutoUpdateable ? "" : "disabled"%> <%=bean.getUpdateDelay() == 0 ? "checked" : ""%> type="radio" name="updateType" value="manual" id="manualUpdate" onclick="onAutoUpdate();"></td></tr>
        <tr><td>Automatic&nbsp;Refresh</td><td><input <%=isAutoUpdateable ? "" : "disabled"%> <%=bean.getUpdateDelay() != 0 ? "checked" : ""%> type="radio" name="updateType" onclick="onAutoUpdate();"></td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td></td><td><select name="updateDelay" id="updateDelay" style="display:none"><labkey:options value="<%=String.valueOf(bean.getUpdateDelay())%>" map="<%=updateDelay%>"></labkey:options></select></td></tr>

        <tr><td><%=PageFlowUtil.generateSubmitButton(bean.isEdit() ? "Update" : "Next")%></td></tr>

        <tr><td></td><td><table>
    <%  for (DisplayColumn col : QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean)) { %>
            <tr><td><input type="hidden" name="snapshotColumns" value="<%=getColumnName(col)%>"></td></tr>
    <%  }
        if (context.getActionURL().getParameter(DataSetDefinition.DATASETKEY) != null) { %>
            <tr><td><input type="hidden" name="<%=DataSetDefinition.DATASETKEY%>" value="<%=context.getActionURL().getParameter(DataSetDefinition.DATASETKEY)%>"></td></tr>
    <%  } %>
        </table></td></tr>

    </table>
</form>

<script type="text/javascript">

    function onAutoUpdate()
    {
        var manualUpdate = YAHOO.util.Dom.get('manualUpdate');
        var updateDelay = YAHOO.util.Dom.get('updateDelay');

        if (manualUpdate.checked)
            updateDelay.style.display = "none";
        else
            updateDelay.style.display = "";
    }

    function validateForm()
    {
        var manualUpdate = YAHOO.util.Dom.get('manualUpdate');
        var updateDelay = YAHOO.util.Dom.get('updateDelay');

        if (manualUpdate.checked)
            updateDelay.value = "0";
    }

    YAHOO.util.Event.addListener(window, "load", onAutoUpdate)

</script>

<%!
    String getColumnName(DisplayColumn col)
    {
        ColumnInfo info = col.getColumnInfo();
        if (info != null)
            return info.getName();

        return col.getName();
    }
%>
