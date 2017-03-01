<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.CreateVisitAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ImportVisitMapAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ShowVisitImportMappingAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.UpdateParticipantVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitOrderAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitSummaryAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitVisibilityAction" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    List<VisitImpl> allVisits = getVisits(Visit.Order.DISPLAY);
%>
<table>
    <tr>
        <td>View study schedule</td>
        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
    </tr>
<%
    if (allVisits.size() > 0)
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
    <tr>
        <td>Visits may be deleted by an administrator</td>
        <td><%= textLink("Delete Multiple Visits", StudyController.BulkDeleteVisitsAction.class) %></td>
    </tr>
    <tr>
        <td>Delete unused visits</td>
        <td><%= textLink("Delete Unused Visits", StudyController.DeleteUnusedVisitsAction.class) %></td>
    </tr>
<%
    }
%>
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
        <td>New visits can be defined for this study at any time</td>
        <td><%= textLink("Create New Visit", CreateVisitAction.class)%></td>
    </tr>
</table>

<%
    if (allVisits.size() > 0)
    {
%>
<p>
<table id="visits" class="labkey-data-region labkey-show-borders">
    <tr>
        <td class="labkey-column-header">&nbsp;</td>
        <td class="labkey-column-header">Label</td>
        <td class="labkey-column-header">Sequence</td>
        <td class="labkey-column-header">Cohort</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">Show By Default</td>
        <td class="labkey-column-header">Description</td>
    </tr>
    <%
        int rowCount = 0;
        for (VisitImpl visit : getVisits(Visit.Order.DISPLAY))
        {
            rowCount++;
    %>
        <tr class="visit-row <%=h(rowCount % 2 == 1 ? "labkey-alternate-row" : "labkey-row")%>">
            <td><%= textLink("edit", buildURL(VisitSummaryAction.class)+ "id=" + visit.getRowId()) %></td>
            <td align=left><%= h(visit.getDisplayString()) %></td>
            <td class="visit-range-cell"><%= visit.getSequenceNumMin() %><%= h(visit.getSequenceNumMin()!= visit.getSequenceNumMax() ? " - " + visit.getSequenceNumMax() : "") %></td>
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