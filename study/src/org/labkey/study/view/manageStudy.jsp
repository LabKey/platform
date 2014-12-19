<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.SpecimenTransform" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageReloadAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyPropertiesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageTypesAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyDefinitionController" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.importer.StudyReload" %>
<%@ page import="org.labkey.study.model.ParticipantCategoryImpl" %>
<%@ page import="org.labkey.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.StudySnapshot" %>
<%@ page import="org.labkey.study.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromPath("clientapi/ext3"));
      resources.add(ClientDependency.fromPath("reports/rowExpander.js"));
      resources.add(ClientDependency.fromPath("FileUploadField.js"));
      resources.add(ClientDependency.fromPath("study/StudyWizard.js"));
      return resources;
  }
%>
<%
    StudyImpl study = getStudy();
    Container c = getContainer();

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
        groups.addAll(Arrays.asList(ParticipantGroupManager.getInstance().getParticipantGroups(c, user, category)));
    }

    if (!study.isAllowReload())
        intervalLabel = "This study is set to not reload";
    else if (null == study.getReloadInterval() || 0 == study.getReloadInterval())
        intervalLabel = "This study is set for manual reloading";
    else
        intervalLabel = "This study is scheduled to check for reload " + (StudyReload.ReloadInterval.Never != currentInterval ? currentInterval.getDescription() : "every " + study.getReloadInterval() + " seconds");

    String availableStudyName = ContainerManager.getAvailableChildContainerName(c, "New Study");

    int numDatasets = study.getDatasetsByType(org.labkey.api.study.DataSet.TYPE_STANDARD, org.labkey.api.study.DataSet.TYPE_PLACEHOLDER).size();

    if (study.hasSourceStudy() || study.isSnapshotStudy())
    {
        String snapshotTitle = study.getStudySnapshotType().getTitle().toLowerCase();
        snapshotTitle = ("ancillary".equals(snapshotTitle) ? "an " : "a ") + snapshotTitle;
%>
<table>
    <tr>
        <td>
            <p>This is <%=text(snapshotTitle)%> study.</p>
        </td>
    </tr>
</table>
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
    }
%>

