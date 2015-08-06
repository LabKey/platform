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
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.CreateVisitAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ImportVisitMapAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ShowVisitImportMappingAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.UpdateParticipantVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitOrderAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitSummaryAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitVisibilityAction" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("study/ManageVisit.css"));
        return resources;
    }
%>
<table>
    <tr>
        <td>View study schedule</td>
        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
    </tr>
<%
    if (getVisits(Visit.Order.DISPLAY).size() > 0)
    {
%>
    <tr>
        <td>Visit ordering affects the study view, reports, and cohort determinations</td>
        <td><%= textLink("Change Visit Order", VisitOrderAction.class)%></td>
    </tr>
    <tr>
        <td>Visit visibility and label can be changed</td>
        <td><%= textLink("Change Properties", VisitVisibilityAction.class)%></td>
    </tr>
<%
    }
%>
    <tr>
        <td>New visits can be defined for this study at any time</td>
        <td><%= textLink("Create New Visit", CreateVisitAction.class)%></td>
    </tr>
    <tr>
        <td>Recalculate visit dates</td>
        <td><%= textLink("Recalculate Visit Dates", UpdateParticipantVisitsAction.class)%></td>
    </tr>
    <tr>
        <td>Import a visit map to quickly define a study</td>
        <td><%= textLink("Import Visit Map", ImportVisitMapAction.class) %></td>
    </tr>
    <tr>
        <td>Visit import mapping allows data containing visit names instead of numbers</td>
        <td><%= textLink("Visit Import Mapping", ShowVisitImportMappingAction.class) %></td>
    </tr>
    <tr>
        <td>Delete unused visits</td>
        <td><%= textLink("Delete Unused Visits", StudyController.DeleteUnusedVisitsAction.class) %></td>
    </tr>
</table>

<%
    if (getVisits(Visit.Order.DISPLAY).size() > 0)
    {
%>
<p>
<table id="visits" cellpadding="3" class="manage-visit-table">
    <tr>
        <th>&nbsp;</th>
        <th>Label</th>
        <th>Sequence</th>
        <th>Cohort</th>
        <th>Type</th>
        <th>Show By Default</th>
        <th>Description</th>
    </tr>
    <%
        for (VisitImpl visit : getVisits(Visit.Order.DISPLAY))
        {
    %>
        <tr>
            <td><%= textLink("edit", buildURL(VisitSummaryAction.class)+ "id=" + visit.getRowId()) %></td>
            <th align=left><%= h(visit.getDisplayString()) %></th>
            <td><%= visit.getSequenceNumMin() %><%= h(visit.getSequenceNumMin()!= visit.getSequenceNumMax() ? "-" + visit.getSequenceNumMax() : "") %></td>
            <td><%= h(visit.getCohort() != null ? h(visit.getCohort().getLabel()) : "All") %></td>
            <td><%= h(visit.getType() != null ? visit.getType().getMeaning() : "[Not defined]") %></td>
            <td><%= visit.isShowByDefault()%></td>
            <td><%= h(visit.getDescription()) %></td>
        </tr>
    <%
        }
    %>
</table>
<%
    }
%>