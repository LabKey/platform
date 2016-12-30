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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
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
    JspView<ReturnUrlForm> me = (JspView<ReturnUrlForm>) HttpView.currentView();
    ReturnUrlForm bean = me.getModelBean();
    Container c = getContainer();

    // study products are editable at the project level for Dataspace projects
    boolean isDataspaceProject = c.getProject() != null && c.getProject().isDataspace() && !c.isDataspace();

    String returnUrl = bean.getReturnUrl() != null ? bean.getReturnUrl() : PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c).toString();
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        Ext4.create('LABKEY.VaccineDesign.StudyProductsPanel', {
            renderTo : 'study-products-panel',
            disableEdit : <%=isDataspaceProject%>,
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
                        text: 'Challenge Types',
                        href: LABKEY.ActionURL.buildURL('query', 'executeQuery', projectPath, {schemaName: 'study', 'query.queryName': 'StudyDesignChallengeTypes'}),
                        hrefTarget: '_blank'  // issue 19493
                    },{
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
                    },{
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
                    text: 'Challenge Types',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignChallengeTypes'}),
                    hrefTarget: '_blank'  // issue 19493
                },{
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
                },{
                    text: 'Routes',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'study', 'query.queryName': 'StudyDesignRoutes'}),
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

<%
if (isDataspaceProject)
{
    ActionURL projectManageProductsURL = new ActionURL(StudyDesignController.ManageStudyProductsAction.class, getContainer().getProject());
    projectManageProductsURL.addReturnURL(getActionURL());
%>
Vaccine design information is defined at the project level for Dataspace projects. The grids below are read-only.
<div style="width: 850px;">
    <ul>
        <li>
            Use the manage study products page at the project level to make changes to the information listed below.
            <%=textLink("Manage Study Products", projectManageProductsURL)%>
        </li>
<%
}
else
{
%>
Enter vaccine design information in the grids below.
<div style="width: 850px;">
    <ul>
        <li>Each immunogen, adjuvant and challenge in the study should be listed on one row of the grids below.</li>
        <li>Immunogens, adjuvants and challenges should have unique labels.</li>
        <li>If possible, the immunogen description should include specific sequences of HIV Antigens included in the immunogen.</li>
        <li>
            Use the manage treatments page to describe the schedule of treatments and combinations of study products administered at each timepoint.
            <%
                ActionURL manageTreatmentsURL = PageFlowUtil.urlProvider(StudyUrls.class).getManageTreatmentsURL(c, c.hasActiveModuleByName("viscstudies"));
                manageTreatmentsURL.addReturnURL(getActionURL());
            %>
            <%=textLink("Manage Treatments", manageTreatmentsURL)%>
        </li>
<%
}
%>
        <li>
            Configure dropdown options for challenge types, immunogen types, genes, subtypes and routes at the project level to be shared across study designs or within this folder for
            study specific properties: <span id='config-dropdown-menu'></span>
        </li>
    </ul>
</div>

<div id="study-products-panel"></div>
