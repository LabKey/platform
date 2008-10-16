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
<%@ page import="org.apache.commons.lang.math.NumberUtils" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.DisplayColumn" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotForm" %>
<%@ page import="org.labkey.api.query.snapshot.QuerySnapshotService" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
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

    Map<String, String> updateDelay = new LinkedHashMap<String, String>();
    updateDelay.put("30", "30 seconds");
    updateDelay.put("60", "1 minute");
    updateDelay.put("300", "5 minutes");
    updateDelay.put("600", "10 minutes");
    updateDelay.put("1800", "30 minutes");
    updateDelay.put("3600", "1 hour");
    updateDelay.put("7200", "2 hours");

    int datasetId = NumberUtils.toInt(context.getActionURL().getParameter(DataSetDefinition.DATASETKEY), -1);
    Study study = StudyManager.getInstance().getStudy(context.getContainer());
    String additionalKey = null;
    boolean isKeyManaged = false;
    boolean isDemographicData = false;

    if (datasetId != -1 && study != null)
    {
        DataSetDefinition dsDef = study.getDataSet(datasetId);
        if (dsDef != null)
        {
            additionalKey = dsDef.getKeyPropertyName();
            isKeyManaged = dsDef.isKeyPropertyManaged();
            isDemographicData = dsDef.isDemographicData();
        }
    }

    Map<String, String> dataKeyMap = createSelection(study, QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean), false);
    Map<String, String> managedKeyMap = createSelection(study, QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean), true);
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

<% if (!bean.isEdit()) { %>
        <tr><th colspan="10" class="labkey-header">Additional Key&nbsp;<%=helpPopup("Additional Key",
                    "If dataset has more than one row per participant/visit, " +
                            "an additional key field must be provided. There " +
                            "can be at most one row in the dataset for each " +
                            "combination of participant, visit and key. " +
                            "<ul><li>None: No additional key</li>" +
                            "<li>Data Field: A user-managed key field</li>" +
                            "<li>Managed Field: A numeric field defined below will be managed " +
                            "by the server to make each new entry unique</li>" +
                            "</ul>", true)%><th></tr>
        <tr><td colspan="10" class="labkey-title-area-line"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>
        <tr><td colspan="2"><i>If the source query has more than one row per participant/visit, an additional key<br/>field must be provided.</i></td></tr>
        <tr><td>&nbsp;</td></tr>

        <tr><td>None:</td><td><input type="radio" id="keyTypeNone" name="additionalKeyType" value="none" <%=additionalKey==null ? "checked" : ""%> onclick="onKeyType();"></td></tr>
        <tr><td>Data Field:</td><td><input type="radio" id="keyTypeData" name="additionalKeyType" value="data" <%=(additionalKey!=null && !isKeyManaged) ? "checked" : ""%> onclick="onKeyType();">&nbsp;
            <select name="additionalKey" id="dataKeyList"><labkey:options value="<%=additionalKey%>" map="<%=dataKeyMap%>"/></select>
        </td></tr>
        <tr><td>Managed Field:</td><td><input type="radio" id="keyTypeManaged" name="additionalKeyType" value="managed" <%=isKeyManaged ? "checked" : ""%> onclick="onKeyType();">&nbsp;
            <select name="additionalKey" id="managedKeyList"><labkey:options value="<%=additionalKey%>" map="<%=managedKeyMap%>"/></select>
        </td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><th colspan="10" class="labkey-header">Demographic Data&nbsp;<%=helpPopup("Demographic Data", "Demographic data appears only once for each participant in the study.")%><th></tr>
        <tr><td colspan="10" class="labkey-title-area-line"><img height="1" width="1" src="<%=AppProps.getInstance().getContextPath() + "/_.gif"%>"></td></tr>
        <tr><td>Demographic Data:</td><td><input type="checkbox" name="demographicData" <%=isDemographicData ? "checked" : ""%></td></tr>
<% } %>

        <tr><td><%=PageFlowUtil.generateSubmitButton(bean.isEdit() ? "Update" : "Next")%></td></tr>
    </table>
    <%  for (DisplayColumn col : QuerySnapshotService.get(bean.getSchemaName()).getDisplayColumns(bean)) { %>
            <input type="hidden" name="snapshotColumns" value="<%=getColumnName(col)%>">
    <%  }
        if (context.getActionURL().getParameter(DataSetDefinition.DATASETKEY) != null) { %>
            <input type="hidden" name="<%=DataSetDefinition.DATASETKEY%>" value="<%=context.getActionURL().getParameter(DataSetDefinition.DATASETKEY)%>">
    <%  } %>
</form>

<script type="text/javascript">

    function onKeyType()
    {
        if (YAHOO.util.Dom.get('keyTypeNone').checked)
        {
            YAHOO.util.Dom.get('dataKeyList').disabled=true;
            YAHOO.util.Dom.get('managedKeyList').disabled=true;
        }
        else if (YAHOO.util.Dom.get('keyTypeData').checked)
        {
            YAHOO.util.Dom.get('dataKeyList').disabled=false;
            YAHOO.util.Dom.get('managedKeyList').disabled=true;
        }
        else if (YAHOO.util.Dom.get('keyTypeManaged').checked)
        {
            YAHOO.util.Dom.get('dataKeyList').disabled=true;
            YAHOO.util.Dom.get('managedKeyList').disabled=false;
        }
    }

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
    YAHOO.util.Event.addListener(window, "load", onKeyType);
</script>

<%!
    String getColumnName(DisplayColumn col)
    {
        ColumnInfo info = col.getColumnInfo();
        if (info != null)
            return info.getName();

        return col.getName();
    }

    Map<String, String> createSelection(Study study, List<DisplayColumn> columns, boolean numericOnly) throws IOException
    {
        Map<String, String> viewMap = new HashMap<String, String>();
        for (DisplayColumn col : columns)
        {
            if (DataSetDefinition.isDefaultFieldName(col.getName(), study))
                continue;
            if (numericOnly && !Number.class.isAssignableFrom(col.getValueClass()))
                continue;

            String name = getColumnName(col);
            viewMap.put(name, col.getCaption());
        }
        return viewMap;
    }
%>
