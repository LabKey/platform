<%
    /*
    * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Study study = getStudy();
    StudyManager manager = StudyManager.getInstance();
%>
<labkey:errors/>

<form action="manageCohorts.post" name="manageCohorts" method="POST">
    <input type="hidden" name="reshow" value="true">

    <input type="radio" onclick="document.manageCohorts.submit();" name="manualCohortAssignment"
           value="false" <%=study.isManualCohortAssignment() ? "" : "checked"%>>Automatic<br>
    <input type="radio" onclick="document.manageCohorts.submit();" name="manualCohortAssignment"
           value="true" <%=study.isManualCohortAssignment() ? "checked" : ""%>>Manual

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
                DataSetDefinition dataset = StudyManager.getInstance().getDataSetDefinition(study, participantCohortDataSetId.intValue());
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
                <%= generateSubmitButton("Update Assignments")%>
                <%= buttonImg("Clear Assignments", "if (confirm('Refreshing will clear cohort information for all participants.  Continue?')) { document.manageCohorts.clearParticipants.value='true'; return true; } else return false;")%>
            </td>
        </tr>
        </table>
    <%
        WebPartView.endTitleFrame(out);
    }
    %>

</form>