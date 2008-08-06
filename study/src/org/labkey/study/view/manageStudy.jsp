<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<h4>General Study Information</h4>
<%
    String visitLabel = StudyManager.getInstance().getVisitManager(getStudy()).getPluralLabel();
    ActionURL manageCohortsURL = new ActionURL(CohortController.ManageCohortsAction.class, getStudy().getContainer());
%>
<table>
    <tr>
        <th align="left">Study Label</th>
        <td><%= h(getStudy().getLabel()) %></td>
        <td><%= textLink("Change Label", "manageStudyProperties.view") %></td>
    </tr>
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
        <td><%= textLink("Manage Cohorts", manageCohortsURL.getLocalURIString()) %></td>
    </tr>
    <tr>
        <th align="left">Security</th>
        <td>Manage access to Study datasets and samples</td>
        <% ActionURL url = new ActionURL(SecurityController.BeginAction.class, getStudy().getContainer());%>
        <td><%= textLink("Manage Security", url.toString()) %></td>
    </tr>
    <tr>
        <th align="left">Reports/Views</th>
        <td>Manage reports and views for this Study</td>
        <td><%= PageFlowUtil.textLink("Manage Reports and Views", new ActionURL("Study-Reports", "manageReports.view", getStudy().getContainer())) %></td>
    </tr>
    <tr>
        <th align="left">Quality Control States</th>
        <td>Manage QC states for this Study</td>
        <td><%= PageFlowUtil.textLink("Manage QC States", new ActionURL("Study", "manageQCStates.view", getStudy().getContainer())) %></td>
    </tr>
</table>

<h4>Specimen Request/Tracking Settings</h4>
<table>
    <tr>
        <th align="left">Specimen Repository</th>
        <td>This study uses <%=getStudy().getRepositorySettings().isSimple() ? "standard" : "advanced"%> specimen repository</td>
        <td><%=textLink("Change Repository System", ActionURL.toPathString("Study-Samples", "showManageRepositorySettings.view", getStudy().getContainer()))%></td>
    </tr>
    <%
        if (getStudy().getRepositorySettings().isEnableRequests())
        {
    %>
    <tr>
        <th align="left">Statuses</th>
        <td>This study defines <%= getStudy().getSampleRequestStatuses(HttpView.currentContext().getUser()).length %> specimen request
            statuses</td>
        <td><%= textLink("Manage Request Statuses",
                ActionURL.toPathString("Study-Samples", "manageStatuses.view", getStudy().getContainer())) %></td>
    </tr>
    <tr>
        <th align="left">Actors</th>
        <td>This study defines <%= getStudy().getSampleRequestActors().length %> specimen request
            actors</td>
        <td><%= textLink("Manage Actors and Groups",
                ActionURL.toPathString("Study-Samples", "manageActors.view", getStudy().getContainer())) %></td>
    </tr>
    <tr>
        <th align="left">Request Requirements</th>
        <td>The default requirements for new requests can be customized by study</td>
        <td><%= textLink("Manage Default Requirements",
                ActionURL.toPathString("Study-Samples", "manageDefaultReqs.view", getStudy().getContainer())) %></td>
    </tr>
    <tr>
        <th align="left">Request Form</th>
        <td>The inputs required for a new specimen request can be customized by study.</td>
        <td><%= textLink("Manage New Request Form",
                ActionURL.toPathString("Study-Samples", "manageRequestInputs.view", getStudy().getContainer())) %></td>
    </tr>
    <tr>
        <th align="left">Notifications</th>
        <td>Specimen request notifications can be customized by study.</td>
        <td><%= textLink("Manage Notifications",
                ActionURL.toPathString("Study-Samples", "manageNotifications.view", getStudy().getContainer())) %></td>
    </tr>
    <tr>
        <th align="left">Display Settings</th>
        <td>Warnings for low available vial counts are configurable.</td>
        <td><%= textLink("Manage Display Settings",
                ActionURL.toPathString("Study-Samples", "manageDisplaySettings.view", getStudy().getContainer())) %></td>
    </tr>
    <%
        }
    %>
</table>
<%=buttonLink("Snapshot Study Data", "snapshot.view")%> <%=buttonLink("Delete Study", "deleteStudy.view")%>