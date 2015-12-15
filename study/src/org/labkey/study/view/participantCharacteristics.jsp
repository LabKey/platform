<%
/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.collections.CsvSet" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page import="org.labkey.api.reports.report.view.ChartDesignerBean" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.util.ExceptionUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.DatasetController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.reports.StudyChartQueryReport" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Arrays" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = getViewContext();
    UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "study");
    JspView<StudyManager.ParticipantViewConfig> me = (JspView<StudyManager.ParticipantViewConfig>) HttpView.currentView();
    StudyManager.ParticipantViewConfig bean = me.getModelBean();

    ChartDesignerBean chartBean = new ChartDesignerBean();

    chartBean.setReportType(StudyChartQueryReport.TYPE);
    chartBean.setSchemaName(schema.getSchemaName());
    String currentUrl = bean.getRedirectUrl();
    if (currentUrl == null)
        currentUrl = getActionURL().getLocalURIString();

    ActionURL url = ReportUtil.getChartDesignerURL(context, chartBean);
    url.setAction(ReportsController.DesignChartAction.class);
    url.addParameter("returnUrl", currentUrl);
    url.addParameter("isParticipantChart", "true");
    url.addParameter("participantId", bean.getParticipantId());

    StudyManager manager = StudyManager.getInstance();
    Study study = manager.getStudy(getContainer());

    User user = (User) request.getUserPrincipal();
    List<DatasetDefinition> datasets = manager.getDatasetDefinitions(study);
    Map<Integer, String> expandedMap = StudyController.getExpandedState(context, bean.getDatasetId());
    boolean updateAccess = study.getContainer().hasPermission(user, UpdatePermission.class);
%>

