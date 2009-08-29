<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.StudyDefinitionController" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.query.StudyPropertiesQueryView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.study.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.importer.StudyReload" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    JspView<StudyPropertiesQueryView> me = (JspView<StudyPropertiesQueryView>) HttpView.currentView();
    StudyImpl study = getStudy();
    Container c = me.getViewContext().getContainer();

    String visitLabel = StudyManager.getInstance().getVisitManager(study).getPluralLabel();
    ActionURL manageCohortsURL = new ActionURL(CohortController.ManageCohortsAction.class, c);
    User user = HttpView.currentContext().getUser();
    int numProperties = study.getNumExtendedProperties(user);
    String propString = numProperties == 1 ? "property" : "properties";

    StudyReload.ReloadInterval currentInterval = StudyReload.ReloadInterval.getForSeconds(study.getReloadInterval());
    String intervalLabel;

    if (!study.isAllowReload())
        intervalLabel = "This study is set to not reload";
    else if (null == study.getReloadInterval() || 0 == study.getReloadInterval().intValue())
        intervalLabel = "This study is set for manual reloading";
    else
        intervalLabel = "This study is scheduled to check for reload " + (StudyReload.ReloadInterval.Never != currentInterval ? currentInterval.getDescription() : "every " + study.getReloadInterval() + " seconds");
%>
<table>
    <%
        if(c.hasPermission(user, AdminPermission.class))
        {
    %>
    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>General Study Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Study Label</th>
        <td><%= h(study.getLabel()) %></td>
        <td><%= textLink("Change Label", "manageStudyProperties.view") %></td>
    </tr>
    <tr>
        <th align="left">Reloading</th>
        <td><%= h(intervalLabel) %></td>
        <td><%= textLink("Manage Reloading", "manageReload.view") %></td>
    </tr>
    <tr>
        <th align="left">Additional Properties</th>
        <td>This study has <%=numProperties%> additional <%=propString%></td>
        <td><%= textLink("Edit Definition", new ActionURL(StudyDefinitionController.EditStudyDefinitionAction.class, c)) %></td>
    </tr>

    <% if (numProperties > 0)
    {
    %>

    <tr>
        <td colspan="3">

            <%me.include(me.getModelBean(), out);%>

        </td>
    </tr>
    <%
    }
    %>

    <tr>
        <th align="left">Datasets</th>
        <td>This study defines <%= getDataSets().length %> Datasets</td>
        <td><%= textLink("Manage Datasets", "manageTypes.view") %></td>
    </tr>
    <tr>
        <th align="left"><%= visitLabel %></th>
        <td>This study defines <%= getVisits().length %> <%=visitLabel%></td>
        <td><%= textLink("Manage " + visitLabel, "manageVisits.view") %></td>
    </tr>
    <tr>
        <th align="left">Locations</th>
        <td>This study references <%= getSites().length %> labs/sites/repositories</td>
        <td><%= textLink("Manage Labs/Sites", "manageSites.view") %></td>
    </tr>
    <tr>
        <th align="left">Cohorts</th>
        <td>This study defines <%= getCohorts(getViewContext().getUser()).length %> cohorts</td>
        <td><%= textLink("Manage Cohorts", manageCohortsURL) %></td>
    </tr>
    <tr>
        <th align="left">Security</th>
        <td>Manage access to Study datasets and samples</td>
        <% ActionURL url = new ActionURL(SecurityController.BeginAction.class, c);%>
        <td><%= textLink("Manage Security", url) %></td>
    </tr>
    <tr>
        <th align="left">Reports/Views</th>
        <td>Manage views for this Study</td>
        <td><%= PageFlowUtil.textLink("Manage Views", new ActionURL(ReportsController.ManageReportsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Quality Control States</th>
        <td>Manage QC states for this Study</td>
        <td><%= PageFlowUtil.textLink("Manage QC States", new ActionURL(StudyController.ManageQCStatesAction.class, c)) %></td>
    </tr>
    <tr>
        <td colspan="3" class="labkey-announcement-title"><span>Specimen Repository Settings</span></td>
    </tr>
    <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
    <tr>
        <th align="left">Repository Type</th>
        <td>This study uses the <%=study.getRepositorySettings().isSimple() ? "standard" : "advanced"%> specimen repository</td>
        <td><%=textLink("Change Repository Type", new ActionURL(SpringSpecimenController.ShowManageRepositorySettingsAction.class, c))%></td>
    </tr>
    <tr>
        <th align="left">Display and Behavior</th>
        <td>Manage warnings, comments, and workflow</td>
        <td><%= textLink("Manage Display and Behavior",
                new ActionURL(SpringSpecimenController.ManageDisplaySettingsAction.class, c)) %></td>
    </tr>
<%
    } // admin permission

    if(c.hasPermission(user, ManageRequestSettingsPermission.class))
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
        <td>This study defines <%= study.getSampleRequestStatuses(HttpView.currentContext().getUser()).length %> specimen request
            statuses</td>
        <td><%= textLink("Manage Request Statuses",
                new ActionURL(SpringSpecimenController.ManageStatusesAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Actors</th>
        <td>This study defines <%= study.getSampleRequestActors().length %> specimen request
            actors</td>
        <td><%= textLink("Manage Actors and Groups",
                new ActionURL(SpringSpecimenController.ManageActorsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Request Requirements</th>
        <td>Manage default requirements for new requests</td>
        <td><%= textLink("Manage Default Requirements",
                new ActionURL(SpringSpecimenController.ManageDefaultReqsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Request Form</th>
        <td>Manage inputs required for a new specimen request </td>
        <td><%= textLink("Manage New Request Form",
                new ActionURL(SpringSpecimenController.ManageRequestInputsAction.class, c)) %></td>
    </tr>
    <tr>
        <th align="left">Notifications</th>
        <td>Manage specimen request notifications</td>
        <td><%= textLink("Manage Notifications",
                new ActionURL(SpringSpecimenController.ManageNotificationsAction.class, c)) %></td>
    </tr>
    <%
        }
        }
    %>
</table><br>
<%
    if(c.hasPermission(user, AdminPermission.class))
    {
%>
<%=generateButton("Export Study", "exportStudy.view")%>
<%=generateButton("Snapshot Study Data", "snapshot.view")%>
<%=generateButton("Delete Study", "deleteStudy.view")%>
<%
    }
%>