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
<%@ page import="org.apache.commons.beanutils.ConvertUtils" %>
<%@ page import="org.apache.commons.collections4.multimap.AbstractSetValuedMap" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashSet" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.QueryLogging" %>
<%@ page import="org.labkey.api.data.Results" %>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.exp.LsidManager" %>
<%@ page import="org.labkey.api.qc.DataState" %>
<%@ page import="org.labkey.api.qc.QCStateManager" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.visualization.GenericChartReport" %>
<%@ page import="org.labkey.api.visualization.TimeChartReport" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.ExpandStateNotifyAction" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.query.StudyQuerySchema" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.TreeSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("vis/vis");
        dependencies.add("vis/genericChart/genericChartHelper.js");
        dependencies.add("vis/timeChart/timeChartHelper.js");
    }
%>

<%
    ViewContext context = getViewContext();
    StudyQuerySchema querySchema = (StudyQuerySchema) QueryService.get().getUserSchema(getUser(), getContainer(), "study");
    DbSchema dbSchema = querySchema.getDbSchema();
    JspView<StudyManager.ParticipantViewConfig> me = (JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    final StudyManager.ParticipantViewConfig bean = me.getModelBean();
    Map<String, String> aliasMap = bean.getAliases();

    StudyManager manager = StudyManager.getInstance();
    StudyImpl study = manager.getStudy(getContainer());

    User user = (User) request.getUserPrincipal();
    List<DatasetDefinition> allDatasets = manager.getDatasetDefinitions(study);

    ArrayList<Pair<DatasetDefinition,TableInfo>> datasets = new ArrayList<>(allDatasets.size());

    for (DatasetDefinition def : allDatasets)
    {
        if (!def.isShowByDefault() || null == def.getStorageTableInfo() || def.isDemographicData())
            continue;
        TableInfo t = querySchema.getDatasetTableForLookup(def, null);
        if (null==t || !t.hasPermission(user, ReadPermission.class))
            continue;
        datasets.add(new Pair<>(def,t));
    }

    //
    // Create a resultset per dataset
    // A multiple SELECT query would make sense here, but it doesn't seem to work with our Postgres driver.
    //
    // First select from the StudyData UNION table to see which datasets we need to query individually
    // populate a VisitMultiMap while we're at it
    //

    final Map<Pair<String, Double>, Integer> visitRowIdMap = new HashMap<>();
    final Map<Double, Date> ptidVisitDates = new TreeMap<>();

    new SqlSelector(dbSchema,
            "SELECT VisitRowId, ParticipantId, SequenceNum, VisitDate\n" +
                    "FROM " + StudySchema.getInstance().getTableInfoParticipantVisit() + "\n" +
                    "WHERE Container = ? AND ParticipantId = ?",
            study.getContainer(), bean.getParticipantId()).forEach(rs -> {
                int visitRowId = rs.getInt(1);
                String ptid = rs.getString(2);
                double sequenceNum = rs.getDouble(3);
                Date visitDate = rs.getDate(4);
                visitRowIdMap.put(new Pair<>(ptid, sequenceNum), visitRowId);
                if (bean.getParticipantId().equals(ptid))
                    ptidVisitDates.put(sequenceNum, visitDate);
            });

    final VisitMultiMap visitSequenceMap = new VisitMultiMap();
    final Map<Double, Integer> countKeysForSequence = new HashMap<>();
    final Set<Integer> datasetSet = new HashSet<>();

    if (!datasets.isEmpty())
    {
        SQLFragment f = new SQLFragment();
        String union = "";
        for (var pair : datasets)
        {
            DatasetDefinition dd = pair.getKey();
            TableInfo t = pair.getValue();
            String alias = "__x" + dd.getDatasetId() + "__";
            ColumnInfo ptid = t.getColumn(study.getSubjectColumnName());
            ColumnInfo seq = t.getColumn("SequenceNum");
            f.append(union).append("SELECT ")
                    .append(ptid.getValueSql(alias)).append(" AS ParticipantId,")
                    .append(seq.getValueSql(alias)).append(" AS SequenceNum,")
                    .append(dd.getDatasetId()).append(" as DatasetId,")
                    .append("COUNT(*) AS _RowCount");
            f.append("\nFROM ").append(t.getFromSQL(alias));
            f.append("\nWHERE ").append(ptid.getValueSql(alias)).append("=?").add(bean.getParticipantId());
            f.append("\nGROUP BY ").append(ptid.getValueSql(alias)).append(",").append(seq.getValueSql(alias));
            union = "\n  UNION ALL\n";
        }
        f.append("\n ORDER BY 2");

        new SqlSelector(dbSchema.getScope(), f, QueryLogging.noValidationNeededQueryLogging()).forEach(rs -> {
            String ptid = rs.getString(1);
            double s = rs.getDouble(2);
            Double sequenceNum = rs.wasNull() ? null : s;
            int datasetId = rs.getInt(3);
            int rowCount = ((Number) rs.getObject(4)).intValue();
            Integer visitRowId = visitRowIdMap.get(new Pair<>(ptid, sequenceNum));
            if (null != visitRowId && null != sequenceNum)
                visitSequenceMap.put(visitRowId, sequenceNum);
            datasetSet.add(datasetId);
            Integer count = countKeysForSequence.get(sequenceNum);
            if (null == count || count < rowCount)
                countKeysForSequence.put(sequenceNum, rowCount);
        });
    }

    // Now we have a list of datasets with 1 or more rows and a visitMap to help with layout
    // get the data

    List<VisitImpl> allVisits = manager.getVisits(study, Visit.Order.DISPLAY);
    ArrayList<VisitImpl> visits = new ArrayList<>(visitSequenceMap.size());
    for (VisitImpl visit : allVisits)
    {
        if (visitSequenceMap.containsKey(visit.getRowId()))
            visits.add(visit);
    }

    Map<Integer, String> expandedMap = StudyController.getExpandedState(context, bean.getDatasetId());
    boolean updateAccess = study.getContainer().hasPermission(user, UpdatePermission.class);

    int totalSeqKeyCount = 0;
%>

<%
    if (null != aliasMap && !aliasMap.isEmpty())
    {
%>
<h3>Aliases:</h3>
<%
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : aliasMap.entrySet())
    {
        builder.append(entry.getKey() + ": " + entry.getValue() + ", ");
    }
    String aliasString = builder.substring(0, builder.toString().length() - 2);
%>
<p><%=h(aliasString)%>
</p>
<%
    }
