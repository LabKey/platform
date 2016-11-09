<%
/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.util.ExtUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("timechart");
    }
%>
<%
    JspView<VisualizationController.GetVisualizationForm> me = (JspView<VisualizationController.GetVisualizationForm>) HttpView.currentView();
    ViewContext ctx = getViewContext();
    VisualizationController.GetVisualizationForm form = me.getModelBean();
    boolean canEdit = false;
    boolean canShare = ctx.hasPermission(ShareReportPermission.class);
    boolean isDeveloper = getUser().isDeveloper();

    String numberFormat = PropertyManager.getProperties(getContainer(), "DefaultStudyFormatStrings").get("NumberFormatString");
    if (numberFormat == null)
        numberFormat = Formats.f1.toPattern();
    String numberFormatFn = ExtUtil.toExtNumberFormatFn(numberFormat);

    ReportIdentifier id = form.getReportId();
    ActionURL editUrl = null;

    if (id != null)
    {
        Report report = id.getReport(ctx);
        if (report != null)
        {
            canEdit = report.canEdit(getUser(), getContainer());
            editUrl = report.getEditReportURL(ctx);
        }
    }
    else
    {
        canEdit = ctx.hasPermission(ReadPermission.class) && ! getUser().isGuest();
    }

    String elementId = "vis-wizard-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<labkey:scriptDependency/>
<div id="<%=h(elementId)%>"></div>

<script type="text/javascript">
    var init = function(){
        var reportId = <%=q(id != null ? id.toString() : null)%>;
        if (reportId != null)
        {
            LABKEY.Query.Visualization.get({
                reportId: reportId,
                success: function(result)
                {
                    if (result.type == LABKEY.Query.Visualization.Type.TimeChart)
                    {
                        var saveReportInfo = {
                            name: result.name,
                            description: result.description,
                            queryName: result.queryName,
                            schemaName: result.schemaName,
                            shared: result.shared,
                            ownerId: result.ownerId,
                            createdBy: result.createdBy,
                            reportProps: result.reportProps,
                            thumbnailURL: result.thumbnailURL
                        };

                        // TODO verify conversion for backwards compatibility
                        LABKEY.vis.TimeChartHelper.convertSavedReportConfig(result.visualizationConfig, saveReportInfo);
                        initializeChartPanel(result.visualizationConfig, saveReportInfo);
                    }
                    else
                    {
                        displayErrorMsg("The saved chart is not of type TimeChart");
                    }
                },
                failure: function (response)
                {
                    displayErrorMsg("No time chart report found for reportId:" + reportId + ".");
                },
                scope: this
            });
        }
        else
        {
            initializeChartPanel();
        }
    };

    var initializeChartPanel = function(chartInfo, saveReportInfo)
    {
        Ext4.create('LABKEY.vis.TimeChartPanel', {
            renderTo: <%=q(elementId)%>,
            height: 650,
            minWidth: 875,
            border: false,
            chartInfo: chartInfo,
            saveReportInfo: saveReportInfo,
            canEdit: <%=canEdit%>,
            canShare: <%=canShare%>,
            isDeveloper: <%=isDeveloper%>,
            defaultNumberFormat: eval("<%=numberFormatFn%>"),
            allowEditMode: <%=!getUser().isGuest() && form.allowToggleMode()%>,
            editModeURL: <%=q(editUrl != null ? editUrl.toString() : null) %>
        });
    };

    var displayErrorMsg = function(msg)
    {
        Ext4.get(<%=q(elementId)%>).update("<span class='labkey-error'>" + msg + "</span>");
    };

    Ext4.onReady(function(){ init(); });
</script>
