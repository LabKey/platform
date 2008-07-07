<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Sort"%>
<%@ page import="org.labkey.api.data.Table"%>
<%@ page import="org.labkey.api.data.TableInfo"%>
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.query.QueryService"%>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ChartDesignerBean" %>
<%@ page import="org.labkey.api.reports.report.view.ChartUtil" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.reports.StudyChartQueryReport" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
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

%>
<p/>

<table border="0" cellspacing="0" cellpadding="2" class="normal" style="border-bottom:solid 2px <%=borderColor%>;">

    <%
        for (DataSetDefinition dataSet : datasets)
        {
            // Only interested in demographic data
            if (!dataSet.isDemographicData())
                continue;

            String typeURI = dataSet.getTypeURI();
            if (null == typeURI)
                continue;

            int datasetId = dataSet.getDataSetId();
            boolean expanded = expandedMap.containsKey(datasetId);

            // sort the properties so they appear in the same order as the grid view
            PropertyDescriptor[] pds = sortProperties(StudyController.getParticipantPropsFromCache(HttpView.getRootContext(), typeURI), dataSet, HttpView.getRootContext());
            if (!dataSet.canRead(user))
            {
                %><tr class="header"><th nowrap align="left" bgcolor="<%=headerColor%>" style="border-top:solid 2px <%=borderColor%>; border-bottom:solid 2px <%=borderColor%>;"><%=h(dataSet.getDisplayString())%></th><td nowrap align="left" bgcolor="<%=headerColor%>" style="border-top:solid 2px <%=borderColor%>; border-bottom:solid 2px <%=borderColor%>;">(no access)</td></tr><%
                continue;
            }

            %>
            <tr class="header">
            <th nowrap colspan="<%=2%>" align="left" bgcolor="<%=headerColor%>" style="border-top:solid 2px <%=borderColor%>; border-bottom:solid 2px <%=borderColor%>;"><a title="Click to expand" href="<%=new ActionURL("Study", "expandStateNotify", study.getContainer()).addParameter("datasetId", Integer.toString(datasetId)).addParameter("id", Integer.toString(bean.getDatasetId()))%>" onclick="return collapseExpand(this, true);"><%=h(dataSet.getDisplayString())%></a><%
            if (null != StringUtils.trimToNull(dataSet.getDescription()))
            {
                %><%=PageFlowUtil.helpPopup(dataSet.getDisplayString(), dataSet.getDescription())%><%
            }
            %></th>
            </tr>
            <%

            for (Report report : ReportService.get().getReports(user, study.getContainer(), Integer.toString(datasetId)))
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
                    <td><img border=0 src="<%=new ActionURL(ReportsController.PlotChartAction.class, study.getContainer()).addParameter("participantId", bean.getParticipantId()).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId())%>"></td>
                </tr>
                <%
            }

            if (updateAccess)
            {
                %>
                <tr style="<%=expanded ? "" : "display:none"%>">
                    <td colspan="2" class="studyShaded">[<a href="<%=url.replaceParameter("queryName", dataSet.getLabel()).replaceParameter("datasetId", String.valueOf(datasetId))%>">add chart</a>]</td>
                </tr>
                <%
            }

            int row = 0;

            // Cache the request for data, so that we don't have to repeat this query
            // once for each row and column, but only once per row.
            TableInfo datasetTable = dataSet.getTableInfo(user);
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("ParticipantId", bean.getParticipantId());

            Sort sort;
            if (study.isDateBased())
            {
                sort = new Sort("-Date");
            }
            else
            {
                sort = new Sort("-SequenceNum");
            }


            Map<String,String> result = Table.selectObject(datasetTable, Collections.singleton("lsid"), filter, sort, Map.class);
            String lsid = result != null ? result.get("lsid") : null;
            Map<String,Object> datasetRow = null;

            if (lsid != null)
            {
                datasetRow = StudyService.get().getDatasetRow(user, context.getContainer(), datasetId, lsid);
            }

            if (datasetRow == null)
            {
                // TODO: display "[add]" to add a new entry for this participant
                continue;
            }

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

                    Object value = datasetRow.get(pd.getName());
                    %><td class="<%=className%>"><%= (null == value ? "&nbsp;" : h(ConvertUtils.convert(value)))%></td><%
                   
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