<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.compliance.ComplianceService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.PHI" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.specimen.SpecimenManager" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyManagementOption" %>
<%@ page import="org.labkey.api.study.StudyReloadSource" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.CohortController.ManageCohortsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ConfigureMasterPatientSettingsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DeleteStudyAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DemoModeAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageAlternateIdsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageExternalReloadAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageLocationsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageParticipantCategoriesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageQCStatesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyPropertiesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageTypesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.SnapshotSettingsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.StudyScheduleAction" %>
<%@ page import="org.labkey.study.controllers.StudyDefinitionController.EditStudyDefinitionAction" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController.ManageStudyProductsAction" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController.BeginAction" %>
<%@ page import="org.labkey.study.model.ParticipantCategoryImpl" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.StudySnapshot" %>
<%@ page import="org.labkey.study.view.specimen.ManageSpecimenView" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("reports/rowExpander.js");
        dependencies.add("FileUploadField.js");
        dependencies.add("study/StudyWizard.js");
    }
%>

<style type="text/css">
    .lk-study-prop-label {
        width: 200px;
        /*font-weight: bold;*/
    }

    .lk-study-prop-desc {
        width: 450px;
    }

    .publish-radio-option .x-column {
        padding-left: 1px;
    }
</style>

<%
    StudyImpl study = getStudy();
    Container c = getContainer();

    Study sharedStudy = StudyManager.getInstance().getSharedStudyOrCurrent(study);
    boolean sharedVisits = sharedStudy != null && sharedStudy.getShareVisitDefinitions();

    String visitLabel = StudyManager.getInstance().getVisitManager(study).getPluralLabel();
    ActionURL manageCohortsURL = urlFor(ManageCohortsAction.class);
    User user = getUser();
    int numProperties = study.getNumExtendedProperties(user);
    String propString = numProperties == 1 ? "property" : "properties";

    String subjectNounSingle = StudyService.get().getSubjectNounSingular(c);
    List<ParticipantGroup> groups = new LinkedList<>();

    for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(c, user))
    {
        groups.addAll(ParticipantGroupManager.getInstance().getParticipantGroups(c, user, category));
    }

    String availableStudyName = ContainerManager.getAvailableChildContainerName(c, "New Study");

    int numDatasets = study.getDatasetsByType(Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER).size();
    Collection<StudyReloadSource> reloadSources = StudyService.get().getStudyReloadSources(getContainer());

    ComplianceService complianceService = ComplianceService.get();
    String maxAllowedPhi = complianceService.getMaxAllowedPhi(c, getUser()).name();

    if (study.hasSourceStudy() || study.isSnapshotStudy())
    {
        String snapshotTitle = study.getStudySnapshotType().getTitle().toLowerCase();
        snapshotTitle = ("ancillary".equals(snapshotTitle) ? "an " : "a ") + snapshotTitle;
%>
        <p>This is <%=h(snapshotTitle)%> study.</p>
<%
        if (c.hasPermission(user, AdminPermission.class))
        {
%>
            <%= button("View Settings").href(urlFor(SnapshotSettingsAction.class)) %>
<%
        }

        StudySnapshot snapshot = StudyManager.getInstance().getStudySnapshot(study.getStudySnapshot());
        assert null != snapshot;
        Container parent = null==snapshot.getSource() ? null : ContainerManager.getForId(snapshot.getSource());

        // Display a button if user has read permissions there.
        if (null != parent && parent.hasPermission(user, ReadPermission.class))
        {
%>
            <%= button("Visit Source Study").href(urlProvider(ProjectUrls.class).getBeginURL(parent)) %>
<%
        }
%>
        <br/><br/>
<%
    }

    if (c.hasPermission(user, AdminPermission.class))
    {
%>
            <labkey:panel title="General Study Settings">
                <table class="lk-fields-table lk-manage-study-table">
                    <tr>
                        <td class="lk-study-prop-label">Study Properties</td>
                        <td class="lk-study-prop-desc">Study label, investigator, grant, description, etc.</td>
                        <td><%= link("Change Study Properties", ManageStudyPropertiesAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Additional Properties</td>
                        <td class="lk-study-prop-desc">This study has <%=numProperties%> additional <%=h(propString)%></td>
                        <td><%
                            Container p = c.getProject();
                            if (p.hasPermission(user, AdminPermission.class))
                            {
                                ActionURL editDefinition = new ActionURL(EditStudyDefinitionAction.class, p)
                                    .addReturnURL(getActionURL());
                                %><%=link("Edit Additional Properties", editDefinition).usePost()%><%
                            }
                            else
                            {
                                %>&nbsp;<%
                            }
                        %></td>
                    </tr>
                    <%
                        if (!reloadSources.isEmpty())
                        {
                    %>
                    <tr>
                        <td class="lk-study-prop-label">Reloading</td>
                        <td class="lk-study-prop-desc">Manage reloading from external repositories</td>
                        <td><%= link("Manage External Reloading", ManageExternalReloadAction.class) %></td>
                    </tr>
                    <%
                        }
                    %>

                    <tr>
                        <td class="lk-study-prop-label">Datasets</td>
                        <td class="lk-study-prop-desc">This study defines <%= numDatasets %> datasets</td>
                        <td><%= link("Manage Datasets", ManageTypesAction.class) %></td>
                    </tr>
                    <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
                    <tr>
                        <td class="lk-study-prop-label"><%= h(visitLabel) %></td>
                        <td class="lk-study-prop-desc">This study defines <%= getVisits(Visit.Order.DISPLAY).size()%> <%=h(visitLabel.toLowerCase())%>
                            <% if (sharedVisits) { %>(shared)<% } %>
                        </td>
                        <td><%= link("Manage " + (sharedVisits ? "shared " : "") + visitLabel, ManageVisitsAction.class) %></td>
                    </tr>
                    <% } %>
                     <tr>
                        <td class="lk-study-prop-label">Study Schedule</td>
                         <td class="lk-study-prop-desc">This study defines <%= numDatasets %> datasets
                             <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
                             and <%= getVisits(Visit.Order.DISPLAY).size() %> <%=h(visitLabel.toLowerCase())%>
                             <% } %>
                         </td>
                        <td><%= link("Study Schedule", StudyScheduleAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Locations</td>
                        <td class="lk-study-prop-desc">This study references <%= getLocations().size() %> locations (labs/sites/repositories)</td>
                        <td><%= link("Manage Locations", ManageLocationsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Cohorts</td>
                        <td class="lk-study-prop-desc">This study defines <%= getCohorts(getUser()).size() %> cohorts</td>
                        <td><%= link("Manage Cohorts", manageCohortsURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label"><%= h(subjectNounSingle) %> Groups</td>
                        <td class="lk-study-prop-desc">This study defines <%=groups.size()%> <%= h(subjectNounSingle.toLowerCase()) %> groups</td>
                        <td><%= link("Manage " + subjectNounSingle + " Groups", ManageParticipantCategoriesAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Alternate <%= h(subjectNounSingle) %> IDs</td>
                        <td class="lk-study-prop-desc">Configure how alternate <%= h(subjectNounSingle.toLowerCase()) %> ids and aliases are generated</td>
                        <td><%= link("Manage Alternate " + subjectNounSingle + " IDs and Aliases", ManageAlternateIdsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Security</td>
                        <td class="lk-study-prop-desc">Manage access to study datasets</td>
                        <td><%= link("Manage Security", BeginAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Reports/Views</td>
                        <td class="lk-study-prop-desc">Manage views for this study</td>
                        <td><%=link("Manage Views", urlProvider(ReportUrls.class).urlManageViews(c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Quality Control States</td>
                        <td class="lk-study-prop-desc">Manage QC states for datasets in this study</td>
                        <td><%=link("Manage Dataset QC States", ManageQCStatesAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Study Products</td>
                        <td class="lk-study-prop-desc">This study defines <%= getStudyProducts(user, null).size() %> study products</td>
                        <%
                            ActionURL manageStudyProductsURL = urlFor(ManageStudyProductsAction.class)
                                .addReturnURL(getActionURL());
                        %>
                        <td><%= link("Manage Study Products", manageStudyProductsURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Treatments</td>
                        <td class="lk-study-prop-desc">This study defines <%= getStudyTreatments(user).size() %> treatments</td>
                        <%
                            ActionURL manageTreatmentsURL = urlProvider(StudyUrls.class).getManageTreatmentsURL(getContainer(), false)
                                .addReturnURL(getActionURL());
                        %>
                        <td><%= link("Manage Treatments", manageTreatmentsURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Assay Schedule</td>
                        <td class="lk-study-prop-desc">This study defines <%= getAssaySpecimenConfigs().size() %> assay configurations</td>
                        <%
                            boolean hasRhoModule = getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule("rho"));
                            ActionURL assayScheduleURL = urlProvider(StudyUrls.class).getManageAssayScheduleURL(getContainer(), hasRhoModule)
                                .addReturnURL(getActionURL());
                        %>
                        <td><%= link("Manage Assay Schedule", assayScheduleURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Demo Mode</td>
                        <td class="lk-study-prop-desc">Demo mode obscures <%=h(subjectNounSingle.toLowerCase())%> IDs on many pages</td>
                        <td><%=link("Demo Mode", DemoModeAction.class) %></td>
                    </tr>
                    <%
                        for (StudyManagementOption option : StudyService.get().getManagementOptions())
                        {
                            if (c.hasPermission(user, option.getPermission()))
                            {
                                option.setContainer(getContainer());
                    %>
                                <tr>
                                    <td class="lk-study-prop-label"><%=h(option.getTitle())%></td>
                                    <td class="lk-study-prop-desc"><%=h(option.getDescription())%></td>
                                    <td><%=link(option.getLinkText(), option.getLinkUrl())%></td>
                                </tr>
                    <%
                            }
                        }
                    %>
                    <tr>
                        <td class="lk-study-prop-label">Master Patient Index</td>
                        <td class="lk-study-prop-desc">Configure the Master Patient Index settings for this folder</td>
                        <td><%= link("Master Patient Index", ConfigureMasterPatientSettingsAction.class) %></td>
                    </tr>
                </table>
            </labkey:panel>

<%
    } // admin permission

    if (SpecimenManager.get().isSpecimenModuleActive(c))
        include(new ManageSpecimenView(), out);

    if (study.allowExport(getUser()))
    {
%>
        <%= button("Export Study").href(urlProvider(AdminUrls.class).getExportFolderURL(c).addParameter("exportType", "study")) %>
<%
    }

    if (c.hasPermission(user, AdminPermission.class) && !c.isDataspace())
    {
%>
        <%= button("Reload Study").href(urlProvider(AdminUrls.class).getImportFolderURL(c).addParameter("origin", "Reload")) %>
        <%= button("Delete Study").href(urlFor(DeleteStudyAction.class)) %>
<%
    }

    if (study.allowExport(getUser()))
    {
%>
        <%= button("Create Ancillary Study").href("javascript:void(0)").onClick("showCreateStudyWizard('ancillary')") %>
        <%= button("Publish Study").href("javascript:void(0)").onClick("showCreateStudyWizard('publish')") %>
<%
    }
%>
<script type="text/javascript">
    function showCreateStudyWizard(mode)
    {
        LABKEY.study.openCreateStudyWizard(mode, <%=q(availableStudyName)%>, <%=q(maxAllowedPhi)%>);
    }
</script>