%>

<style type="text/css">
    .labkey-participant-view-header {
        white-space: nowrap;
        padding: 2px;
    }

    .labkey-participant-view-row td {
        padding: 2px;
        border: solid lightgray 1px;
        border-top-width: 0;
    }

    .labkey-ptid-chart {
        margin-top: 10px;
        width: 900px;
        background-color: white;
        border: solid 1px #808080;
    }

    .labkey-ptid-remove, .labkey-ptid-add {
        cursor: pointer;
    }
</style>

<script type="text/javascript">
(function($) {
    var tableReady = false;
    LABKEY.Utils.onReady(function ()
    {
        tableReady = true;
        showChartsForExpandedSection();
    });

    LABKEY.ParticipantViewUnhideSelect = function (button) {
        var el = getCallingElement(button);
        if (el) {
            el.show();
            $(button).hide();
        }
    };

    LABKEY.ParticipantViewShowSelectedChart = function(button) {
        var el = getCallingElement(button);
        if (el && el.val()) {
            updateReportParticipantViewProperty(el.val(), true);
        }
    };

    LABKEY.ParticipantViewRemoveChart = function (reportId) {
        if (reportId) {
            updateReportParticipantViewProperty(reportId, false);
        }
    };

    LABKEY.ParticipantViewToggleIfReady = function(link, notify, datasetId) {
        if (tableReady) {
            LABKEY.Utils.toggleLink(link, notify);
            showChartsForExpandedSection(datasetId);
        }

        return false;
    };

    function showChartsForExpandedSection(datasetId) {
        // get all of the to-be-rendered chart divs (on original page load it is all, after that it is for a given dataset)
        var chartDivSelection = datasetId != undefined ? $('.labkey-ptid-chart-dataset' + datasetId) : $('.labkey-ptid-chart');

        // make the call to render the chart for that div if it is visible and has not already been rendered
        chartDivSelection.each(function(index, div) {
            var divId = div.getAttribute('id');
            var reportId = div.getAttribute('report-id');
            if (divId && reportId) {
                var divEl = $('#' + divId);
                if (divEl && divEl.is(':visible') && divEl.find('svg').length === 0) {
                    renderChartWebpart(divId, reportId);
                }
            }
        });
    }

    function updateReportParticipantViewProperty(reportId, value) {
        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('reports', 'updateReportParticipantViewProperty.api'),
            jsonData: {showInParticipantView: value ? 'true' : 'false', reportId: reportId},
            success: function (response) {
                // reshow the page
                window.location.reload(true);
            },
            // Show generic error message
            failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
        });
    }

    function getCallingElement(button) {
        var datasetId = button.getAttribute("dataset-id");
        if (datasetId) {
            return $("#addChartSelect-" + datasetId);
        }

        return null;
    }

    function renderChartWebpart(divId, reportId) {
        LABKEY.Query.Visualization.get({
            reportId: reportId,
            success: function(response) {
                var visConfig = response.visualizationConfig;

                // determine which chart helper to use based on visConfig info
                if (visConfig.queryConfig && visConfig.chartConfig) {
                    // add ptid filter to the chart queryConfig filterArray
                    var ptidColName = LABKEY.getModuleContext('study') ? LABKEY.getModuleContext('study').subject.columnName : 'ParticipantId';
                    if (!visConfig.queryConfig.filterArray)
                        visConfig.queryConfig.filterArray = [];
                    visConfig.queryConfig.filterArray.push({name: ptidColName, type: 'eq', value: <%=q(bean.getParticipantId())%>});

                    // explicitly set the chart height and width
                    visConfig.chartConfig.width = 900;
                    visConfig.chartConfig.height = 505;

                    LABKEY.vis.GenericChartHelper.renderChartSVG(divId, visConfig.queryConfig, visConfig.chartConfig);
                }
                else {
                    var studyContext = LABKEY.getModuleProperty('study', 'subject');
                    var queryConfig = {
                        containerPath: LABKEY.container.path,
                        nounSingular: studyContext.nounSingular || "Participant",
                        subjectColumnName: studyContext.columnName || "ParticipantId"
                    };

                    // explicitly set the subject values array to the single ptid from this context
                    if (visConfig.subject) {
                        visConfig.subject.values = [<%=q(bean.getParticipantId())%>];
                    }

                    // explicitly set the chart height and width
                    visConfig.width = 900;
                    visConfig.height = 505;

                    LABKEY.vis.TimeChartHelper.renderChartSVG(divId, queryConfig, visConfig);
                }
            }
        });
    }
})(jQuery);
</script>

