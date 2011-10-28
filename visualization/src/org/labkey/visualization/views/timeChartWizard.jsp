<%
/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Report> me = (JspView<Report>) HttpView.currentView();
    Report bean = me.getModelBean();
    String reportId = "";

    if (bean != null)
        reportId = bean.getDescriptor().getReportId().toString();

    String elementId = "vis-wizard-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("vis/measuresPanel.js");
    LABKEY.requiresVisualization();
    LABKEY.requiresScript("vis/timeChartPanel.js");
    Ext.QuickTips.init();
</script>

<script type="text/javascript">

    Ext.onReady(function(){
        showTimeChartWizard({
            reportId: '<%=reportId%>',
            elementId: '<%=elementId%>',
            success: viewSavedChart
        })
    });

    function showTimeChartWizard(config)
    {
        // get the type information from the server
        LABKEY.Visualization.getTypes({
            successCallback : function(types){storeVisualizationTypes(types, config);},
            failureCallback : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
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
        if(LABKEY.Visualization.getFromUrl(config)) {
            // we have a saved chart being access, viewSavedChart will be called
        }
        else {
            // no saved visualization to show, so just initialize the wizard without a pre-selected measure
            initializeTimeChartPanel(config);
        }
    }

    function viewSavedChart(result, response, options){
        if(result.type == LABKEY.Visualization.Type.TimeChart){
            var saveReportInfo = {
                name: result.name,
                description: result.description,
                queryName: result.queryName,
                schemaName: result.schemaName,
                shared: result.shared,
                ownerId: result.ownerId,
                createdBy: result.createdBy
            };

            initializeTimeChartPanel(response.initialConfig, result.visualizationConfig, saveReportInfo);
        }
        else {
            Ext.Msg.alert("Error", "The saved chart is not of type = TimeChart");
        }
    }

    function initializeTimeChartPanel(config, chartInfo, saveReportInfo) {
        // create a new chart panel and insert into the wizard
        var panel = new Ext.Panel({
            renderTo: config.elementId,
            height: 650,
            resizable: true,
            layout: 'border',
            frame: false,
            border: false,
            items: [
                new LABKEY.vis.TimeChartPanel({
                    region: 'center',
                    layout: 'border',
                    flex: 1,
                    border: false,
                    viewInfo: viewTypes['line'],
                    chartInfo: chartInfo,
                    saveReportInfo: saveReportInfo
                })
            ]
        });
    }
</script>

<div id="<%=elementId%>" class="extContainer" style="width:100%"/>
