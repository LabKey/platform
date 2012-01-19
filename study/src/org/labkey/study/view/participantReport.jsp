<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Report> me = (JspView<Report>) HttpView.currentView();
    Report bean = me.getModelBean();
    String reportId = "";

    if (bean != null)
        reportId = bean.getDescriptor().getReportId().toString();

    String elementId = "participant-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String customizeId = "participant-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<style type="text/css">
    .x4-reset .x4-border-layout-ct {
        background-color: white;
    }
</style>
<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("TemplateHelper.js");
    LABKEY.requiresScript("study/ParticipantReport.js");
</script>

<script type="text/javascript">

    Ext4.onReady(function(){
        var panel = Ext4.create('LABKEY.ext4.ParticipantReport', {
            height     : 1000, // TODO: figure out dynamic height
            renderTo   : '<%= elementId %>',
            <%--previewEl  : '<%= customizeId %>',--%>
//            reportId   : 'fake-report-id',
            allowCustomize : true,
            openCustomize : true
        });
    });

</script>

<div id="<%= elementId%>" class="extContainer" style="width:100%;"></div>
<div id="<%= customizeId %>" class="extContainer" style="width:100%;"></div>