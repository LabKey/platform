<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.SpecimenTransform" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyReloadSource" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageReloadAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyPropertiesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageTypesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyDefinitionController" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.importer.StudyReload" %>
<%@ page import="org.labkey.study.model.ParticipantCategoryImpl" %>
<%@ page import="org.labkey.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.StudySnapshot" %>
<%@ page import="org.labkey.study.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.compliance.ComplianceService" %>
<%@ page import="org.labkey.api.data.PHI" %>
<%@ page import="org.labkey.api.study.StudyManagementOption" %>
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
    Boolean sharedVisits = sharedStudy != null && sharedStudy.getShareVisitDefinitions();

    String visitLabel = StudyManager.getInstance().getVisitManager(study).getPluralLabel();
    ActionURL manageCohortsURL = new ActionURL(CohortController.ManageCohortsAction.class, c);
    User user = getUser();
    int numProperties = study.getNumExtendedProperties(user);
    String propString = numProperties == 1 ? "property" : "properties";

    StudyReload.ReloadInterval currentInterval = StudyReload.ReloadInterval.getForSeconds(study.getReloadInterval());
    String intervalLabel;

    String subjectNounSingle = StudyService.get().getSubjectNounSingular(c);
    List<ParticipantGroup> groups = new LinkedList<>();

    for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(c, user))
    {
        groups.addAll(ParticipantGroupManager.getInstance().getParticipantGroups(c, user, category));
    }

    if (!study.isAllowReload())
        intervalLabel = "This study is set to not reload";
    else if (null == study.getReloadInterval() || 0 == study.getReloadInterval())
        intervalLabel = "This study is set for manual reloading";
    else
        intervalLabel = "This study is scheduled to check for reload " + (StudyReload.ReloadInterval.Never != currentInterval ? currentInterval.getDescription() : "every " + study.getReloadInterval() + " seconds");

    String availableStudyName = ContainerManager.getAvailableChildContainerName(c, "New Study");

    int numDatasets = study.getDatasetsByType(Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER).size();
    Collection<StudyReloadSource> reloadSources = StudyService.get().getStudyReloadSources(getContainer());

    ComplianceService complianceService = ComplianceService.get();
    String maxAllowedPhi = (null != complianceService ? complianceService.getMaxAllowedPhi(c, getUser()).name() : PHI.Restricted.name());

    if (study.hasSourceStudy() || study.isSnapshotStudy())
    {
        String snapshotTitle = study.getStudySnapshotType().getTitle().toLowerCase();
        snapshotTitle = ("ancillary".equals(snapshotTitle) ? "an " : "a ") + snapshotTitle;
%>
        <p>This is <%=text(snapshotTitle)%> study.</p>
<%
        if (c.hasPermission(user, AdminPermission.class))
        {
%>
            <%= button("View Settings").href(StudyController.SnapshotSettingsAction.class, c) %>
<%
        }

        StudySnapshot snapshot = StudyManager.getInstance().getRefreshStudySnapshot(study.getStudySnapshot());
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
                        <td><%= textLink("Change Study Properties", ManageStudyPropertiesAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Additional Properties</td>
                        <td class="lk-study-prop-desc">This study has <%=numProperties%> additional <%=h(propString)%></td>
                        <td><%
                            Container p = c.getProject();
                            if (p.hasPermission(user, AdminPermission.class))
                            {
                                ActionURL returnURL = getActionURL();
                                ActionURL editDefinition = new ActionURL(StudyDefinitionController.EditStudyDefinitionAction.class, p);
                                editDefinition.addReturnURL(returnURL);
                                %><%= textLink("Edit Additional Properties", editDefinition) %><%
                            }
                            else
                            {
                                %>&nbsp;<%
                            }
                        %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Reloading</td>
                        <td class="lk-study-prop-desc"><%= h(intervalLabel) %></td>
                        <td><%= textLink("Manage Reloading", ManageReloadAction.class) %></td>
                    </tr>
                    <%
                        if (!reloadSources.isEmpty())
                        {
                    %>
                    <tr>
                        <td class="lk-study-prop-label">Reloading</td>
                        <td class="lk-study-prop-desc">Manage reloading from external repositories</td>
                        <td><%= textLink("Manage External Reloading", StudyController.ManageExternalReloadAction.class) %></td>
                    </tr>
                    <%
                        }
                    %>

                    <tr>
                        <td class="lk-study-prop-label">Datasets</td>
                        <td class="lk-study-prop-desc">This study defines <%= numDatasets %> datasets</td>
                        <td><%= textLink("Manage Datasets", ManageTypesAction.class) %></td>
                    </tr>
                    <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
                    <tr>
                        <td class="lk-study-prop-label"><%= h(visitLabel) %></td>
                        <td class="lk-study-prop-desc">This study defines <%= getVisits(Visit.Order.DISPLAY).size()%> <%=h(visitLabel.toLowerCase())%>
                            <% if (sharedVisits) { %>(shared)<% } %>
                        </td>
                        <td><%= textLink("Manage " + (sharedVisits ? "shared " : "") + visitLabel, ManageVisitsAction.class) %></td>
                    </tr>
                    <% } %>
                     <tr>
                        <td class="lk-study-prop-label">Study Schedule</td>
                         <td class="lk-study-prop-desc">This study defines <%= numDatasets %> datasets
                             <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
                             and <%= getVisits(Visit.Order.DISPLAY).size() %> <%=h(visitLabel.toLowerCase())%>
                             <% } %>
                         </td>
                        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Locations</td>
                        <td class="lk-study-prop-desc">This study references <%= getLocations().size() %> locations (labs/sites/repositories)</td>
                        <td><%= textLink("Manage Locations", StudyController.ManageLocationsAction.class) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Location Types</td>
                        <td class="lk-study-prop-desc">Configure which location types are allowed to be requesting locations</td>
                        <td><%= textLink("Manage Location Types", StudyController.ManageLocationTypesAction.class) %></td>
                    </tr>

                    <tr>
                        <td class="lk-study-prop-label">Cohorts</td>
                        <td class="lk-study-prop-desc">This study defines <%= getCohorts(getUser()).size() %> cohorts</td>
                        <td><%= textLink("Manage Cohorts", manageCohortsURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label"><%= h(subjectNounSingle) %> Groups</td>
                        <td class="lk-study-prop-desc">This study defines <%=groups.size()%> <%= h(subjectNounSingle.toLowerCase()) %> groups</td>
                        <td><%= textLink("Manage " + subjectNounSingle + " Groups", new ActionURL(StudyController.ManageParticipantCategoriesAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Alternate <%= h(subjectNounSingle) %> IDs</td>
                        <td class="lk-study-prop-desc">Configure how alternate <%= h(subjectNounSingle.toLowerCase()) %> ids and aliases are generated</td>
                        <td><%= textLink("Manage Alternate " + subjectNounSingle + " IDs and Aliases", new ActionURL(StudyController.ManageAlternateIdsAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Security</td>
                        <td class="lk-study-prop-desc">Manage access to study datasets and samples</td>
                        <% ActionURL securityUrl = new ActionURL(SecurityController.BeginAction.class, c);%>
                        <td><%= textLink("Manage Security", securityUrl) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Reports/Views</td>
                        <td class="lk-study-prop-desc">Manage views for this Study</td>
                        <td><%=textLink("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Quality Control States</td>
                        <td class="lk-study-prop-desc">Manage QC states for datasets in this study</td>
                        <td><%=textLink("Manage Dataset QC States", new ActionURL(StudyController.ManageQCStatesAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Comments</td>
                        <td class="lk-study-prop-desc">Manage <%= h(subjectNounSingle.toLowerCase()) %> and  <%= h(subjectNounSingle.toLowerCase()) %>/visit comments</td>
                        <td><%= textLink("Manage Comments",
                                new ActionURL(SpecimenController.ManageSpecimenCommentsAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Study Products</td>
                        <td class="lk-study-prop-desc">This study defines <%= getStudyProducts(user, null).size() %> study products</td>
                        <%
                            ActionURL manageStudyProductsURL = new ActionURL(StudyDesignController.ManageStudyProductsAction.class, getContainer());
                            manageStudyProductsURL.addReturnURL(getActionURL());
                        %>
                        <td><%= textLink("Manage Study Products", manageStudyProductsURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Treatments</td>
                        <td class="lk-study-prop-desc">This study defines <%= getStudyTreatments(user).size() %> treatments</td>
                        <%
                            ActionURL manageTreatmentsURL = PageFlowUtil.urlProvider(StudyUrls.class).getManageTreatmentsURL(getContainer(), false);
                            manageTreatmentsURL.addReturnURL(getActionURL());
                        %>
                        <td><%= textLink("Manage Treatments", manageTreatmentsURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Assay Schedule</td>
                        <td class="lk-study-prop-desc">This study defines <%= getAssaySpecimenConfigs().size() %> assay configurations</td>
                        <%
                            boolean hasRhoModule = getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule("rho"));
                            ActionURL assayScheduleURL = PageFlowUtil.urlProvider(StudyUrls.class).getManageAssayScheduleURL(getContainer(), hasRhoModule);
                            assayScheduleURL.addReturnURL(getActionURL());
                        %>
                        <td><%= textLink("Manage Assay Schedule", assayScheduleURL) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Demo Mode</td>
                        <td class="lk-study-prop-desc">Demo mode obscures <%=h(subjectNounSingle.toLowerCase())%> IDs on many pages</td>
                        <td><%=textLink("Demo Mode",
                                new ActionURL(StudyController.DemoModeAction.class, c)) %></td>
                    </tr>
                    <%
                        for (StudyManagementOption option : StudyService.get().getManagementOptions())
                        {
                            option.setContainer(getContainer());
                    %>
                            <tr>
                                <td class="lk-study-prop-label"><%=h(option.getTitle())%></td>
                                <td class="lk-study-prop-desc"><%=h(option.getDescription())%></td>
                                <td><%=textLink(option.getLinkText(), option.getLinkUrl())%></td>
                            </tr>
                    <%
                        }
                    %>
                </table>
            </labkey:panel>

<%
        if (!study.hasSourceStudy() && !study.isSnapshotStudy())
        {
%>
            <labkey:panel title="Specimen Repository Settings">
                <table class="lk-fields-table">
                    <tr>
                        <td class="lk-study-prop-label">Repository Type</td>
                        <td class="lk-study-prop-desc">This study uses the <%=text(study.getRepositorySettings().isSimple() ? "standard" : "advanced")%> specimen repository</td>
                        <td><%=textLink("Change Repository Type", new ActionURL(SpecimenController.ShowManageRepositorySettingsAction.class, c))%></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Specimen Properties</td>
                        <td class="lk-study-prop-desc">Customize specimen properties for this repository</td>
                        <td><%=textLink("Edit specimen properties", new ActionURL(SpecimenController.DesignerAction.class, c))%></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Display and Behavior</td>
                        <td class="lk-study-prop-desc">Manage warnings, comments, and workflow</td>
                        <td><%= textLink("Manage Display and Behavior",
                                new ActionURL(SpecimenController.ManageDisplaySettingsAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Specimen Web Part</td>
                        <td class="lk-study-prop-desc">Configure the specimen groupings in the specimen web part</td>
                        <td><%= textLink("Configure Specimen Groupings",
                                new ActionURL(SpecimenController.ManageSpecimenWebPartAction.class, c)) %></td>
                    </tr>
                <%
                            for (SpecimenTransform transform : SpecimenService.get().getSpecimenTransforms(getContainer()))
                            {
                                ActionURL manageAction = transform.getManageAction(getContainer(), getUser());
                                if (manageAction != null)
                                {
                %>
                        <tr>
                            <td class="lk-study-prop-label">External Specimen Repository</td>
                            <td class="lk-study-prop-desc">Configure settings for a <%=h(transform.getName())%> repository.</td>
                            <td><%=textLink("Configure " + transform.getName(), manageAction)%></td>
                        </tr>
                <%
                                }
                            }
                %>
                </table>
            </labkey:panel>
        <%
        }
        else
        {
            String childStudyType = study.getStudySnapshotType().getTitle().toLowerCase();
        %>
        <p><em>Note: specimen repository and request settings are not available for <%=text(childStudyType)%> studies.</em></p>
        <%
        }
    } // admin permission

    if (c.hasPermission(user, ManageRequestSettingsPermission.class))
    {
        if (study.getRepositorySettings().isEnableRequests())
        {
    %>
            <labkey:panel title="Specimen Request Settings">
                <table class="lk-fields-table">
                    <tr>
                        <td class="lk-study-prop-label">Statuses</td>
                        <td class="lk-study-prop-desc">This study defines <%= study.getSampleRequestStatuses(getUser()).size() %> specimen request
                            statuses</td>
                        <td><%= textLink("Manage Request Statuses",
                                new ActionURL(SpecimenController.ManageStatusesAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Actors</td>
                        <td class="lk-study-prop-desc">This study defines <%= study.getSampleRequestActors().length %> specimen request
                            actors</td>
                        <td><%= textLink("Manage Actors and Groups",
                                new ActionURL(SpecimenController.ManageActorsAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Request Requirements</td>
                        <td class="lk-study-prop-desc">Manage default requirements for new requests</td>
                        <td><%= textLink("Manage Default Requirements",
                                new ActionURL(SpecimenController.ManageDefaultReqsAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Request Form</td>
                        <td class="lk-study-prop-desc">Manage inputs required for a new specimen request </td>
                        <td><%= textLink("Manage New Request Form",
                                new ActionURL(SpecimenController.ManageRequestInputsAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Notifications</td>
                        <td class="lk-study-prop-desc">Manage specimen request notifications</td>
                        <td><%= textLink("Manage Notifications",
                                new ActionURL(SpecimenController.ManageNotificationsAction.class, c)) %></td>
                    </tr>
                    <tr>
                        <td class="lk-study-prop-label">Requestability Rules</td>
                        <td class="lk-study-prop-desc">Manage the rules used to determine specimen availability for request</td>
                        <td><%= textLink("Manage Requestability Rules",
                                new ActionURL(SpecimenController.ConfigureRequestabilityRulesAction.class, c)) %></td>
                    </tr>
                </table>
            </labkey:panel>
<%
        }
    }

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
        <%= button("Delete Study").href(StudyController.DeleteStudyAction.class, c) %>
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
