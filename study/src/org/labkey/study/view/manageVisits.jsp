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
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController.BulkDeleteVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.CreateVisitAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.DeleteUnusedVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ImportVisitMapAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ShowVisitImportMappingAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.StudyScheduleAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.UpdateParticipantVisitsAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitOrderAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitSummaryAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.VisitVisibilityAction" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    List<VisitImpl> allVisits = getVisits(Visit.Order.DISPLAY);
%>
<table class="lk-fields-table">
    <tr>
        <td>View study schedule</td>
        <td><%= link("Study Schedule", StudyScheduleAction.class) %></td>
    </tr>
<%
    if (allVisits.size() > 0)
    {
%>
    <tr>
        <td>Visit ordering affects the study view, reports, and cohort determinations</td>
        <td><%= link("Change Visit Order", VisitOrderAction.class)%></td>
    </tr>
    <tr>
        <td>Visit visibility and label can be changed</td>
        <td><%= link("Change Properties", VisitVisibilityAction.class)%></td>
    </tr>
    <tr>
        <td>Visits may be deleted by an administrator</td>
        <td><%= link("Delete Multiple Visits", BulkDeleteVisitsAction.class) %></td>
    </tr>
    <tr>
        <td>Delete unused visits</td>
        <td><%= link("Delete Unused Visits", DeleteUnusedVisitsAction.class) %></td>
    </tr>
<%
    }
%>
    <tr>
        <td>Recalculate visit dates</td>
        <td><%= link("Recalculate Visit Dates", UpdateParticipantVisitsAction.class)%></td>
    </tr>
    <tr>
        <td>Import a visit map to quickly define a study</td>
        <td><%= link("Import Visit Map", ImportVisitMapAction.class) %></td>
    </tr>
    <tr>
        <td>Visit import mapping allows data containing visit names instead of numbers</td>
        <td><%= link("Visit Import Mapping", ShowVisitImportMappingAction.class) %></td>
    </tr>
    <tr>
        <td>New visits can be defined for this study at any time</td>
        <td><%= link("Create New Visit", CreateVisitAction.class)%></td>
    </tr>
</table>

<%
    if (allVisits.size() > 0)
    {
%>
<p>
<labkey:panel title="Visits" width="800">
<table id="visits" class="labkey-data-region-legacy labkey-show-borders">
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
        ActionURL editTimepointURL = new ActionURL(VisitSummaryAction.class, getStudy().getContainer());
        int rowCount = 0;
        for (VisitImpl visit : getVisits(Visit.Order.DISPLAY))
        {
            rowCount++;
    %>
        <tr class="visit-row <%=h(rowCount % 2 == 1 ? "labkey-alternate-row" : "labkey-row")%>">
            <td width="20"><%= iconLink("fa fa-pencil", "edit", editTimepointURL.replaceParameter("id", visit.getRowId())) %></td>
            <td align=left><%= h(visit.getDisplayString()) %></td>
            <td class="visit-range-cell"><%=h(visit.getSequenceString())%></td>
            <td><%= h(visit.getCohort() != null ? h(visit.getCohort().getLabel()) : "All") %></td>
            <td><%= h(visit.getType() != null ? visit.getType().getMeaning() : "[Not defined]") %></td>
            <td><%= visit.isShowByDefault()%></td>
            <td><%= h(visit.getDescription()) %></td>
        </tr>
    <%
        }
    %>
</table>
</labkey:panel>
<%
    }
%>