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
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/vaccineDesign/vaccineDesign.lib.xml");
        dependencies.add("study/vaccineDesign/VaccineDesign.css");
    }
%>
<%
    JspView<StudyDesignController.AssayScheduleForm> me = (JspView<StudyDesignController.AssayScheduleForm>) HttpView.currentView();
    StudyDesignController.AssayScheduleForm form = me.getModelBean();

    Container c = getContainer();
    Study study = StudyManager.getInstance().getStudy(getContainer());

    // assay schedule is editable for the individual studies in a Dataspace project
    boolean disableEdit = c.isProject() && c.isDataspace();

    String visitNoun = "Visit";
    if (study != null && study.getTimepointType() == TimepointType.DATE)
        visitNoun = "Timepoint";

    String returnUrl = form.getReturnUrl() != null ? form.getReturnUrl() : PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c).toString();
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        Ext4.create('LABKEY.VaccineDesign.AssaySchedulePanel', {
            renderTo : 'assay-configurations-panel',
            disableEdit : <%=disableEdit%>,
            visitNoun : <%=q(visitNoun)%>,
            useAlternateLookupFields : <%=form.isUseAlternateLookupFields()%>,
            returnURL : <%=q(returnUrl)%>
        });

        var projectMenu = null;
        if (LABKEY.container.type != "project")
        {
            var projectPath = LABKEY.container.path.substring(0, LABKEY.container.path.indexOf("/", 1));
            projectMenu = {
                text: 'Project',
                menu: {
                    items: [{
                        text: 'Assays',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignAssays'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
                        text: 'Labs',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignLabs'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
                        text: 'Sample Types',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignSampleTypes'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
                        text: 'Units',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignUnits'}),
                        hrefTarget: '_blank'  // issue 19493
                    }]
                }
            };
        }

        var folderMenu = {
            text: 'Folder',
            menu: {
                items: [{
                    text: 'Assays',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignAssays'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'Labs',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignLabs'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'Sample Types',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignSampleTypes'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
                    text: 'Units',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignUnits'}),
                    hrefTarget: '_blank'  // issue 19493
                }]
            }
        };

        Ext4.create('Ext.button.Button', {
            text: 'Configure',
            renderTo: 'config-dropdown-menu',
            menu: projectMenu ? {items: [projectMenu, folderMenu]} : folderMenu.menu
        });
    });
</script>

Enter assay schedule information in the grids below.
<div style="width: 900px;">
    <ul>
        <li <%=form.isUseAlternateLookupFields() ? "style='display:none;'" : ""%>>
            Configure dropdown options for assays, labs, sample types, and units at the project
            level to be shared across study designs or within this folder for
            study specific properties: <span id='config-dropdown-menu'></span>
        </li>
        <li>
            Select the <%=h(visitNoun.toLowerCase())%>s for each assay in the schedule
            portion of the grid to define the expected assay schedule for the study.
        </li>
        <li <%=!form.isUseAlternateLookupFields() ? "style='display:none;'" : ""%>>
            Use the manage locationss page to further configure information about the locations for this study.
            <%= textLink("Manage Locations", StudyController.ManageLocationsAction.class) %>
        </li>
        <li>
            Use the manage <%=h(visitNoun.toLowerCase())%>s page to further configure
            information about the <%=h(visitNoun.toLowerCase())%>s for this study or to change
            the <%=h(visitNoun.toLowerCase())%> display order.
            <%=textLink("Manage " + visitNoun + "s", StudyController.ManageVisitsAction.class)%>
        </li>
    </ul>
</div>
<div id="assay-configurations-panel" class="table-responsive"></div>
<br/>
