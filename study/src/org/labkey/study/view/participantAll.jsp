<%@ page import="org.apache.commons.beanutils.ConvertUtils"%>
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.SimpleFilter"%>
<%@ page import="org.labkey.api.data.Table"%>
<%@ page import="org.labkey.api.data.TableInfo"%>
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.query.QueryService"%>
<%@ page import="org.labkey.api.query.UserSchema"%>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.view.ChartDesignerBean" %>
<%@ page import="org.labkey.api.reports.report.view.ChartUtil" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.labkey.study.reports.StudyChartQueryReport" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentContext();
    UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
    String contextPath = request.getContextPath();
    JspView<StudyManager.ParticipantViewConfig> me = (org.labkey.api.view.JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    StudyManager.ParticipantViewConfig bean = me.getModelBean();

    ChartDesignerBean chartBean = new ChartDesignerBean();

    chartBean.setReportType(StudyChartQueryReport.TYPE);
    chartBean.setSchemaName(schema.getSchemaName());
    chartBean.addParam("isParticipantChart", "true");
    chartBean.addParam("participantId", bean.getParticipantId());
    String currentUrl = bean.getRedirectUrl();
    if (currentUrl == null)
        currentUrl = context.getActionURL().getLocalURIString();

    ActionURL url = ChartUtil.getChartDesignerURL(context, chartBean);
    url.setPageFlow("Study-Reports");
    url.addParameter("returnUrl", currentUrl);
    StudyManager manager = StudyManager.getInstance();
    Study study = manager.getStudy(context.getContainer());
    StudyManager.AllParticipantData all = manager.getAllParticpantData(study, bean.getParticipantId());

    Visit[] allVisits = manager.getVisits(study);
    ArrayList<Visit> visits = new ArrayList<Visit>(all.getVisitSequenceMap().size());
    for (Visit visit : allVisits)
    {
        if (all.getVisitSequenceMap().containsKey(visit.getRowId()))
            visits.add(visit);
    }

    // UNDONE Move this code into StudyManager.getAllParticpantData()
    Map<Double, Date> visitDates = new TreeMap<Double, Date>();
    TableInfo tinfoParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
    SimpleFilter f = new SimpleFilter();
    f.addCondition("Container", study.getContainer());
    f.addCondition("ParticipantId", bean.getParticipantId());
    ResultSet rsVisitDates = Table.select(tinfoParticipantVisit, PageFlowUtil.set("SequenceNum", "VisitDate"), f, null);
    while (rsVisitDates.next())
    {
        Double seq = rsVisitDates.getDouble(1);
        Date date = rsVisitDates.getTimestamp(2);
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
    for (Map.Entry entry : all.getValueMap().entrySet())
    {
        ParticipantDataMapKey key = (ParticipantDataMapKey) entry.getKey();
        Map keyMap = (Map) entry.getValue();
        Integer count = countKeysForSequence.get(key.sequenceNum);
        if (count == null || keyMap.size() > count)
            countKeysForSequence.put(key.sequenceNum, keyMap.size());
    }

    User user = (User) request.getUserPrincipal();
    DataSetDefinition[] datasets = manager.getDataSetDefinitions(study);
    Map<Integer, String> expandedMap = StudyController.getExpandedState(context, bean.getDatasetId());
    boolean updateAccess = study.getContainer().hasPermission(user, ACL.PERM_UPDATE);

    String shadeColor = "#EEEEEE";
// UNDONE: move into stylesheet
    String borderColor = "#808080";
    String headerColor = "#CCCCCC";
//    String styleTD = "border-right:solid 1px " + borderColor + ";";
    String styleTH = "border-right:solid 1px " + borderColor + "; border-top:solid 2px " + borderColor + ";";

    int totalSeqKeyCount = 0;
%>
<style type="text/css">
    .gth {<%=styleTH%> }
</style>
<p/>

<table border="0" cellspacing="0" cellpadding="2" class="normal" style="border-bottom:solid 2px <%=borderColor%>;">

    <tr bgcolor="<%=shadeColor%>">
        <td class=gth><img alt="" width=180 height=1 src="<%=contextPath%>/_.gif"></td><%

            for (Visit visit : visits)
            {
                int seqKeyCount = 0;
                for (Double seqNum : (Collection<Double>)all.getVisitSequenceMap().get(visit.getRowId()))
                {
                    Integer c = countKeysForSequence.get(seqNum);
                    seqKeyCount += c == null ? 1 : c;
                }
                totalSeqKeyCount += seqKeyCount;
                %><td align="center" valign="top" bgcolor="<%=shadeColor%>" class="gth" colspan="<%=seqKeyCount%>"><%= h(visit.getDisplayString()) %></td><%
            }
        %>
    </tr>

    <tr bgcolor="<%=shadeColor%>">
        <td class=gth><img alt="" width=1 height=1 src="<%=contextPath%>/_.gif"></td><%

        for (Visit visit : visits)
        {
            Collection<Double> sequences = ((Collection<Double>) all.getVisitSequenceMap().get(visit.getRowId()));
            for (Double seqNum : sequences)
            {
                Date date = visitDates.get(seqNum);
                Integer keyCount = countKeysForSequence.get(seqNum);
                if (null == keyCount)
                    keyCount = 1;
                %><td align="center" valign="top" bgcolor="<%=shadeColor%>" class=gth  colspan="<%=keyCount%>"><%= null==date ? "&nbsp;" : ConvertUtils.convert(date) %></td><%
            }
        }
        %>
    </tr>

    <%
        Map<ParticipantDataMapKey,Map<String,Map<Integer,Object>>> valueMap = all.getValueMap(); 
        ParticipantDataMapKey pdKey = new ParticipantDataMapKey(0, 0);

        for (DataSetDefinition dataSet : datasets)
        {
            if (!all.getDataSetIds().contains(dataSet.getDataSetId()))
                continue;
            String typeURI = dataSet.getTypeURI();
            if (null == typeURI)
                continue;

            pdKey.datasetId = dataSet.getDataSetId();
            boolean expanded = expandedMap.containsKey(pdKey.datasetId);

            // sort the properties so they appear in the same order as the grid view
            PropertyDescriptor[] pds = sortProperties(StudyController.getParticipantPropsFromCache(HttpView.getRootContext(), typeURI), dataSet, HttpView.getRootContext());
            if (!dataSet.canRead(user))
            {
                %><tr class="header"><th nowrap align="left" bgcolor="<%=headerColor%>" style="border-top:solid 2px <%=borderColor%>; border-bottom:solid 2px <%=borderColor%>;"><%=h(dataSet.getDisplayString())%></th><td colspan="<%=totalSeqKeyCount%>" nowrap align="left" bgcolor="<%=headerColor%>" style="border-top:solid 2px <%=borderColor%>; border-bottom:solid 2px <%=borderColor%>;">(no access)</td></tr><%
                continue;
            }

            %>
            <tr class="header">
            <th nowrap colspan="<%=totalSeqKeyCount+1%>" align="left" bgcolor="<%=headerColor%>" style="border-top:solid 2px <%=borderColor%>; border-bottom:solid 2px <%=borderColor%>;"><a title="Click to expand" href="<%=new ActionURL("Study", "expandStateNotify", study.getContainer()).addParameter("datasetId", Integer.toString(pdKey.datasetId)).addParameter("id", Integer.toString(bean.getDatasetId()))%>" onclick="return collapseExpand(this, true);"><%=h(dataSet.getDisplayString())%></a><%
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
                    <td><a href="<%=new ActionURL(ReportsController.DeleteReportAction.class, study.getContainer()).addParameter(ReportDescriptor.Prop.redirectUrl.name(), currentUrl).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId())%>">[remove]</a></td>
                </tr>
                <%
                }
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="<%=totalSeqKeyCount%>"><img border=0 src="<%=new ActionURL(ReportsController.PlotChartAction.class, study.getContainer()).addParameter("participantId", bean.getParticipantId()).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId())%>"></td>
                </tr>
                <%
            }

            if (updateAccess)
            {
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="<%=totalSeqKeyCount+1%>" class="studyShaded">[<a href="<%=url.replaceParameter("queryName", dataSet.getLabel()).replaceParameter("datasetId", String.valueOf(pdKey.datasetId))%>">add chart</a>]</td>
                </tr>
                <%
            }

            int row = 0;
            for (PropertyDescriptor pd : pds)
            {
                if (pd == null) continue;
                row++;
                String className = row % 2 == 0 ? "studyShaded" : "studyCell";
                String labelName = pd.getLabel();
                if (StringUtils.isEmpty(labelName))
                    labelName = pd.getName();
                %>
                <tr style="<%=expanded ? "" : "display:none"%>"><td class="<%=className%>" align="left" nowrap><%=h(labelName)%></td><%
                for (Visit visit : visits)
                {
                    for (double seq : (Collection<Double>) all.getVisitSequenceMap().get(visit.getRowId()))
                    {
                        // UNDONE
                        pdKey.sequenceNum = seq;
                        Map<String,Map<Integer,Object>> keyMap = valueMap.get(pdKey);
                        int countTD = 0;
                        if (null != keyMap)
                        {
                            for (Map<Integer,Object> propMap : keyMap.values())
                            {
                                Object value = propMap.get(pd.getPropertyId());
                                %><td class="<%=className%>"><%= (null == value ? "&nbsp;" : h(ConvertUtils.convert(value)))%></td><%
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
            %></tr><%
            }
        }
    %>
</table>
<%!
PropertyDescriptor[] sortProperties(PropertyDescriptor[] pds, DataSetDefinition dsd, ViewContext context)
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