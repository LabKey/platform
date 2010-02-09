<%
    /*
    * Copyright (c) 2008-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.study.CohortFilter" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyImpl study = getStudy();
    StudyManager manager = StudyManager.getInstance();
%>
<labkey:errors/>
<%
    if (study.isManualCohortAssignment()) // Need to create a form for submitting the assignments
    {
%>
<form action="manageCohorts.post" name="manualAssignment" method="POST">
    <input type="hidden" name="reshow" value="false">
    <input type="hidden" name="manualCohortAssignment" value="true">
<%
    }
    // Need all participants and cohorts for both versions
    Map<Participant, CohortImpl> participant2Cohort = new LinkedHashMap<Participant, CohortImpl>();
    for (Participant participant : manager.getParticipants(study))
    {
        CohortImpl cohort = manager.getCurrentCohortForParticipant(
                study.getContainer(),
                getViewContext().getUser(),
                participant.getParticipantId());
        participant2Cohort.put(participant, cohort);
    }
    CohortImpl[] cohortArray = manager.getCohorts(study.getContainer(), HttpView.currentContext().getUser());
    List<CohortImpl> cohorts = new ArrayList<CohortImpl>();
    cohorts.addAll(Arrays.asList(cohortArray));

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
                If cohort blinding is required, <a href="<%= securityUrl.getLocalURIString() %>">enable dataset-level security</a>.<p>
            <%
        }
    }
    else
    {
        %>
        <b>Note:</b> All users with read access to this folder can view Cohort information.
                If cohort blinding is required, enable automatic cohort assignment and <a href="<%= securityUrl.getLocalURIString() %>">dataset-level security</a>.<p>
        <%
    }
    // If we're using automatic assignment, the user can't alter the specific assignments,
    // but we want to show a read-only view of them
    %>
    <table>
        <tr>
            <th><%= h(StudyService.get().getSubjectColumnName(getViewContext().getContainer())) %></th>
            <th>Current Cohort</th>
        </tr>
    <%
    for (Map.Entry<Participant, CohortImpl> entry : participant2Cohort.entrySet())
    {


    %>
        <tr>
            <td><%= h(entry.getKey().getParticipantId()) %></td>
            <td><%
                if(!study.isManualCohortAssignment())
                {
                    org.labkey.api.study.Cohort cohort = entry.getValue();
                    String label = cohort == null ? "" : cohort.getLabel();
                    %><%=h(label)%><%
                }
                else
                {
                    %>
                <input type="hidden" name="participantId" value="<%=entry.getKey().getParticipantId()%>">
                <select name="<%= CohortFilter.Params.cohortId.name() %>"><%
                    // Need to display selection drop-down for each participant
                    CohortImpl selectedCohort = entry.getValue();
                    
                    if (selectedCohort == null)
                        selectedCohort = nullCohort;

                    for (CohortImpl c : cohorts)
                    {
                        String selected = c.getRowId() == selectedCohort.getRowId() ? "selected" : "";
                        
                    %>
                    <option value="<%=c.getRowId()%>" <%= selected %>><%=h(c.getLabel())%></option>
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
    }
    %>
    </table>
    <%
        if (study.isManualCohortAssignment())
        {
            %><%= PageFlowUtil.generateSubmitButton("Save")%><%
        }

    %>


</form>
