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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
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
    boolean isDataspaceStudy = c.getProject() != null && c.getProject().isDataspace() && !c.isDataspace();
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
        var immunogensGrid = Ext4.create('LABKEY.ext4.ImmunogensGrid', {
            renderTo : "immunogens-grid",
            disableEdit : <%=isDataspaceStudy%>
        });

        var adjuvantsGrid = Ext4.create('LABKEY.ext4.AdjuvantsGrid', {
            renderTo : "adjuvants-grid",
            disableEdit : <%=isDataspaceStudy%>
        });

        var projectMenu = null;
        if (LABKEY.container.type != "project")
        {
            var projectPath = LABKEY.container.path.substring(0, LABKEY.container.path.indexOf("/", 1));
            projectMenu = {
                text: 'Project',
                menu: {
                    items: [{
                        text: 'Immunogen Types',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignImmunogenTypes'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
                        text: 'Genes',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignGenes'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
                        text: 'SubTypes',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignSubTypes'}),
                        hrefTarget: '_blank'  // issue 19493
                    }]
                }
            };
        }

        var folderMenu = {
            text: 'Folder',
            menu: {
                items: [{
                    text: 'Immunogen Types',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignImmunogenTypes'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'Genes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignGenes'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'SubTypes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignSubTypes'}),
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

Enter vaccine design information in the grids below.
<div style="width: 810px;">
    <ul>
        <li>
            Configure dropdown options for immunogen types, genes, and subtypes at the project level to be shared across study designs or within this folder for
            study specific properties: <span id='config-dropdown-menu'></span>
        </li>
        <li>Each immunogen and adjuvant in the study should be listed on one row of the grids below.</li>
        <li>Immunogens and adjuvants should have unique names.</li>
        <li>If possible, the immunogen description should include specific sequences of HIV Antigens included in the immunogen.</li>
        <li>Use the manage treatments page to describe the schedule of treatments and combinations of immunogens and adjuvants administered at each timepoint.</li>
    </ul>
</div>
<div id="immunogens-grid"></div>
<span style='font-style: italic; font-size: smaller; display: <%=h(isDataspaceStudy ? "none" : "inline")%>;'>* Double click a row to edit the label and type, double click the HIV Antigens cell to edit them separately</span>
<br/><br/>
<div id="adjuvants-grid"></div>
<span style='font-style: italic; font-size: smaller; display: <%=h(isDataspaceStudy ? "none" : "inline")%>;'>* Double click a row to edit the label</span>
<br/><br/>
<%=textLink("Manage Treatments", StudyDesignController.ManageTreatmentsAction.class)%>

