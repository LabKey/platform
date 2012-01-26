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

<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);
    String visitLabel = StudyManager.getInstance().getVisitManager(study).getPluralLabel();
    Portal.WebPart webpart = me.getModelBean();
    int webPartIndex = (webpart == null ? 0 : webpart.getIndex());
%>

<div>
    <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
    <%= textLink("Manage " + visitLabel, StudyController.ManageVisitsAction.class) %>
    <% } %>
    <%= textLink("Manage Datasets", StudyController.ManageTypesAction.class) %>
    <br />
</div>

<div id='study-schedule-<%=webPartIndex%>' class="study-schedule-container"></div>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresCss("study/StudySchedule.css");
    LABKEY.requiresScript("study/StudyScheduleGrid.js");
</script>

<script type="text/javascript">
    function callRender() {

        var studySchedulePanel = Ext4.create('LABKEY.ext4.StudyScheduleGrid', {
            renderTo : "study-schedule-"+ <%=webPartIndex%>
        });

    }
    Ext4.onReady(callRender);
</script>