<table class="labkey-data-region">
<tr class="labkey-alternate-row">
    <td class="labkey-participant-view-header"><img alt="" width=180 height=1 src="<%=getWebappURL("_.gif")%>"></td>
    <td class="labkey-participant-view-header"><img alt="" width=20 height=1 src="<%=getWebappURL("_.gif")%>"></td>
    <%

        for (VisitImpl visit : visits)
        {
            int seqKeyCount = 0;
            for (Double seqNum : visitSequenceMap.get(visit.getRowId()))
            {
                Integer c = countKeysForSequence.get(seqNum);
                seqKeyCount += c == null ? 1 : c;
            }
            totalSeqKeyCount += seqKeyCount;
    %>
    <td class="labkey-participant-view-header" colspan="<%=seqKeyCount%>">
        <%= h(visit.getDisplayString()) %>
        <%= visit.getDescription() != null ? helpPopup("Visit Description", visit.getDescription()) : HtmlString.EMPTY_STRING %>
    </td>
    <%
        }
    %>
</tr>

<tr class="labkey-alternate-row">
    <td class="labkey-participant-view-header"><img alt="" width=1 height=1 src="<%=getWebappURL("_.gif")%>"></td>
    <td class="labkey-participant-view-header"><img alt="" width=1 height=1 src="<%=getWebappURL("_.gif")%>"></td>
    <%

        for (VisitImpl visit : visits)
        {
            Collection<Double> sequences = visitSequenceMap.get(visit.getRowId());
            for (Double seqNum : sequences)
            {
                Date date = ptidVisitDates.get(seqNum);
                Integer keyCount = countKeysForSequence.get(seqNum);
                if (null == keyCount)
                    keyCount = 1;
    %>
    <td class="labkey-participant-view-header" colspan="<%=keyCount%>">
        <%=formatDate(date)%>
        <%=(study.getTimepointType().isVisitBased() && date != null ? HtmlString.unsafe("<br/>Visit: ") : HtmlString.EMPTY_STRING)%>
        <%=(study.getTimepointType().isVisitBased() ? h(seqNum) : HtmlString.EMPTY_STRING)%>
    </td>
    <%
            }
        }
    %>
