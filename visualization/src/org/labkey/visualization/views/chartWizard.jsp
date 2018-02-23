<%
/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page import="org.labkey.api.reports.report.view.ReportUtil" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.settings.FolderSettingsCache" %>
<%@ page import="org.labkey.api.util.ExtUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("vischart");
    }
%>
<%
    JspView<VisualizationController.ChartWizardReportForm> me = (JspView<VisualizationController.ChartWizardReportForm>) HttpView.currentView();
    VisualizationController.ChartWizardReportForm form = me.getModelBean();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    User user = getUser();

    String numberFormat = Formats.getNumberFormatString(c);
    String numberFormatFn = numberFormat != null ? ExtUtil.toExtNumberFormatFn(numberFormat) : null;

    boolean canShare = ctx.hasPermission(ShareReportPermission.class);
    boolean isDeveloper = user.isDeveloper();
    boolean allowEditMode = !user.isGuest() && form.allowToggleMode();
    boolean canEdit = false;
    ActionURL editUrl = null;

    ReportIdentifier id = form.getReportId();
    if (id != null)
    {
        Report report = id.getReport(ctx);
        if (report != null)
        {
            canEdit = report.canEdit(user, c);
            editUrl = report.getEditReportURL(ctx);
        }
    }
    else
    {
        canEdit = ctx.hasPermission(ReadPermission.class) && !user.isGuest();
    }

    String renderId = form.getComponentId() != null ? form.getComponentId() : "chart-wizard-report";
%>
<div id="<%=h(renderId)%>"></div>
<script type="text/javascript">

    var init = function(reportId, renderTo, canEdit, editUrl)
    {
        if (reportId != null)
        {
            LABKEY.Query.Visualization.get({
                reportId: reportId,
                success: function(result)
                {
                    initializeChartWizardPanel(renderTo, canEdit, editUrl, result);

                },
                failure: function (response)
                {
                    displayErrorMsg(renderTo, "No saved chart wizard report found for reportId:" + reportId + ".");
                },
                scope: this
            });
        }
        else
        {
            initializeChartWizardPanel(renderTo, canEdit, editUrl);
        }
    };

    var initializeChartWizardPanel = function(renderTo, canEdit, editUrl, savedReportInfo)
    {
        var hasSavedReport = Ext4.isDefined(savedReportInfo),
            saveReportIsGenericChart = hasSavedReport && savedReportInfo.type == LABKEY.Query.Visualization.Type.GenericChart,
            saveReportIsTimeChart = hasSavedReport && savedReportInfo.type == LABKEY.Query.Visualization.Type.TimeChart;
        if (hasSavedReport && !saveReportIsGenericChart && !saveReportIsTimeChart)
        {
            displayErrorMsg(renderTo, "The saved chart does not match one of the expected chart types.");
            return;
        }

        // only allow auto resize for the height this for chart wizard action (i.e. this is the only component on the page)
        var autoResizeSkipHeight = LABKEY.ActionURL.getAction() != 'runReport' && LABKEY.ActionURL.getAction() != 'genericChartWizard';

        Ext4.create('LABKEY.ext4.BaseChartWizardPanel', {
            renderTo: renderTo,
            savedReportInfo: savedReportInfo,
            canEdit: canEdit,
            canShare: <%=canShare%>,
            isDeveloper: <%=isDeveloper%>,
            defaultNumberFormat: eval("<%=text(numberFormatFn)%>"),
            allowEditMode: <%=allowEditMode%>,
            editModeURL: editUrl,

            baseUrl: <%=q(getActionURL().toString())%>,
            schemaName: <%=q(form.getSchemaName() != null ? form.getSchemaName() : null) %>,
            queryName: <%=q(form.getQueryName() != null ? form.getQueryName() : null) %>,
            queryLabel: <%=q(ReportUtil.getQueryLabelByName(user, c, form.getSchemaName(), form.getQueryName()))%>,
            viewName: <%=q(form.getViewName() != null ? form.getViewName() : null) %>,
            dataRegionName: <%=q(form.getDataRegionName())%>,

            renderType: <%=q(form.getRenderType())%>,
            autoColumnName  : <%=q(form.getAutoColumnName() != null ? form.getAutoColumnName() : null) %>,
            autoColumnYName  : <%=q(form.getAutoColumnYName() != null ? form.getAutoColumnYName() : null) %>,
            autoColumnXName  : <%=q(form.getAutoColumnXName() != null ? form.getAutoColumnXName() : null) %>,
            restrictColumnsEnabled: <%=FolderSettingsCache.areRestrictedColumnsEnabled(c)%>,

            minWidth: 900,
            autoResize: {
                skipHeight: autoResizeSkipHeight
            }
        });
    };

    var displayErrorMsg = function(renderTo, msg)
    {
        Ext4.get(renderTo).update("<span class='labkey-error'>" + msg + "</span>");
    };

    Ext4.onReady(function()
    {
        var renderTo = <%=q(renderId)%>,
            reportId = <%=q(id != null ? id.toString() : null) %>,
            canEdit = <%=canEdit%>,
            editUrl = <%=q(editUrl != null ? editUrl.toString() : null) %>;

        init(reportId, renderTo, canEdit, editUrl);
    });
</script>

