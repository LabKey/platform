<%
    /*
    * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.study.DataSet" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyImpl study = getStudy();
    StudyManager manager = StudyManager.getInstance();
%>
<labkey:errors/>

<form action="manageCohorts.post" name="manageCohorts" method="POST">
    <input type="hidden" name="reshow" value="true">
    <input type="hidden" name="clearParticipants" value="false">
    <input type="hidden" name="updateParticipants" value="false">
<%
    WebPartView.startTitleFrame(out, "Assignment Type", null, "100%", null);
%>
    <script type="text/javascript">
        function setAdvanced(advanced)
        {
            var manualEl = document.getElementById('manualCohortAssignmentEnabled');
            var manual = manualEl && manualEl.checked;
            if (advanced && manual)
            {
                 if (confirm('Enabling advanced cohort tracking will enable automatic assignment.  ' +
                                        'Any manual cohort assignments will be cleared, and you must ' +
                                        'select a study dataset containing cohort assignments.  Continue?'))
                {
                    document.getElementById('manualCohortAssignmentEnabled').checked = false;
                    document.getElementById('manualCohortAssignmentDisabled').checked = true;
                    document.manageCohorts.submit();
                    return true;
                }
            }
            else if (confirm('Changing between simple and advanced cohort assignment requires updating cohort ' +
                               'assignments for all participants.  Update cohort assignments now?'))
            {
                document.manageCohorts.submit();
                return true;
            }
            return false;
        }
    </script>
    <input type="radio" onclick="return setAdvanced(false);" name="advancedCohortSupport"
           value="false" <%=study.isAdvancedCohorts() ? "" : "checked"%>>Simple: Participants are assigned to a single cohort throughout the study.<br>
    <input type="radio" onclick="return setAdvanced(true);" name="advancedCohortSupport"
           value="true" <%=study.isAdvancedCohorts() ? "checked" : ""%>>Advanced: Participants may change cohorts mid-study.  Note that advanced cohort management requires automatic assignment via a study dataset.<br>
<%
    WebPartView.endTitleFrame(out);

    if (!study.isAdvancedCohorts())
    {
        WebPartView.startTitleFrame(out, "Assignment Method", null, "100%", null);
%>
    <input type="radio" onclick="document.manageCohorts.submit();" name="manualCohortAssignment" id="manualCohortAssignmentDisabled"
           value="false" <%=study.isManualCohortAssignment() ? "" : "checked"%>>Automatic: cohort assignments will be read from an existing study dataset.<br>
    <input type="radio" onclick="document.manageCohorts.submit();" name="manualCohortAssignment" id="manualCohortAssignmentEnabled"
           value="true" <%=study.isManualCohortAssignment() ? "checked" : ""%>>Manual: cohort assignments will be made by hand.

    <%
        WebPartView.endTitleFrame(out);
    }
    if (!study.isManualCohortAssignment())
    { // If it's automatic, we need to include the dataset selection widgets
        WebPartView.startTitleFrame(out, "Automatic Participant/Cohort Assignment", null, "100%", null);
    %>
    <b>Note:</b> Only users with read access to the selected dataset will be able to view Cohort information.
<table>
        <tr>
            <th align="right">Participant/Cohort Dataset<%= helpPopup("Participant/Cohort Dataset", "Participants can be assigned to cohorts based on the data in a field of a single dataset.  If set, participant's cohort assignments will be reloaded every time this dataset is re-imported.")%></th>
            <td>
                <select name="participantCohortDataSetId" onchange="document.manageCohorts.participantCohortProperty.value=''; document.manageCohorts.submit()">
                    <option value="-1">[None]</option>
                    <%
                        for (DataSet dataset : manager.getDataSetDefinitions(study))
                        {
                            String selected = (study.getParticipantCohortDataSetId() != null &&
                                    dataset.getDataSetId() == study.getParticipantCohortDataSetId().intValue() ? "selected" : "");
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
            Integer participantCohortDataSetId = study.getParticipantCohortDataSetId();
            if (participantCohortDataSetId == null || participantCohortDataSetId.intValue() < 0)
                descriptors = new PropertyDescriptor[0];
            else
            {
                DataSet dataset = StudyManager.getInstance().getDataSetDefinition(study, participantCohortDataSetId.intValue());
                if (dataset != null)
                    descriptors = OntologyManager.getPropertiesForType(dataset.getTypeURI(), study.getContainer());
                else
                    descriptors = new PropertyDescriptor[0];
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
                <%= buttonImg("Update Assignments", "document.manageCohorts.updateParticipants.value='true'; return true;")%>
                <%= buttonImg("Clear Assignments", "if (confirm('Are you sure you want to clear cohort information for all participants?')) { document.manageCohorts.clearParticipants.value='true'; return true; } else return false;")%>
            </td>
        </tr>
        </table>
    <%
        WebPartView.endTitleFrame(out);
    }
    %>

</form>