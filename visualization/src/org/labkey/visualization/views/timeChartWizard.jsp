<%
/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("timechart"));
        return resources;
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
    String numberFormatFn;
    if(numberFormat == null)
    {
        numberFormat = Formats.f1.toPattern();
    }
    numberFormatFn = ExtUtil.toExtNumberFormatFn(numberFormat);

    ReportIdentifier id = form.getReportId();
    Report report = null;
    ActionURL editUrl = null;

    if (id != null)
    {
        report = id.getReport(ctx);
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
    Ext4.onReady(function(){
        // if the URL is requesting a report by Id, but it does not exist, display an error message
        if (<%= id != null && report == null %>)
        {
            Ext4.get('<%=h(elementId)%>').update("<span class='labkey-error'>Visualization does not exist for <%=id%>.</span>");
        }
        else
        {
            showTimeChartWizard({
                reportId: <%=q(id != null ? id.toString() : "")%>,
                elementId: '<%=h(elementId)%>',
                success: viewSavedChart,
                failure: displayFailureException
            });
        }
    });

    function showTimeChartWizard(config)
    {
        // get the type information from the server
        LABKEY.Query.Visualization.getTypes({
            successCallback : function(types){
                storeVisualizationTypes(types, config);
            },
            failureCallback : function(info, response, options) {
                if (info.exception)
                    displayFailureException(info);
                else
                    LABKEY.Utils.displayAjaxErrorResponse(response, options);
            },
            scope: this
        });
    }

    var viewTypes = {};
    function storeVisualizationTypes(types, config) {
        // store the type information
        for (var i=0; i < types.length; i++)
        {
            var type = types[i];
            viewTypes[type.type] = type;
        }

        // see if the wizard is being accessed with a saved visualization referenced on the URL
        if(LABKEY.Query.Visualization.getFromUrl(config)) {
            // we have a saved chart being access, viewSavedChart will be called
        }
        else {
            // no saved visualization to show, so just initialize the wizard without a pre-selected measure
            initializeTimeChartPanel(config);
        }
    }

    function viewSavedChart(result, response, options){
        if(result.type == LABKEY.Query.Visualization.Type.TimeChart){
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

            initializeTimeChartPanel(response.initialConfig, result.visualizationConfig, saveReportInfo);
        }
        else {
            Ext4.get('<%=h(elementId)%>').update("<span class='labkey-error'>The saved chart is not of type TimeChart</span>");
        }
    }

    function displayFailureException(info)
    {
        Ext4.get('<%=h(elementId)%>').update("<span class='labkey-error'>" + info.exception + "</span>");
    }

    function initializeTimeChartPanel(config, chartInfo, saveReportInfo) {
        // create a new chart panel and insert into the wizard
        Ext4.create('Ext.panel.Panel', {
            renderTo: config.elementId,
            height: 650,
            minWidth: 875,
            resizable: false,
            layout: 'border',
            frame: false,
            border: false,
            items: [{
                xtype: 'timechartpanel',
                border: false,
                region: 'center',
                viewInfo: viewTypes['line'],
                chartInfo: chartInfo,
                saveReportInfo: saveReportInfo,
                canEdit: <%=canEdit%>,
                canShare: <%=canShare%>,
                isDeveloper: <%=isDeveloper%>,
                defaultNumberFormat: eval("<%=numberFormatFn%>"),
                allowEditMode: <%=!getUser().isGuest() && form.allowToggleMode()%>,
                editModeURL: <%=q(editUrl != null ? editUrl.toString() : null) %>
            }]
        });
    }
</script>