</tr>

<%
    for (var pair : datasets)
    {
        var dataset = pair.getKey();
        var table = pair.getValue();

        // Do not display demographic data here. That goes in a separate web part,
        // the participant characteristics
        if (dataset.isDemographicData())
            continue;

        String typeURI = dataset.getTypeURI();
        if (null == typeURI)
            continue;

        int datasetId = dataset.getDatasetId();
        boolean expanded = false;
        if ("expand".equalsIgnoreCase(expandedMap.get(datasetId)))
            expanded = true;

        assert table.hasPermission(user,ReadPermission.class);
        if (!table.hasPermission(user,ReadPermission.class))
        {
%>
<tr class="labkey-header">
    <th nowrap align="left" class="labkey-expandable-row-header"><%=h(dataset.getDisplayString())%>
    </th>
    <td colspan="<%=totalSeqKeyCount+1%>" nowrap align="left" class="labkey-expandable-row-header">(no access)</td>
</tr>
<%
        continue;
        }

    if (!datasetSet.contains(datasetId))
        continue;

    // get the data for this dataset and group rows by SequenceNum/Key
    Map<Double, Map<Object, Map<String, Object>>> seqKeyRowMap = new HashMap<>();
    FieldKey keyColumnName = null == dataset.getKeyPropertyName() ? null : new FieldKey(null, dataset.getKeyPropertyName());
    ColumnInfo keyColumn = null == keyColumnName ? null : table.getColumn(keyColumnName);
    ColumnInfo seqnumColumn = table.getColumn("SequenceNum");

    Map<FieldKey, ColumnInfo> allColumns = getQueryColumns(table);
    ColumnInfo sourceLsidColumn = allColumns.get(new FieldKey(null, "sourceLsid"));

    final int rowCount;
    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(study.getSubjectColumnName()), bean.getParticipantId());
    Sort sort = new Sort("SequenceNum");
    try (Results dsResults = new TableSelector(table, allColumns.values(), filter, sort).getResults())
    {
        rowCount = dsResults.getSize();
        while (dsResults.next())
        {
            Object sequenceNum = seqnumColumn.getValue(dsResults);
            Object key = null == keyColumn ? "" : keyColumn.getValue(dsResults);

            Map<Object, Map<String, Object>> keyMap = seqKeyRowMap.get(sequenceNum);
            if (null == keyMap)
                seqKeyRowMap.put(((Number) sequenceNum).doubleValue(), keyMap = new HashMap<>());
            keyMap.put(key, dsResults.getRowMap());
        }
    }
    if (rowCount == 0)
        continue;
