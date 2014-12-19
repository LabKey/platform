<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.security.permissions.ManageStudyPermission" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4ClientApi"));
        resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.js"));
        resources.add(ClientDependency.fromPath("dataview/DataViewsPanel.css"));
        resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>
<%
    Container c = getContainer();
    User user = getUser();
    ActionURL returnURL = getActionURL();

    Study study = StudyManager.getInstance().getStudy(c);
    boolean canManageStudy = c.hasPermission(user, ManageStudyPermission.class);
    boolean isDataspace = c.isProject() && c.isDataspace();

    String visitDisplayName = "Visit";
    if (study != null && study.getTimepointType() == TimepointType.DATE)
        visitDisplayName = "Timepoint";

    String subjectName = "Subject";
    if (study != null)
        subjectName = study.getSubjectNounSingular();
%>

<style>
    .x4-panel-header-default
    {
        background-color: transparent;
        background-image: none !important;
        border: none;
    }
    .x4-panel-header-text-container-default
    {
        font-size: 15px;
        color: black;
    }

    .x4-grid-cell-inner
    {
        white-space: normal;
    }
</style>

<script type="text/javascript">
    Ext4.onReady(function(){
        var treatmentsGrid = Ext4.create('LABKEY.ext4.TreatmentsGrid', {
            renderTo : "treatments-grid",
            disableEdit : <%=isDataspace%>,
            listeners: {
                treatmentsAddedOrRemoved: function() {
                    treatmentScheduleGrid.getTreatmentScheduleData(false);
                }
            }
        });

        var treatmentScheduleGrid = Ext4.create('LABKEY.ext4.TreatmentScheduleGrid', {
            renderTo : "treatment-schedule-grid",
            disableEdit : <%=isDataspace%>,
            visitNoun : <%=q(visitDisplayName)%>,
            subjectNoun : <%=q(subjectName)%>
        });

        var projectMenu = null;
        if (LABKEY.container.type != "project")
        {
            var projectPath = LABKEY.container.path.substring(0, LABKEY.container.path.indexOf("/", 1));
            projectMenu = {
                text: 'Project',
                menu: {
                    items: [{
                        text: 'Routes',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignRoutes'}),
                        hrefTarget: '_blank'  // issue 19493
                    }]
                }
            };
        }

        var folderMenu = {
            text: 'Folder',
            menu: {
                items: [{
                    text: 'Routes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignRoutes'}),
                    hrefTarget: '_blank'  // issue 19493
                }]
            }
        };

        var menu = Ext4.create('Ext.button.Button', {
            text: 'Configure',
            renderTo: 'config-dropdown-menu',
            menu: projectMenu ? {items: [projectMenu, folderMenu]} : folderMenu.menu
        });
    });
</script>

Enter treatment information in the grids below.
<div style="width: 700px;">
    <ul>
        <li>
            Configure dropdown options for routes at the project level to be shared across study designs or within this folder for
            study specific properties: <span id='config-dropdown-menu'></span>
        </li>
        <li>Use the "Insert New" button in the treatments grid to add a new study treatment.</li>
        <li>Each treatment may consist of several study products, i.e. immunogens and/or adjuvants.</li>
        <li>Use the "Insert New" button in the treatment schedule grid to add a new study cohort.</li>
        <li>Enter the number of subjects for the cohort in the count column.</li>
    </ul>
</div>
<div id="treatments-grid"></div>
<div style='font-style: italic; font-size: smaller; display: <%=h(isDataspace ? "none" : "inline")%>;'>* Double click to edit a treatment record and its product definition</div>
<br/>
<%=textLink("Manage Study Products", StudyDesignController.ManageStudyProductsAction.class)%>
<br/><br/>
<div id="treatment-schedule-grid"></div>
<div style='font-style: italic; font-size: smaller; display: <%=h(isDataspace ? "none" : "inline")%>;'>* Double click to edit a group/cohort and its treatment/visit map definition</div>
<br/>
<%
    if (canManageStudy)
    {
        if (study != null && study.getTimepointType() == TimepointType.VISIT && study.getVisits(Visit.Order.DISPLAY).size() > 1)
        {
            %><%= textLink("Change Visit Order", new ActionURL(StudyController.VisitOrderAction.class, c).addReturnURL(returnURL)) %><%
        }
%>
        <%=textLink("Manage " + visitDisplayName + "s", StudyController.ManageVisitsAction.class)%>
        <%=textLink("Manage Cohorts", CohortController.ManageCohortsAction.class)%>
<%
    }
%>