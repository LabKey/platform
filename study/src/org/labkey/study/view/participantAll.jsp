<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.apache.commons.beanutils.ConvertUtils"%>
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.SimpleFilter"%>
<%@ page import="org.labkey.api.data.Table"%>
<%@ page import="org.labkey.api.data.TableInfo"%>
<%@ page import="org.labkey.api.exp.LsidManager"%>
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.query.QueryService"%>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ChartDesignerBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.labkey.study.reports.StudyChartQueryReport" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentContext();
    UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
    String contextPath = request.getContextPath();
    JspView<StudyManager.ParticipantViewConfig> me = (JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    StudyManager.ParticipantViewConfig bean = me.getModelBean();

    ChartDesignerBean chartBean = new ChartDesignerBean();

    chartBean.setReportType(StudyChartQueryReport.TYPE);
    chartBean.setSchemaName(schema.getSchemaName());
    String currentUrl = bean.getRedirectUrl();
    if (currentUrl == null)
        currentUrl = context.getActionURL().getLocalURIString();

    ActionURL url = ReportUtil.getChartDesignerURL(context, chartBean);
    url.setAction(ReportsController.DesignChartAction.class);
    url.addParameter("returnUrl", currentUrl);
    url.addParameter("isParticipantChart", "true");
    url.addParameter("participantId", bean.getParticipantId());
    
    StudyManager manager = StudyManager.getInstance();
    Study study = manager.getStudy(context.getContainer());
    AllParticipantData all = manager.getAllParticipantData(study, bean.getParticipantId(), bean.getQCStateSet());

    VisitImpl[] allVisits = manager.getVisits(study, Visit.Order.DISPLAY);
    ArrayList<VisitImpl> visits = new ArrayList<VisitImpl>(all.getVisitSequenceMap().size());
    for (VisitImpl visit : allVisits)
    {
        if (all.getVisitSequenceMap().containsKey(visit.getRowId()))
            visits.add(visit);
    }

    // UNDONE Move this code into StudyManager.getAllParticipantData()
    Map<Double, Date> visitDates = new TreeMap<Double, Date>();
    TableInfo tinfoParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
    SimpleFilter f = new SimpleFilter();
    f.addCondition("Container", study.getContainer());
    f.addCondition("ParticipantId", bean.getParticipantId());
    ResultSet rsVisitDates = Table.select(tinfoParticipantVisit, PageFlowUtil.set("SequenceNum", "VisitDate", "ParticipantId", "Container"), f, null);
    int s = rsVisitDates.findColumn("SequenceNum");
    int v = rsVisitDates.findColumn("VisitDate");
    while (rsVisitDates.next())
    {
        Double seq = rsVisitDates.getDouble(s);
        Date date = rsVisitDates.getTimestamp(v);
        visitDates.put(seq, date);
    }
    rsVisitDates.close();

    // The columns are arranged in a hierarchy
    // Visit
    //   SequenceNum
    //      AssayKey (often just one column key=="")
    //
    // in order to lay this out correctly we need to count the sequences per visit, and the max(#keys) per sequence
    // ahead of time, so that's what wer're doing here
    //
    // visits already has list of all visits
    // all.getVisitSequenceMap().get(visit.getRowId())) gives us sequences for a visit
    // still need max(#keys for sequence)
    Map<Double, Integer> countKeysForSequence = new HashMap<Double, Integer>();
    for (Map.Entry<ParticipantDataMapKey, AllParticipantData.RowSet> entry : all.getValueMap().entrySet())
    {
        ParticipantDataMapKey key = entry.getKey();
        AllParticipantData.RowSet keyMap = entry.getValue();
        Integer count = countKeysForSequence.get(key.sequenceNum);
        if (count == null || keyMap.getKeyFieldCount() > count.intValue())
            countKeysForSequence.put(key.sequenceNum, keyMap.getKeyFieldCount());
    }

    User user = (User) request.getUserPrincipal();
    DataSetDefinition[] datasets = manager.getDataSetDefinitions(study);
    Map<Integer, String> expandedMap = StudyController.getExpandedState(context, bean.getDatasetId());
    boolean updateAccess = study.getContainer().hasPermission(user, UpdatePermission.class);

    int totalSeqKeyCount = 0;
%>

<table class="labkey-data-region">

    <tr class="labkey-alternate-row">
        <td class="labkey-participant-view-header"><img alt="" width=180 height=1 src="<%=contextPath%>/_.gif"></td><%

            for (VisitImpl visit : visits)
            {
                int seqKeyCount = 0;
                for (Double seqNum : all.getVisitSequenceMap().get(visit.getRowId()))
                {
                    Integer c = countKeysForSequence.get(seqNum);
                    seqKeyCount += c == null ? 1 : c;
                }
                totalSeqKeyCount += seqKeyCount;
                %><td class="labkey-participant-view-header" colspan="<%=seqKeyCount%>"><%= h(visit.getDisplayString()) %></td><%
            }
        %>
    </tr>

    <tr class="labkey-alternate-row">
        <td class="labkey-participant-view-header"><img alt="" width=1 height=1 src="<%=contextPath%>/_.gif"></td><%

        for (VisitImpl visit : visits)
        {
            Collection<Double> sequences = all.getVisitSequenceMap().get(visit.getRowId());
            for (Double seqNum : sequences)
            {
                Date date = visitDates.get(seqNum);
                Integer keyCount = countKeysForSequence.get(seqNum);
                if (null == keyCount)
                    keyCount = 1;
                %><td class="labkey-participant-view-header" colspan="<%=keyCount%>"><%= null==date ? "&nbsp;" : ConvertUtils.convert(date) %></td><%
            }
        }
        %>
    </tr>

    <%
        Map<ParticipantDataMapKey, AllParticipantData.RowSet> valueMap = all.getValueMap();
        ParticipantDataMapKey pdKey = new ParticipantDataMapKey(0, 0);

        for (DataSetDefinition dataSet : datasets)
        {
            if (!all.getDataSetIds().contains(dataSet.getDataSetId()))
                continue;

            // Do not display demographic data here. That goes in a separate web part,
            // the participant characteristics
            if (dataSet.isDemographicData())
                continue;

            String typeURI = dataSet.getTypeURI();
            if (null == typeURI)
                continue;

            pdKey.datasetId = dataSet.getDataSetId();
            boolean expanded = false;
            if ("expand".equalsIgnoreCase(expandedMap.get(pdKey.datasetId)))
                expanded = true;

            // sort the properties so they appear in the same order as the grid view
            PropertyDescriptor[] pds = sortProperties(StudyController.getParticipantPropsFromCache(HttpView.getRootContext(), typeURI), dataSet, HttpView.getRootContext());
            if (!dataSet.canRead(user))
            {
                %><tr class="labkey-header"><th nowrap align="left" class="labkey-expandable-row-header"><%=h(dataSet.getDisplayString())%></th><td colspan="<%=totalSeqKeyCount%>" nowrap align="left" class="labkey-expandable-row-header">(no access)</td></tr><%
                continue;
            }

            %>
            <tr class="labkey-header">
            <th nowrap colspan="<%=totalSeqKeyCount+1%>" align="left" class="labkey-expandable-row-header"><a title="Click to expand" href="<%=new ActionURL("Study", "expandStateNotify", study.getContainer()).addParameter("datasetId", Integer.toString(pdKey.datasetId)).addParameter("id", Integer.toString(bean.getDatasetId()))%>" onclick="return collapseExpand(this, true);"><%=h(dataSet.getDisplayString())%></a><%
            if (null != StringUtils.trimToNull(dataSet.getDescription()))
            {
                %><%=PageFlowUtil.helpPopup(dataSet.getDisplayString(), dataSet.getDescription())%><%
            }
            %></th>
            </tr>
            <%

            for (Report report : ReportService.get().getReports(user, study.getContainer(), Integer.toString(pdKey.datasetId)))
            {
                if (updateAccess)
                {
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td><a href="<%=new ActionURL(ReportsController.DeleteReportAction.class, study.getContainer()).addParameter(ReportDescriptor.Prop.redirectUrl.name(), currentUrl).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId().toString())%>">[remove]</a></td>
                </tr>
                <%
                }
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="<%=totalSeqKeyCount%>"><img src="<%=new ActionURL(ReportsController.PlotChartAction.class, study.getContainer()).addParameter("participantId", bean.getParticipantId()).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId().toString())%>"></td>
                </tr>
                <%
            }

            if (updateAccess)
            {
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="<%=totalSeqKeyCount+1%>" class="labkey-alternate-row">[<a href="<%=url.replaceParameter("queryName", dataSet.getLabel()).replaceParameter("datasetId", String.valueOf(pdKey.datasetId))%>">add chart</a>]</td>
                </tr>
                <%
            }
            int row = 0;
            String className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";

            // display details link(s) only if we have a source lsid in at least one of the rows
            boolean hasSourceLsid = false;

            if (StudyManager.getInstance().showQCStates(context.getContainer()))
            {
                row++;
                %>
                <tr style="<%=expanded ? "" : "display:none"%>"><td class="<%= className %>" align="left" nowrap>QC State</td>
                <%
                for (VisitImpl visit : visits)
                {
                    for (double seq : all.getVisitSequenceMap().get(visit.getRowId()))
                    {
                        pdKey.sequenceNum = seq;
                        AllParticipantData.RowSet keyMap = valueMap.get(pdKey);
                        int countTD = 0;
                        if (null != keyMap)
                        {
                            for (AllParticipantData.Row propMap : keyMap.getAll())
                            {
                                QCState state = propMap.getQCState();
                                boolean hasDescription = state != null && state.getDescription() != null && state.getDescription().length() > 0;
                                %>
                                    <td class="<%=className%>">
                                        <%= state == null ? "Unspecified" : h(state.getLabel())%><%= hasDescription ? helpPopup("QC State: " + state.getLabel(), state.getDescription()) : "" %>
                                    </td>
                                <%
                                countTD++;
                            }
                        }
                        // do we need to pad?
                        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
                        if (countTD < maxTD)
                        {
                            %><td class="<%=className%>" colspan="<%=maxTD-countTD%>">&nbsp;</td><%
                        }
                    }
                }
                %>
                </tr>
                <%
            }
            for (PropertyDescriptor pd : pds)
            {
                if (pd == null) continue;
                row++;
                className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
                String labelName = pd.getLabel();
                if (StringUtils.isEmpty(labelName))
                    labelName = pd.getName();
                %>
                <tr class="<%=className%>" style="<%=expanded ? "" : "display:none"%>"><td align="left" nowrap><%=h(labelName)%></td><%
                for (VisitImpl visit : visits)
                {
                    for (double seq : all.getVisitSequenceMap().get(visit.getRowId()))
                    {
                        // UNDONE
                        pdKey.sequenceNum = seq;
                        AllParticipantData.RowSet keyMap = valueMap.get(pdKey);
                        int countTD = 0;
                        if (null != keyMap)
                        {
                            for (AllParticipantData.Row propMap : keyMap.getAll())
                            {
                                if (propMap.getSourceLsid() != null)
                                    hasSourceLsid = true;
                                Object value = propMap.get(pd.getPropertyId());
                                %><td><%= (null == value ? "&nbsp;" : h(ConvertUtils.convert(value), true))%></td><%
                                countTD++;
                            }
                        }
                        // do we need to pad?
                        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
                        if (countTD < maxTD)
                        {
                            %><td colspan="<%=maxTD-countTD%>">&nbsp;</td><%
                        }
                    }
                }
            %></tr><%
            }
            if (hasSourceLsid) // Need to display a details link
            {
                row++;
                className = row % 2 == 0 ? "labkey-alternate-row" : "labkey-row";
                %>
                <tr class="<%=className%>" style="<%=expanded ? "" : "display:none"%>"><td align="left" nowrap>Details</td><%
                for (VisitImpl visit : visits)
                {
                    for (double seq : all.getVisitSequenceMap().get(visit.getRowId()))
                    {
                        pdKey.sequenceNum = seq;
                        AllParticipantData.RowSet keyMap = valueMap.get(pdKey);
                        int countTD = 0;
                        if (null != keyMap)
                        {
                            for (AllParticipantData.Row propMap : keyMap.getAll())
                            {
                                String link = "&nbsp;";
                                String sourceLsid = propMap.getSourceLsid();

                                if (sourceLsid != null && LsidManager.get().hasPermission(sourceLsid, getViewContext().getUser(), ReadPermission.class))
                                {
                                    ActionURL sourceURL = new ActionURL(StudyController.DatasetItemDetailsAction.class, context.getContainer());
                                    sourceURL.addParameter("sourceLsid", sourceLsid);
                                    link = "[<a href=\"" + sourceURL.getLocalURIString() + "\">details</a>]";
                                }
                                %><td><%= link%></td><%
                                countTD++;
                            }
                        }
                        // do we need to pad?
                        int maxTD = countKeysForSequence.get(seq) == null ? 1 : countKeysForSequence.get(seq);
                        if (countTD < maxTD)
                        {
                            %><td colspan="<%=maxTD-countTD%>">&nbsp;</td><%
                        }
                    }
                }
            %></tr><%
            }
        }
    %>
</table>
<%!
PropertyDescriptor[] sortProperties(PropertyDescriptor[] pds, DataSet dsd, ViewContext context)
{
    final Map<String, Integer> sortMap = StudyController.getSortedColumnList(context, dsd);
    if (sortMap != null && !sortMap.isEmpty())
    {
        final PropertyDescriptor[] props = new PropertyDescriptor[sortMap.size()];
        for (PropertyDescriptor p : pds)
        {
            if (sortMap.containsKey(p.getName()))
                props[sortMap.get(p.getName())] = p;
        }
        return props;
    }
    return pds;
}
%>
