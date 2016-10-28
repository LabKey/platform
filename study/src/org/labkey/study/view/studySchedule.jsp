<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("dataviews");
        dependencies.add("study/StudySchedule.css");
        dependencies.add("study/StudyScheduleGrid.js");
    }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    Portal.WebPart webpart = me.getModelBean();
    Container c = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);
    User user = getUser();
    boolean canEdit  = c.hasPermission(user, AdminPermission.class);
    int webPartIndex = (webpart == null ? 0 : webpart.getIndex());
    boolean nullStudy = (study == null);
    String timepointType = null;
    if (!nullStudy)
    {
        timepointType = study.getTimepointType().toString();
    }
%>
<%
    if (!nullStudy)
    {
%>
        <div id='study-schedule-<%=webPartIndex%>' class="study-schedule-container"></div>
        <script type="text/javascript">

            Ext4.onReady(function(){
                Ext4.create('LABKEY.ext4.StudyScheduleGrid', {
                    renderTo      : "study-schedule-"+ <%=webPartIndex%>,
                    timepointType : "<%=timepointType%>",
                    canEdit       : <%=canEdit%>
                });
            });
        </script>
<%
    }
    else
    {
%>
        <div id='study-schedule-<%=webPartIndex%>' class="study-schedule-container">
            <p>The folder must contain a study in order to display a study schedule.</p>
        </div>
<%
    }
%>
