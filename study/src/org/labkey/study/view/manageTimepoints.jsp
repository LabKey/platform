<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.CreateVisitAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.UpdateParticipantVisitsAction" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.io.Writer" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<labkey:errors />
<%
    StudyController.StudyPropertiesForm form = (StudyController.StudyPropertiesForm) getModelBean();
    Study study = getStudy();
    List<VisitImpl> timepoints = StudyManager.getInstance().getVisits(study, Visit.Order.DISPLAY);

    if (form.getStartDate() == null)
    {
        form.setStartDate(study.getStartDate());
    }
    if (form.getDefaultTimepointDuration() == 0)
    {
        form.setDefaultTimepointDuration(study.getDefaultTimepointDuration());
    }
%>

<table>
    <tr>
        <td>View study schedule.</td>
        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
    </tr>
    <tr>
        <td>Assign data to the correct timepoint</td>
        <td><%= textLink("Recompute Timepoints", UpdateParticipantVisitsAction.class)%></td>
    </tr>
<% if (timepoints.size() > 0) { %>
    <tr>
        <td>Timepoints may be deleted by an administrator</td>
        <td><%= textLink("Delete Multiple Timepoints", StudyController.BulkDeleteVisitsAction.class) %></td>
    </tr>
<% } %>
    <tr>
        <td>New visits can be defined for this study at any time.</td>
        <td><%= textLink("Create New Timepoint", CreateVisitAction.class)%></td>
    </tr>
</table>

<%
    FrameFactoryClassic.startTitleFrame(out, "Timepoint Configuration", null, "600", null);
%>
<labkey:form action="<%=h(buildURL(StudyController.ManageVisitsAction.class))%>" method="POST">
   Data in this study is grouped using date-based timepoints rather than visit ids.
    <ul>
       <li>A timepoint is assigned to each dataset row by computing the number of days between a subject's start date and the date supplied in the row.</li>
       <li>Each subject can have an individual start date specified by providing a StartDate field in a demographic dataset.</li>
       <li>If no start date is available for a subject, the study start date is used.</li>
       <li>
           If dataset, specimen, or other data is imported that is not associated with an existing timepoint,
           a new timepoint will automatically be created.
       </li>
       <li>
           The default timepoint duration will determine the number of days included in automatically created timepoints.
       </li>
    </ul>
<%
    if (PageFlowUtil.useExperimentalCoreUI())
    {
%>
    <labkey:input type="text" id="startDateInput" name="startDate" label="Start Date"
                  contextContent="A start date is required for date based studies."
                  value="<%=formatDate(form.getStartDate())%>" />
    <labkey:input type="number" id="defaultTimepointDurationInput" name="defaultTimepointDuration"
                  label="Default Timepoint Duration" value="<%=h(form.getDefaultTimepointDuration())%>" />
<%
    }
    else
    {
%>
    <table>
        <tr>
            <td class="labkey-form-label"><label for="startDateInput">Start Date</label><%=helpPopup("Start Date", "A start date is required for studies that are date based.")%></td>
            <td><input type="text" id="startDateInput" name="startDate" value="<%=formatDate(form.getStartDate())%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="defaultTimepointDurationInput">Default Timepoint Duration</label></td>
            <td><input type="number" id="defaultTimepointDurationInput" name="defaultTimepointDuration" value="<%=h(form.getDefaultTimepointDuration())%>">
            </td>
        </tr>
    </table>
<%
    }
%>
    <%= button("Update").submit(true) %>
    <%= generateBackButton() %>
</labkey:form>
<%
    FrameFactoryClassic.endTitleFrame(out);
%>

<% if (timepoints.size() > 0) { %>
<%
    FrameFactoryClassic.startTitleFrame(out, "Timepoints", null, "900", null);
%>
<p>NOTE: If you edit the day range of timepoints, use <%= textLink("Recompute Timepoints", UpdateParticipantVisitsAction.class)%> to
assign dataset data to the correct timepoints.</p>
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">&nbsp;</td>
        <td class="labkey-column-header">Label</td>
        <td class="labkey-column-header">Start Day</td>
        <td class="labkey-column-header">End Day</td>
        <td class="labkey-column-header">Cohort</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header">Show By Default</td>
        <td class="labkey-column-header">Description</td>
    </tr>
<%
    ActionURL editTimepointURL = new ActionURL(StudyController.VisitSummaryAction.class, study.getContainer());
    int rowCount = 0;
    for (VisitImpl timepoint : timepoints)
    {
        rowCount++;
%>
    <tr class="<%=h(rowCount % 2 == 1 ? "labkey-alternate-row" : "labkey-row")%>">
<%
    if (PageFlowUtil.useExperimentalCoreUI())
    {
%>
        <td width="20"><%= iconLink("fa fa-pencil", "edit", editTimepointURL.replaceParameter("id", String.valueOf(timepoint.getRowId()))) %></td>
<%
    }
    else
    {
%>
        <td><%=textLink("edit", editTimepointURL.replaceParameter("id", String.valueOf(timepoint.getRowId())))%></td>
<%
    }
%>
        <td><%=h(timepoint.getLabel())%></td>
        <td><%=h(""+timepoint.getSequenceNumMin())%></td>
        <td><%=h(""+timepoint.getSequenceNumMax())%></td>
        <td><%= h(timepoint.getCohort() != null ? h(timepoint.getCohort().getLabel()) : "All") %></td>
        <td><%= h(timepoint.getType() != null ? timepoint.getType().getMeaning() : "[Not defined]") %></td>
        <td><%= timepoint.isShowByDefault()%></td>
        <td><%= h(timepoint.getDescription()) %></td>
    </tr>
<%  }
%>
</table>
<%
    FrameFactoryClassic.endTitleFrame(out);
%>
<% } %>