<%
/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/vaccineDesign/vaccineDesign.lib.xml");
        dependencies.add("study/vaccineDesign/VaccineDesign.css");
    }
%>
<%
    User user = getUser();
    Container container = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(container);
%>

<style type="text/css">
    .study-vaccine-design tr.header-row td {
        background-color: #<%= WebThemeManager.getTheme(container).getGridColor() %> !important;
    }
</style>

<%
    if (study != null)
    {
        %>This section describes the study products evaluated in the study.</br><%
        if (container.hasPermission(user, UpdatePermission.class))
        {
            ActionURL editUrl = new ActionURL(StudyDesignController.ManageStudyProductsAction.class, getContainer());
            editUrl.addReturnURL(getActionURL());
%>
            <%=textLink("Manage Study Products", editUrl)%><br/>
<%
        }
%>
        <br/>
        <div id="immunogens-grid"></div>
        <br/>
        <div id="adjuvants-grid"></div>
        <br/>
        <div id="challenges-grid"></div>
<%
    }
    else
    {
%>
        <p>The folder must contain a study in order to display a vaccine design.</p>
<%
    }
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        Ext4.create('LABKEY.VaccineDesign.ImmunogensGrid', {
            renderTo : 'immunogens-grid',
            showDoseRoute: false,
            disableEdit : true
        });

        Ext4.create('LABKEY.VaccineDesign.AdjuvantsGrid', {
            renderTo : 'adjuvants-grid',
            showDoseRoute: false,
            disableEdit : true
        });

        Ext4.create('LABKEY.VaccineDesign.ChallengesGrid', {
            renderTo : 'challenges-grid',
            showDoseRoute: false,
            disableEdit : true
        });
    });
</script>