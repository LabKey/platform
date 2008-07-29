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
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<QueryView> me = (JspView<QueryView>) HttpView.currentView();
    QueryView view = me.getModelBean();
    Study study = getStudy();
    StudyManager manager = StudyManager.getInstance();
%>
<labkey:errors/>

<form action="manageCohorts.post" name="manageCohorts" method="POST">
    <input type="hidden" name="reshow" value="true">

    <%
        WebPartView.startTitleFrame(out, "Participant/Cohort Assignment");
    %>
    <input type="radio" name="manualCohortAssignment"
           value="false" <%=study.isManualCohortAssignment() ? "" : "checked"%>>Automatic<br>
    <input type="radio" name="manualCohortAssignment"
           value="true" <%=study.isManualCohortAssignment() ? "checked" : ""%>>Manual
    <p>
        <%= buttonImg("Update", "document.manageCohorts.reshow.value='true'; return true;")%>
        <%= buttonLink("Cancel", new ActionURL(StudyController.ManageStudyAction.class, me.getViewContext().getContainer()))%>

    <%
        WebPartView.endTitleFrame(out);
    %>
    <%
    if (!study.isManualCohortAssignment())
    { // If it's automatic, we need to include the dataset selection widgets
        WebPartView.startTitleFrame(out, "Automatic Participant/Cohort Assignment");
    %>
    <b>Note:</b> Only users with read access to the selected dataset will be able to view Cohort information.
<table>
        <tr>
            <th align="right">Participant/Cohort Dataset<%= helpPopup("Participant/Cohort Dataset", "Participants can be assigned to cohorts based on the data in a field of a single dataset.  If set, participant's cohort assignments will be reloaded every time this dataset is re-inported.")%></th>
            <td>
                <select name="participantCohortDataSetId" onchange="document.manageCohorts.participantCohortProperty.value=''; document.manageCohorts.submit()">
                    <option value="-1">[None]</option>
                    <%
                        for (DataSetDefinition dataset : manager.getDataSetDefinitions(study))
                        {
                            String selected = (study.getParticipantCohortDataSetId() != null &&
                                    dataset.getDataSetId() == study.getParticipantCohortDataSetId() ? "selected" : "");
                            %><option value="<%= dataset.getDataSetId() %>" <%= selected %>><%= h(dataset.getLabel()) %></option><%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <th align="right">Cohort Field Name</th>
            <td>
                <select name="participantCohortProperty">
                    <option value="">[None]</option>
                <%
            PropertyDescriptor[] descriptors;
            Integer particpantCohortDataSetId = study.getParticipantCohortDataSetId();
            if (particpantCohortDataSetId == null || particpantCohortDataSetId.intValue() < 0)
                descriptors = new PropertyDescriptor[0];
            else
            {
                DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinition(study, particpantCohortDataSetId.intValue());
                descriptors = OntologyManager.getPropertiesForType(dataset.getTypeURI(), study.getContainer());
            }
                for (PropertyDescriptor pd : descriptors)
                {
                    if (pd.getPropertyType() == PropertyType.STRING) // only strings can be cohort labels
                    {
                %>
                    <option value="<%= pd.getName() %>" <%= pd.getName().equals(study.getParticipantCohortProperty()) ? "SELECTED" : "" %>>
                        <%= h(null == pd.getLabel() ? pd.getName() : pd.getLabel()) %>
                    </option>
                <%
                    }
                }
                %>
                </select>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%= buttonImg("Update Assignments")%>
                <%= buttonImg("Clear Assignments", "if (confirm('Refreshing will clear cohort information for all participants.  Continue?')) { document.manageCohorts.clearParticipants.value='true'; return true; } else return false;")%>
            </td>
        </tr>
        </table>
    <%
        WebPartView.endTitleFrame(out);
    }

    // Both manual and automatic pages get the query view
    manager.assertCohortsViewable(study.getContainer(), HttpView.currentContext().getUser());    

    WebPartView.startTitleFrame(out, "Cohorts");
    me.include(view, out);
    WebPartView.endTitleFrame(out);

    // Need all participants and cohorts for both versions
    Map<Participant,Cohort> participant2Cohort = new LinkedHashMap<Participant,Cohort>();
    for (Participant participant : manager.getParticipants(study))
    {
        Cohort cohort = manager.getCohortForParticipant(
                study.getContainer(),
                getViewContext().getUser(),
                participant.getParticipantId());
        participant2Cohort.put(participant, cohort);
    }
    Cohort[] cohortArray = manager.getCohorts(study.getContainer(), HttpView.currentContext().getUser());
    List<Cohort> cohorts = new ArrayList<Cohort>();
    for (Cohort cohort : cohortArray)
    {
        cohorts.add(cohort);
    }
    // Need a null cohort for manual removal of cohort setting
    Cohort nullCohort = new Cohort();
    nullCohort.setRowId(-1);
    nullCohort.setLabel("<Unassigned>");
    cohorts.add(nullCohort);

    // If we're using automatic assignment, the user can't alter the specific assignments,
    // but we want to show a read-only view of them
    String assignmentTitle = study.isManualCohortAssignment() ? "Manual Cohort Assignment" : "Current Cohort Assignments";
    WebPartView.startTitleFrame(out, assignmentTitle);

    if (study.isManualCohortAssignment())
        out.println("<b>Note:</b> Only users with read access to this folder will be able to view Cohort information.<p>");

    %>
    <table>
        <tr>
            <th>Participant ID</th>
            <th>Cohort</th>
        </tr>
    <%
    for (Map.Entry<Participant,Cohort> entry : participant2Cohort.entrySet())
    {


    %>
        <tr>
            <td><%= h(entry.getKey().getParticipantId()) %></td>
            <td><%
                if(!study.isManualCohortAssignment())
                {
                    Cohort cohort = entry.getValue();
                    String label = cohort == null ? "" : cohort.getLabel();
                    %><%=h(label)%><%
                }
                else
                {
                    %>
                <input type="hidden" name="participantId" value="<%=entry.getKey().getParticipantId()%>">
                <select name="cohortId"><%
                    // Need to display selection drop-down for each participant
                    Cohort selectedCohort = entry.getValue();
                    
                    if (selectedCohort == null)
                        selectedCohort = nullCohort;

                    for (Cohort c : cohorts)
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
            %><%= buttonImg("Save", "document.manageCohorts.reshow.value='false'; return true;")%><%
        }

    WebPartView.endTitleFrame(out);

    %>


</form>