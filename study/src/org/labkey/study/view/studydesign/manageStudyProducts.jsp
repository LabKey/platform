<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.study.view.studydesign.StudyDesignConfigureMenuItem" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
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
            study specific properties:
            <div style="display: inline" class="dropdown">
                <button data-toggle="dropdown" class="btn btn-default">Configure <i class="fa fa-caret-down"></i></button>
                <ul class="dropdown-menu dropdown-menu-right">
                    <%
                        NavTree folderTree = new NavTree("Folder");
                        folderTree.addChild(new StudyDesignConfigureMenuItem("Challenge Types", "study", "StudyDesignChallengeTypes", getContainer()));
                        folderTree.addChild(new StudyDesignConfigureMenuItem("Immunogen Types", "study", "StudyDesignImmunogenTypes", getContainer()));
                        folderTree.addChild(new StudyDesignConfigureMenuItem("Genes", "study", "StudyDesignGenes", getContainer()));
                        folderTree.addChild(new StudyDesignConfigureMenuItem("SubTypes", "study", "StudyDesignSubTypes", getContainer()));
                        folderTree.addChild(new StudyDesignConfigureMenuItem("Routes", "study", "StudyDesignRoutes", getContainer()));

                        if (!getContainer().isProject())
                        {
                            NavTree projectTree = new NavTree("Project");
                            projectTree.addChild(new StudyDesignConfigureMenuItem("Challenge Types", "study", "StudyDesignChallengeTypes", getContainer().getProject()));
                            projectTree.addChild(new StudyDesignConfigureMenuItem("Immunogen Types", "study", "StudyDesignImmunogenTypes", getContainer().getProject()));
                            projectTree.addChild(new StudyDesignConfigureMenuItem("Genes", "study", "StudyDesignGenes", getContainer().getProject()));
                            projectTree.addChild(new StudyDesignConfigureMenuItem("SubTypes", "study", "StudyDesignSubTypes", getContainer().getProject()));
                            projectTree.addChild(new StudyDesignConfigureMenuItem("Routes", "study", "StudyDesignRoutes", getContainer().getProject()));

                            NavTree navTree = new NavTree();
                            navTree.addChild(projectTree);
                            navTree.addChild(folderTree);
                            PopupMenuView.renderTree(navTree, out);
                        }
                        else
                        {
                            PopupMenuView.renderTree(folderTree, out);
                        }
                    %>
                </ul>
            </div>
        </li>
    </ul>
</div>

<div id="study-products-panel"></div>