<table class="labkey-data-region">

    <%
        for (DatasetDefinition dataset : datasets)
        {
            // Only interested in default-visible visible demographic data
            if (!dataset.isDemographicData() || !dataset.isShowByDefault())
                continue;

            String typeURI = dataset.getTypeURI();
            if (null == typeURI)
                continue;

            int datasetId = dataset.getDatasetId();
            boolean expanded = true;
            if ("collapse".equalsIgnoreCase(expandedMap.get(datasetId)))
                expanded = false;

            // sort the properties so they appear in the same order as the grid view
            List<PropertyDescriptor> pds = sortProperties(StudyController.getParticipantPropsFromCache(context, typeURI), dataset, context);
            if (!dataset.canRead(user))
            {
    %>
    <tr class="labkey-header">
        <th nowrap align="left" class="labkey-expandable-row-header"><%=h(dataset.getDisplayString())%>
        </th>
        <td nowrap align="left" class="labkey-expandable-row-header">(no access)</td>
    </tr>
    <%
            continue;
        }

    %>
    <tr class="labkey-header">
        <th nowrap colspan="<%=2%>" align="left" class="labkey-expandable-row-header">
            <a title="Click to expand/collapse"
               href="<%=new ActionURL(StudyController.ExpandStateNotifyAction.class, study.getContainer()).addParameter("datasetId", Integer.toString(datasetId)).addParameter("id", Integer.toString(bean.getDatasetId()))%>"
               onclick="return LABKEY.Utils.toggleLink(this, true);">
                <img src="<%=getContextPath()%>/_images/<%= text(expanded ? "minus.gif" : "plus.gif") %>"
                     alt="Click to expand/collapse">
                <%=h(dataset.getDisplayString())%>
            </a><%
            if (null != StringUtils.trimToNull(dataset.getDescription()))
            {
        %><%=PageFlowUtil.helpPopup(dataset.getDisplayString(), dataset.getDescription())%><%
            }
        %></th>
    </tr>
    <%

        for (Report report : ReportService.get().getReports(user, study.getContainer(), Integer.toString(datasetId)))
        {
            if (updateAccess)
            {
    %>
    <tr style="<%=text(expanded ? "" : "display:none")%>">
        <td>
            <a href="<%=new ActionURL(ReportsController.DeleteReportAction.class, study.getContainer()).addParameter(ReportDescriptor.Prop.redirectUrl.name(), currentUrl).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId().toString())%>">[remove]</a>
        </td>
    </tr>
    <%
        }
    %>
    <tr style="<%=text(expanded ? "" : "display:none")%>">
        <td><img
                src="<%=new ActionURL(ReportsController.PlotChartAction.class, study.getContainer()).addParameter("participantId", bean.getParticipantId()).addParameter(ReportDescriptor.Prop.reportId.name(), report.getDescriptor().getReportId().toString())%>">
        </td>
    </tr>
    <%
        }

        int row = 0;

        // Cache the request for data, so that we don't have to repeat this query
        // once for each row and column, but only once per row.
        TableInfo datasetTable = dataset.getTableInfo(user);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(StudyService.get().getSubjectColumnName(dataset.getContainer()), bean.getParticipantId());

        Sort sort;
        if (study.getTimepointType() != TimepointType.VISIT)
        {
            sort = new Sort("-Date");
        }
        else
        {
            sort = new Sort("-SequenceNum");
        }

        // Issue 13496: it is possible for older studies (<12.1) to have multiple records for a participant in a demographic dataset, so default to the first one
        Map<String, Object>[] results = new TableSelector(datasetTable, new CsvSet("lsid," + StudyService.get().getSubjectColumnName(dataset.getContainer()) + ",Date,SequenceNum"), filter, sort).getMapArray();
        if (results.length > 1)
        {
            String msg = "Unexpected number of demographic dataset records. Expected 0 or 1 but found " + results.length + "\n" +
                    dataset.getName() + " in container " + dataset.getContainer().getPath();
            ExceptionUtil.logExceptionToMothership(context.getRequest(), new IllegalStateException(msg));
        }
        String lsid = results.length > 0 ? (String) results[0].get("lsid") : null;
        Map<String, Object> datasetRow = null;

        if (lsid != null)
        {
            datasetRow = dataset.getDatasetRow(user, lsid);
        }

        boolean editAccess = dataset.canWrite(user);
        if (datasetRow == null)
        {
            if (editAccess)
            {
                ActionURL addAction = new ActionURL(DatasetController.InsertAction.class, getContainer());
                addAction.addParameter("datasetId", datasetId);
                addAction.addParameter("quf_ParticipantId", bean.getParticipantId());

    %>
    <td colspan="2" class="labkey-alternate-row"><%=textLink("add", addAction)%>
    </td>
    <%
            }

            continue;
        }

        if (editAccess)
        {
    %>
    <tr class="labkey-alternate-row" style="<%=text(expanded ? "" : "display:none")%>">
        <td colspan="2"><%

            ActionURL editAction = new ActionURL(DatasetController.UpdateAction.class, getContainer());
            editAction.addParameter("datasetId", datasetId);
            editAction.addParameter("lsid", lsid);

        %><%=textLink("edit data", editAction)%>
        </td>
    </tr>
    <%
        }

        for (PropertyDescriptor pd : pds)
        {
            if (pd == null) continue;
            row++;
            String labelName = pd.getLabel();
            if (StringUtils.isEmpty(labelName))
                labelName = pd.getName();
    %>
    <tr class="<%=getShadeRowClass(row % 2 == 0)%>" style="<%=text(expanded ? "" : "display:none")%>">
        <td align="left" nowrap><%=h(labelName)%>
        </td>
        <%
            Object value = datasetRow.get(pd.getName());
        %>
        <td><%=format(value)%>
        </td>
        <%

        %></tr>
    <%
            }
        }
    %>
</table>
<%!
    List<PropertyDescriptor> sortProperties(List<PropertyDescriptor> pds, Dataset dsd, ViewContext context)
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
            return Arrays.asList(props);
        }
        return pds;
    }

    _HtmlString format(Object value)
    {
        if (value instanceof Date)
            return formatDate((Date)value);

        if (value instanceof Number)
            return new _HtmlString(h(Formats.formatNumber(getContainer(), (Number)value)));

        return new _HtmlString(null == value ? "&nbsp;" : h(ConvertUtils.convert(value), true));
    }
%>