%>
<tr class="labkey-header">
    <th nowrap align="left" class="labkey-expandable-row-header">
        <a title="Click to expand/collapse"
            href="<%=h(new ActionURL(ExpandStateNotifyAction.class, study.getContainer()).addParameter("datasetId", Integer.toString(datasetId)).addParameter("id", Integer.toString(bean.getDatasetId())))%>"
            onclick="return LABKEY.ParticipantViewToggleIfReady(this, true, <%=datasetId%>);">
            <img src="<%=getWebappURL("_images/" + (expanded ? "minus.gif" : "plus.gif"))%>" alt="Click to expand/collapse">
            <%=h(dataset.getDisplayString())%>
        </a><%
        if (null != StringUtils.trimToNull(dataset.getDescription()))
        {
    %><%=helpPopup(dataset.getDisplayString(), dataset.getDescription())%><%
        }
    %></th>
    <td class="labkey-expandable-row-header" style="text-align:right;"><%=rowCount%></td>
    <%
        for (VisitImpl visit : visits)
        {
            for (double seq : visitSequenceMap.get(visit.getRowId()))
            {
                Map<Object, Map<String, Object>> keyMap = seqKeyRowMap.get(seq);
                Integer count = countKeysForSequence.get(seq);
                int colspan = count != null ? count : 1;
                if (null != keyMap)
                {
    %>
                <td class="labkey-expandable-row-header" colspan="<%=colspan%>"><%=keyMap.size()%></td>
    <%
                }
                else
                {
    %>
                <td class="labkey-expandable-row-header" colspan="<%=colspan%>">&nbsp;</td>
    <%
                }
            }
        }
    %>
</tr>
<%

    int row = 0;
    HtmlString className;

    // display details link(s) only if we have a source lsid in at least one of the rows
    boolean hasSourceLsid = false;

    if (QCStateManager.getInstance().showStates(getContainer()))
    {
        row++;
        className = getShadeRowClass(row);
%>
<tr class="<%=className%> labkey-participant-view-row" style="<%=text(expanded ? "" : "display:none")%>">
    <td align="left" nowrap>QC State</td>
    <td>&nbsp;</td>
    <%
        for (VisitImpl visit : visits)
        {
            for (double seq : visitSequenceMap.get(visit.getRowId()))
            {
                Map<Object, Map<String, Object>> keyMap = seqKeyRowMap.get(seq);
                int countTD = 0;
                if (null != keyMap)
                {
                    for (Map.Entry<Object, Map<String, Object>> e : keyMap.entrySet())
                    {
                        Integer id = (Integer) e.getValue().get("QCState");
                        DataState state = getQCState(study, id);
                        boolean hasDescription = state != null && state.getDescription() != null && state.getDescription().length() > 0;
    %>
    <td>
        <%= h(state == null ? "Unspecified" : state.getLabel())%><%= hasDescription ? helpPopup("QC State: " + state.getLabel(), state.getDescription()) : HtmlString.EMPTY_STRING %>
    </td>
    <%
                countTD++;
            }
        }
        // do we need to pad?
        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
        if (countTD < maxTD)
        {
    %>
    <td colspan="<%=maxTD-countTD%>">&nbsp;</td>
    <%
                }
            }
        }
    %>
</tr>
<%
    }

    // sort the properties so they appear in the same order as the grid view
    // PropertyDescriptor[] pds = sortProperties(StudyController.getParticipantPropsFromCache(context, typeURI), dataset, context);
    List<ColumnInfo> displayColumns = sortColumns(allColumns.values(), dataset, context);

    for (ColumnInfo col : displayColumns)
    {
        if (col == null)
            continue;
        
        row++;
        className = getShadeRowClass(row);
        String labelName = StringUtils.defaultString(col.getLabel(), col.getName());
%>
<tr class="<%=className%> labkey-participant-view-row" style="<%=text(expanded ? "" : "display:none")%>">
    <td align="left" nowrap><%=h(labelName)%></td>
    <td>&nbsp;</td>
    <%
        for (VisitImpl visit : visits)
        {
            for (double seq : visitSequenceMap.get(visit.getRowId()))
            {
                Map<Object, Map<String, Object>> keyMap = seqKeyRowMap.get(seq);
                int countTD = 0;
                if (null != keyMap)
                {
                    for (Map.Entry<Object, Map<String, Object>> e : keyMap.entrySet())
                    {
                        Map<String, Object> propMap = e.getValue();
                        if (sourceLsidColumn.getValue(propMap) != null)
                            hasSourceLsid = true;
                        Object value = col.getValue(propMap);
    %>
    <td><%=format(value)%>
    </td>
    <%
                countTD++;
            }
        }
        // do we need to pad?
        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
        if (countTD < maxTD)
        {
    %>
    <td colspan="<%=maxTD-countTD%>">&nbsp;</td>
    <%
                }
            }
        }
    %></tr>
<%
    }
    if (hasSourceLsid) // Need to display a details link
    {
        row++;
        className = getShadeRowClass(row);
%>
<tr class="<%=className%>" style="<%=text(expanded ? "" : "display:none")%>">
    <td align="left" nowrap>Details</td>
    <td>&nbsp;</td>
    <%
        for (VisitImpl visit : visits)
        {
            for (double seq : visitSequenceMap.get(visit.getRowId()))
            {
                Map<Object, Map<String, Object>> keyMap = seqKeyRowMap.get(seq);
                int countTD = 0;
                if (null != keyMap)
                {
                    for (Map.Entry<Object, Map<String, Object>> e : keyMap.entrySet())
                    {
                        String link = "&nbsp;";
                        Map<String, Object> propMap = e.getValue();
                        String sourceLsid = (String) sourceLsidColumn.getValue(propMap);

                        if (sourceLsid != null && LsidManager.get().hasPermission(sourceLsid, getUser(), ReadPermission.class))
                        {
                            ActionURL sourceURL = new ActionURL(StudyController.DatasetItemDetailsAction.class, getContainer());
                            sourceURL.addParameter("sourceLsid", sourceLsid);
                            link = "[<a href=\"" + sourceURL.getLocalURIString() + "\">details</a>]";
                        }
    %>
    <td><%= text(link)%>
    </td>
    <%
                countTD++;
            }
        }
        // do we need to pad?
        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
        if (countTD < maxTD)
        {
    %>
    <td colspan="<%=maxTD-countTD%>">&nbsp;</td>
    <%
                }
            }
        }
    %></tr>
<%
        }

    String datasetName = dataset.getName();
    String reportKey = ReportUtil.getReportKey("study", datasetName);

    // Check for reports to render by both the reportKey and the datasetId
    Collection<Report> datasetReports = ReportService.get().getReports(getUser(), getContainer(), reportKey);
    datasetReports.addAll(ReportService.get().getReports(user, study.getContainer(), Integer.toString(datasetId)));

    Map<String, String> reportIdWithNames = new HashMap<>(); // reports available to be added to the display
    Map<String, String> reportsToRender = new HashMap<>(); // reports already selected to be rendered in the page

    for (Report report : datasetReports)
    {
        ReportDescriptor reportDescriptor = report.getDescriptor();

        // for now we only want to include generic charts and time charts
        if (!(report instanceof GenericChartReport || report instanceof TimeChartReport))
            continue;

        // only include shared reports for a given dataset
        if (!reportDescriptor.isShared())
            continue;

        String reportId = reportDescriptor.getProperty("reportId");
        String name = reportDescriptor.getProperty("reportName");
        boolean showInParticipantView = "true".equalsIgnoreCase(reportDescriptor.getProperty("showInParticipantView"));
        if (showInParticipantView)
            reportsToRender.put(reportId, name);
        else
            reportIdWithNames.put(reportId, name);
    }
