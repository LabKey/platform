<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Cohort" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.CohortFilterFactory" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.Participant" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyImpl study = getStudy();
    StudyManager manager = StudyManager.getInstance();
%>
<labkey:errors/>
<labkey:form action="<%=h(buildURL(CohortController.ManageCohortsAction.class))%>" name="manualAssignment" method="POST">
<%
    if (study.isManualCohortAssignment()) // Need to create a form for submitting the assignments
    {
%>
    <input type="hidden" name="reshow" value="false">
    <input type="hidden" name="manualCohortAssignment" value="true">
<%
    }
    // Need all participants and cohorts for both versions
    Map<Participant, CohortImpl> participant2Cohort = new LinkedHashMap<>();
    for (Participant participant : manager.getParticipants(study))
    {
        CohortImpl cohort = manager.getCurrentCohortForParticipant(
                study.getContainer(),
                getUser(),
                participant.getParticipantId());
        participant2Cohort.put(participant, cohort);
    }
    List<CohortImpl> cohorts = new ArrayList<>(manager.getCohorts(study.getContainer(), getUser()));

    // Need a null cohort for manual removal of cohort setting
    CohortImpl nullCohort = new CohortImpl();
    nullCohort.setRowId(-1);
    nullCohort.setLabel("<Unassigned>");
    cohorts.add(nullCohort);

    ActionURL securityUrl = new ActionURL(SecurityController.BeginAction.class, study.getContainer());
    if (!study.isManualCohortAssignment())
    {
        if (study.getSecurityType() == SecurityType.ADVANCED_READ || study.getSecurityType() == SecurityType.ADVANCED_WRITE)
        {
            %>
            <b>Note:</b> Only users with read access to the Cohort Dataset specified above can view Cohort information.<p>
            <%
        }
        else
        {
            %>
            <b>Note:</b> All users with read access to this folder can view Cohort information.
                If cohort blinding is required, <a href="<%= h(securityUrl.getLocalURIString()) %>">enable dataset-level security</a>.<p>
            <%
        }
    }
    else
    {
        %>
        <b>Note:</b> All users with read access to this folder can view Cohort information.
                If cohort blinding is required, enable automatic cohort assignment and <a href="<%= h(securityUrl.getLocalURIString()) %>">dataset-level security</a>.<p>
        <%
    }
    // If we're using automatic assignment, the user can't alter the specific assignments,
    // but we want to show a read-only view of them
    %>
    <table id="participant-cohort-assignments" class="labkey-data-region-legacy labkey-show-borders">
        <tr>
            <td class="labkey-column-header"><%= h(StudyService.get().getSubjectColumnName(getContainer())) %></td>
            <td class="labkey-column-header">Current Cohort</td>
        </tr>
    <%
    int index = 0;
    for (Map.Entry<Participant, CohortImpl> entry : participant2Cohort.entrySet())
    {
    %>
        <tr class="<%=h(index % 2 == 0 ? "labkey-alternate-row" : "labkey-row")%>">
            <td><%= h(id(entry.getKey().getParticipantId())) %></td>
            <td><%
                if (!study.isManualCohortAssignment())
                {
                    Cohort cohort = entry.getValue();
                    String label = cohort == null ? "" : cohort.getLabel();
                    %><%=h(label)%><%
                }
                else
                {
                    %>
                <input type="hidden" name="participantId" value="<%=h(entry.getKey().getParticipantId())%>">
                <select name="<%= h(CohortFilterFactory.Params.cohortId.name()) %>"><%
                    // Need to display selection drop-down for each participant
                    CohortImpl selectedCohort = entry.getValue();
                    
                    if (selectedCohort == null)
                        selectedCohort = nullCohort;

                    for (CohortImpl c : cohorts)
                    {
                        boolean selected = c.getRowId() == selectedCohort.getRowId();
                    %>
                    <option value="<%=c.getRowId()%>"<%=selected(selected)%>><%=h(c.getLabel())%></option>
                    <%

                    }

                    %>
                </select>
                <%

                }

            %>
            </td>
        </tr>
    <%
        index++;
    }
    %>
    </table>
    <%
        if (study.isManualCohortAssignment())
        {
            %><%= button("Save").submit(true) %>
            <%
        }
    %>
</labkey:form>