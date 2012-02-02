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
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<org.labkey.study.controllers.reports.ReportsController.ParticipantReportForm> me = (JspView<org.labkey.study.controllers.reports.ReportsController.ParticipantReportForm>) HttpView.currentView();
    org.labkey.study.controllers.reports.ReportsController.ParticipantReportForm bean = me.getModelBean();
    String reportId = null;

    if (bean.getReportId() != null)
        reportId = bean.getReportId().toString();

    String renderId = "participant-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<style type="text/css">
    .x4-reset .x4-border-layout-ct {
        background-color: white;
    }
</style>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresCss("study/DataViewsPanel.css");
    LABKEY.requiresScript("TemplateHelper.js");
    LABKEY.requiresScript("study/ParticipantReport.js");
    LABKEY.requiresScript("vis/measuresPanel.js");
</script>

<script type="text/javascript">

    Ext4.onReady(function(){
        var panel = Ext4.create('LABKEY.ext4.ParticipantReport', {
            height          : 600, // TODO: figure out dynamic height
            subjectColumn   : <%=q(org.labkey.api.study.StudyService.get().getSubjectColumnName(me.getViewContext().getContainer()))%>,
            subjectVisitColumn: <%=q(org.labkey.api.study.StudyService.get().getSubjectVisitColumnName(me.getViewContext().getContainer()))%>,
            renderTo        : '<%= renderId %>',
            id              : '<%= bean.getComponentId() %>',
            reportId        : <%=q(reportId)%>,
            allowCustomize  : true,
            openCustomize   : true
        });
    });

    function customizeParticipantReport(elementId) {

        function initPanel() {
            var panel = Ext4.getCmp(elementId);

            if (panel) { panel.customize(); }
        }
        Ext4.onReady(initPanel);
    }

</script>

<div id="<%= renderId%>" class="data-views-container" style="width:100%;"></div>