%>
        <tr style="<%=text(expanded ? "" : "display:none")%>">
            <td colspan="<%=totalSeqKeyCount+1%>">
<%
                    for (String reportId: reportsToRender.keySet())
                    {
                        String divId1 = "labkey-ptid-chart-" + getRequestScopedUID();
%>
                        <div id="<%=h(divId1)%>" class="labkey-ptid-chart labkey-ptid-chart-dataset<%=datasetId%>" report-id="<%=h(reportId)%>"></div>
<%
                        if (updateAccess) {
%>
                            <a class="labkey-text-link labkey-ptid-remove" onclick="LABKEY.ParticipantViewRemoveChart('<%=h(reportId)%>')">Remove Chart</a>
<%
                        }
                    }
%>
            </td>
        </tr>
<%

        // This add chart text link will show a dropdown of saved charts for this dataset. User will be able to select
        // saved chart and click on submit to render the chart inline into the participant view.
        if (updateAccess && reportIdWithNames.size() > 0)
        {
%>
            <tr style="<%=text(expanded ? "" : "display:none")%>">
                <td colspan="<%=totalSeqKeyCount+1%>">
                    <a class="labkey-text-link labkey-ptid-add" onclick="LABKEY.ParticipantViewUnhideSelect(this)" dataset-id="<%=datasetId%>">Add Chart</a>

                    <select id="addChartSelect-<%=datasetId%>" style="display: none" onchange="document.getElementById('addButton-<%=datasetId%>').style.display = 'inline-block';">
                        <option>Select a chart...</option>
                        <% for (Map.Entry<String, String> reportEntry : reportIdWithNames.entrySet()) { %>
                        <option value="<%=h(reportEntry.getKey())%>"><%=h(reportEntry.getValue())%></option>
                        <% } %>
                    </select>
                    <button id="addButton-<%=datasetId%>" onclick="LABKEY.ParticipantViewShowSelectedChart(this)" dataset-id="<%=datasetId%>" style="display: none">Submit</button>
                </td>
            </tr>
    <%
        }
    }
