<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page import="org.labkey.api.util.ExtUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<VisualizationController.GenericReportForm> me = (JspView<VisualizationController.GenericReportForm>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    VisualizationController.GenericReportForm form = me.getModelBean();
    String numberFormat = PropertyManager.getProperties(ctx.getContainer(), "DefaultStudyFormatStrings").get("NumberFormatString");
    String numberFormatFn;
    if(numberFormat == null)
    {
        numberFormat = Formats.f1.toPattern();
    }
    numberFormatFn = ExtUtil.toExtNumberFormatFn(numberFormat);

    String renderId = "generic-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("vis/genericChart/genericChartPanel.js");
    LABKEY.requiresVisualization();

</script>

<script type="text/javascript">
    Ext4.QuickTips.init();

    Ext4.onReady(function(){

        var panel = Ext4.create('LABKEY.ext4.GenericChartPanel', {
            height          : 600,
            reportId        : <%=form.getReportId() != null ? q(form.getReportId().toString()) : null %>,
            schemaName      : <%=form.getSchemaName() != null ? q(form.getSchemaName()) : null %>,
            queryName       : <%=form.getQueryName() != null ? q(form.getQueryName()) : null %>,
            dataRegionName  : '<%=form.getDataRegionName()%>',
            renderType      : '<%=form.getRenderType()%>',
            id              : '<%=form.getComponentId() %>',
            baseUrl         : '<%=ctx.getActionURL()%>',
            renderTo        : '<%= renderId %>',
            allowShare      : <%=c.hasPermission(ctx.getUser(), ShareReportPermission.class)%>,
            isDeveloper     : <%=ctx.getUser().isDeveloper()%>,
            hideSave        : <%=ctx.getUser().isGuest()%>,
            autoColumnYName  : <%=form.getAutoColumnYName() != null ? q(form.getAutoColumnYName()) : null%>,
            autoColumnXName  : <%=form.getAutoColumnXName() != null ? q(form.getAutoColumnXName()) : null%>,
            defaultNumberFormat: eval("<%=numberFormatFn%>"),
            allowEditMode: <%=!ctx.getUser().isGuest() && form.allowToggleMode()%>,
            firstLoad: true
        });

        var _resize = function(w,h) {
            LABKEY.Utils.resizeToViewport(panel, w, -1); // don't fit to height
        };

        Ext4.EventManager.onWindowResize(_resize);
    });

    function customizeGenericReport(elementId) {

        function initPanel() {
            var panel = Ext4.getCmp(elementId);

            if (panel) { panel.customize(); }
        }
        Ext4.onReady(initPanel);
    }

</script>

<div id="<%= renderId%>" style="width:100%;"></div>

