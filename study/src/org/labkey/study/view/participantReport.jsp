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
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController.ParticipantReportForm" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/ParticipantReport");
    }
%>
<%
    JspView<org.labkey.study.controllers.reports.ReportsController.ParticipantReportForm> me = (JspView<ParticipantReportForm>) HttpView.currentView();
    ParticipantReportForm bean = me.getModelBean();
    String reportId = null;

    if (bean.getReportId() != null)
        reportId = bean.getReportId().toString();

    Container c = getContainer();
    User user = getUser();
    Study s = StudyManager.getInstance().getStudy(c);

    // for testing
    boolean isPrint = getActionURL().getParameter("print") != null;
    String renderId = "participant-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String filterRenderId = "participant-filter-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<style type="text/css" media="<%=isPrint ? "screen" : "print"%>">
    #headerpanel,
    div.labkey-app-bar,
    .discussion-toggle,
    .labkey-wp-title-left,
    .labkey-wp-title-right,
    .report-filter-window-outer,
    .report-config-panel,
    .report-toolbar
    {
        display: none;
    }

    td.lk-report-subjectid {
        padding-bottom  : 5px;
        font-weight     : bold;
        font-size       : 13pt;
        text-align      : left;
    }

    td.lk-report-cell {font-size: 11pt;}
    td.lk-report-column-header, th.lk-report-column-header {font-size: 12pt;}

    table.labkey-wp { border: none !important; }
    .x4-panel-body-default { border:none !important; }

</style>

<style type="text/css" media="screen">

    td.lk-report-subjectid {
        padding-bottom  : 10px;
        font-weight     : bold;
        font-size       : 1.3em;
        text-align      : left;
    }
</style>

<div id="<%=filterRenderId%>" class="report-filter-window-outer" style="position:<%=bean.isAllowOverflow() ? "absolute" : "absolute"%>;"></div>
<div id="<%=renderId%>"></div>
<script type="text/javascript">

    Ext4.onReady(function(){
        var pr = Ext4.create('LABKEY.ext4.ParticipantReport', {
            height          : 600,
            subjectColumn   : <%=q(org.labkey.api.study.StudyService.get().getSubjectColumnName(getContainer()))%>,
            subjectVisitColumn: <%=q(org.labkey.api.study.StudyService.get().getSubjectVisitColumnName(getContainer()))%>,
            subjectNoun     : {singular : <%=PageFlowUtil.jsString(s.getSubjectNounSingular())%>, plural : <%=PageFlowUtil.jsString(s.getSubjectNounPlural())%>, columnName: <%=PageFlowUtil.jsString(s.getSubjectColumnName())%>},
            visitBased      : <%=s.getTimepointType().isVisitBased()%>,
            renderTo        : '<%= renderId %>',
            filterDiv       : '<%=filterRenderId%>',
            id              : '<%= bean.getComponentId() %>',
            reportId        : <%=q(reportId)%>,
            allowCustomize  : true,
            allowShare      : <%=c.hasPermission(user, ShareReportPermission.class)%>,
            hideSave        : <%=user.isGuest()%>,
            fitted          : <%=bean.isExpanded()%>,
            openCustomize   : true,
            allowOverflow   : <%=bean.isAllowOverflow()%>,
            listeners : {
                render: function(p) {
                    p.panelWidthDiff = Ext4.getBody().getViewSize().width - p.getWidth();
                }
            }
        });

        var _resize = function(w, h) {
            if (pr && pr.doLayout && pr.panelWidthDiff) {
                var width = Ext4.getBody().getViewSize().width - pr.panelWidthDiff < 625 ? 625 : Ext4.getBody().getViewSize().width - pr.panelWidthDiff;
                pr.setWidth(width);
                pr.doLayout();
            }
        };

        Ext4.EventManager.onWindowResize(_resize);
    });

    /**
     * Global, Public
     * Provided for extenral calls to customize the participant report (e.g. from a webpart control)
     * @param elementId - The elementId of that the participant report is currently rendered to.
     */
    var customizeParticipantReport = function(elementId) {

        function initPanel() {
            var panel = Ext4.getCmp(elementId);

            if (panel) { panel.customize(); }
        }
        Ext4.onReady(initPanel);
    };

</script>