%>
</table>
<%!

    Map<Integer, DataState> qcstates = null;

    DataState getQCState(Study study, Integer id)
    {
        if (null == qcstates)
        {
            List<DataState> states = QCStateManager.getInstance().getStates(study.getContainer());
            qcstates = new HashMap<>(2 * states.size());
            for (DataState state : states)
                qcstates.put(state.getRowId(), state);
        }
        return qcstates.get(id);
    }


    Map<FieldKey, ColumnInfo> getQueryColumns(TableInfo t)
    {
        List<ColumnInfo> cols = t.getColumns();
        // Use all of the columns in the default view of the dataset, which might include columns from the assay side if
        // the data was linked
        Set<FieldKey> keys = new LinkedHashSet<>(t.getDefaultVisibleColumns());
        for (ColumnInfo c : cols)
            keys.add(c.getFieldKey());
        return QueryService.get().getColumns(t, keys);
    }

    final CaseInsensitiveHashSet skipColumns = new CaseInsensitiveHashSet();

    boolean skipColumn(ColumnInfo col)
    {
        if (skipColumns.isEmpty())
        {
            skipColumns.addAll( "lsid", "sourcelsid", "sequencenum", "qcstate", "participantid",
                    "visitrowid", "dataset", "participantsequencenum", "created", "modified", "createdby", "modifiedby", "participantvisit",
                    "container", "_key", "datasets", "folder");

            final String subjectColumnName = StudyService.get().getSubjectColumnName(getContainer());
            final String subjectVisitColumnName = StudyService.get().getSubjectVisitColumnName(getContainer());

            skipColumns.add(subjectColumnName);
            skipColumns.add(subjectVisitColumnName);
            skipColumns.add(subjectVisitColumnName + "/visit");
        }

        return (skipColumns.contains(col.getName())) || col.isMvIndicatorColumn();
    }

    List<ColumnInfo> sortColumns(Collection<ColumnInfo> cols, Dataset dsd, ViewContext context)
    {
        final Map<String, Integer> sortMap = StudyController.getSortedColumnList(context, dsd);
        if (!sortMap.isEmpty())
        {
            ArrayList<ColumnInfo> list = new ArrayList<>(sortMap.size());
            for (ColumnInfo col : cols)
            {
                if (!skipColumn(col) && sortMap.containsKey(col.getName()))
                {
                    int index = sortMap.get(col.getName());
                    while (list.size() <= index)
                        list.add(null);
                    list.set(index, col);
                }
            }
            List<ColumnInfo> results = new ArrayList<>();
            for (ColumnInfo col : list)
                if (col != null)
                    results.add(col);
            return results;
        }

        // default list
        List<ColumnInfo> ret = new ArrayList<>(cols.size());
        for (ColumnInfo col : cols)
        {
            if (!skipColumn(col))
                ret.add(col);
        }
        return ret;
    }


    public static class VisitMultiMap extends AbstractSetValuedMap<Integer, Double>
    {
        public VisitMultiMap()
        {
            super(new TreeMap<>());
        }

        @Override
        protected Set<Double> createCollection()
        {
            return new TreeSet<>();
        }
    }


    HtmlString format(Object value)
    {
        if (value instanceof Date)
            return formatDate((Date)value);

        if (value instanceof Number)
            return HtmlString.of(Formats.formatNumber(getContainer(), (Number) value));

        return null == value ? HtmlString.NBSP : h(ConvertUtils.convert(value), true);
    }
%>