<table>
    <%
        if (c.hasPermission(user, AdminPermission.class))
        {
    %>
    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>General Study Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Study Properties</th>
        <td>Study label, investigator, grant, description, etc.</td>
        <td><%= textLink("Change Study Properties", ManageStudyPropertiesAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Additional Properties</th>
        <td>This study has <%=numProperties%> additional <%=h(propString)%></td>
        <td><%
            Container p = c.getProject();
            if (p.hasPermission(user, AdminPermission.class))
            {
                ActionURL returnURL = getActionURL();
                ActionURL editDefinition = new ActionURL(StudyDefinitionController.EditStudyDefinitionAction.class, p);
                editDefinition.addReturnURL(returnURL);
                %><%= textLink("Edit Definition", editDefinition) %><%
            }
            else
            {
                %>&nbsp;<%
            }
        %></td>
    </tr>
    <tr>
        <th align="left">Reloading</th>
        <td><%= h(intervalLabel) %></td>
        <td><%= textLink("Manage Reloading", ManageReloadAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Datasets</th>
        <td>This study defines <%= numDatasets %> datasets</td>
        <td><%= textLink("Manage Datasets", ManageTypesAction.class) %></td>
    </tr>
    <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
    <tr>
        <th align="left"><%= h(visitLabel) %></th>
        <td>This study defines <%= getVisits(Visit.Order.DISPLAY).size()%> <%=h(visitLabel.toLowerCase())%></td>
        <td><%= textLink("Manage " + visitLabel, ManageVisitsAction.class) %></td>
    </tr>
    <% } %>
     <tr>
        <th align="left">Study Schedule</th>
         <td>This study defines <%= numDatasets %> datasets
             <% if (study.getTimepointType() != TimepointType.CONTINUOUS) { %>
             and <%= getVisits(Visit.Order.DISPLAY).size() %> <%=h(visitLabel.toLowerCase())%>
             <% } %>
         </td>
        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Locations</th>
        <td>This study references <%= getLocations().size() %> locations (labs/sites/repositories)</td>
        <td><%= textLink("Manage Locations", StudyController.ManageLocationsAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Location Types</th>
        <td>Configure which location types are allowed to be requesting locations</td>
        <td><%= textLink("Manage Location Types", StudyController.ManageLocationTypesAction.class) %></td>
    </tr>

    <tr>
        <th align="left">Cohorts</th>
        <td>This study defines <%= getCohorts(getUser()).size() %> cohorts</td>
        <td><%= textLink("Manage Cohorts", manageCohortsURL) %></td>
    </tr>
    <tr>
        <th align="left"><%= h(subjectNounSingle) %> Groups</th>
        <td>This study defines <%=groups.size()%> <%= h(subjectNounSingle.toLowerCase()) %> groups</td>
        <td><%= textLink("Manage " + h(subjectNounSingle) + " Groups", new ActionURL(StudyController.ManageParticipantCategoriesAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Alternate <%= h(subjectNounSingle) %> IDs</th>
        <td>Configure how alternate <%= h(subjectNounSingle.toLowerCase()) %> ids and aliases are generated</td>
        <td><%= textLink("Manage Alternate " + h(subjectNounSingle) + " IDs and Aliases", new ActionURL(StudyController.ManageAlternateIdsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Security</th>
        <td>Manage access to study datasets and samples</td>
        <% ActionURL url = new ActionURL(SecurityController.BeginAction.class, c);%>
        <td><%= textLink("Manage Security", url) %></td>
    </tr>
    <tr>
        <th align="left">Reports/Views</th>
        <td>Manage views for this Study</td>
        <td><%=textLink("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(c)) %></td>
    </tr>
    <tr>
        <th align="left">Quality Control States</th>
        <td>Manage QC states for datasets in this study</td>
        <td><%=textLink("Manage Dataset QC States", new ActionURL(StudyController.ManageQCStatesAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Comments</th>
        <td>Manage <%= h(subjectNounSingle.toLowerCase()) %> and  <%= h(subjectNounSingle.toLowerCase()) %>/visit comments</td>
        <td><%= textLink("Manage Comments",
                new ActionURL(SpecimenController.ManageSpecimenCommentsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Study Products</th>
        <td>This study defines <%= getStudyProducts(user, null).size() %> study products</td>
        <td><%= textLink("Manage Study Products", StudyDesignController.ManageStudyProductsAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Treatments</th>
        <td>This study defines <%= getStudyTreatments(user).size() %> treatments</td>
        <td><%= textLink("Manage Treatments", StudyDesignController.ManageTreatmentsAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Assay Schedule</th>
        <td>This study defines <%= getAssaySpecimenConfigs().size() %> assay configurations</td>
        <td><%= textLink("Manage Assay Schedule", StudyDesignController.ManageAssayScheduleAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Demo Mode</th>
        <td>Demo mode obscures <%=h(subjectNounSingle.toLowerCase())%> IDs on many pages</td>
        <td><%=textLink("Demo Mode",
                new ActionURL(StudyController.DemoModeAction.class, c)) %></td>
    </tr>
<%
        if (!study.hasSourceStudy() && !study.isSnapshotStudy())
        {
%>

    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>Specimen Repository Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Repository Type</th>
        <td>This study uses the <%=text(study.getRepositorySettings().isSimple() ? "standard" : "advanced")%> specimen repository</td>
        <td><%=textLink("Change Repository Type", new ActionURL(SpecimenController.ShowManageRepositorySettingsAction.class, c))%></td>
    </tr>
    <tr>
        <th align="left">Specimen Properties</th>
        <td>Customize specimen properties for this repository</td>
        <td><%=textLink("Edit specimen properties", new ActionURL(SpecimenController.DesignerAction.class, c))%></td>
    </tr>
    <tr>
        <th align="left">Display and Behavior</th>
        <td>Manage warnings, comments, and workflow</td>
        <td><%= textLink("Manage Display and Behavior",
                new ActionURL(SpecimenController.ManageDisplaySettingsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Specimen Web Part</th>
        <td>Configure the specimen groupings in the specimen web part</td>
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
            <th align="left">External Specimen Repository</th>
            <td>Configure settings for a <%=h(transform.getName())%> repository.</td>
            <td><%=textLink("Configure " + transform.getName(), manageAction)%></td>
        </tr>
<%
                }
            }
        }
        else
        {
            String childStudyType = study.getStudySnapshotType().getTitle().toLowerCase();
%>
    <tr>
        <td colspan="3">
            <p><em>Note: specimen repository and request settings are not available for <%=text(childStudyType)%> studies.</em></p>
        </td>
    </tr>
<%
        }
    } // admin permission

    if (c.hasPermission(user, ManageRequestSettingsPermission.class))
    {
%>
    <%
        if (study.getRepositorySettings().isEnableRequests())
        {
    %>
    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>Specimen Request Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Statuses</th>
        <td>This study defines <%= study.getSampleRequestStatuses(getUser()).size() %> specimen request
            statuses</td>
        <td><%= textLink("Manage Request Statuses",
                new ActionURL(SpecimenController.ManageStatusesAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Actors</th>
        <td>This study defines <%= study.getSampleRequestActors().length %> specimen request
            actors</td>
        <td><%= textLink("Manage Actors and Groups",
                new ActionURL(SpecimenController.ManageActorsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Request Requirements</th>
        <td>Manage default requirements for new requests</td>
        <td><%= textLink("Manage Default Requirements",
                new ActionURL(SpecimenController.ManageDefaultReqsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Request Form</th>
        <td>Manage inputs required for a new specimen request </td>
        <td><%= textLink("Manage New Request Form",
                new ActionURL(SpecimenController.ManageRequestInputsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Notifications</th>
        <td>Manage specimen request notifications</td>
        <td><%= textLink("Manage Notifications",
                new ActionURL(SpecimenController.ManageNotificationsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Requestability Rules</th>
        <td>Manage the rules used to determine specimen availability for request</td>
        <td><%= textLink("Manage Requestability Rules",
                new ActionURL(SpecimenController.ConfigureRequestabilityRulesAction.class, c)) %></td>
    </tr>
    <%
        }
        }
    %>
</table><br>
<%
    if (c.hasPermission(user, AdminPermission.class) && !c.isDataspace())
    {
%>
<%= button("Export Study").href(urlProvider(AdminUrls.class).getExportFolderURL(c).addParameter("exportType", "study")) %>
<%= button("Reload Study").href(urlProvider(AdminUrls.class).getImportFolderURL(c).addParameter("origin", "Reload")) %>
<%= button("Delete Study").href(StudyController.DeleteStudyAction.class, c) %>
<%= button("Create Ancillary Study").href("javascript:void(0)").onClick("showCreateStudyWizard('ancillary')") %>
<%= button("Publish Study").href("javascript:void(0)").onClick("showCreateStudyWizard('publish')") %>
<%
    }
%>
<script type="text/javascript">
    function showCreateStudyWizard(mode)
    {
        Ext.onReady(function(){

            var wizard = new LABKEY.study.CreateStudyWizard({
                mode: mode,
                studyName : <%=q(availableStudyName)%>
            });

            wizard.on('success', function(info){}, this);

            // run the wizard
            wizard.show();
        });
    }
</script>
