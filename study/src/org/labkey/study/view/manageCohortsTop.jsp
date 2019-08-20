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
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Collections" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyImpl study = getStudy();
    StudyManager manager = StudyManager.getInstance();
    String subjectNounSingle = StudyService.get().getSubjectNounSingular(getContainer());
    String subjectNounPlural = StudyService.get().getSubjectNounPlural(getContainer());
%>
<labkey:errors/>

<labkey:form action="<%=h(buildURL(CohortController.ManageCohortsAction.class))%>" name="manageCohorts" method="POST">
    <input type="hidden" name="reshow" value="true">
    <input type="hidden" name="clearParticipants" value="false">
    <input type="hidden" name="updateParticipants" value="false">
    <%
        // Continuous studies don't populate study.ParticipantVisit or study.Visit, so it's not yet possible to
        // do advanced cohort calculations for continuous studies.  This support could be added in the future.
        if (study.getTimepointType() != TimepointType.CONTINUOUS)
        {
    %>
    <div class="labkey-announcement-title" style="padding-top: 0;">
        <span>Assignment Mode</span>
    </div>
    <script type="text/javascript">
        function setAdvanced(advanced)
        {
            var manualEl = document.getElementById('manualCohortAssignmentEnabled');
            var manual = manualEl && manualEl.checked;
            if (advanced && manual)
            {
                if (confirm('Enabling advanced cohort mode will enable automatic assignment.  ' +
                        'Any manual cohort assignments will be cleared, and you must ' +
                        'select a study dataset containing cohort assignments.  Continue?'))
                {
                    document.getElementById('manualCohortAssignmentEnabled').checked = false;
                    document.getElementById('manualCohortAssignmentDisabled').checked = true;
                    document.manageCohorts.submit();
                    return true;
                }
            }
            else if (confirm('Changing between simple and advanced modes requires updating cohort ' +
                    'assignments for all <%= h(subjectNounPlural.toLowerCase()) %>.  Update cohort assignments now?'))
            {
                document.manageCohorts.submit();
                return true;
            }
            return false;
        }
    </script>
    <input type="radio" onclick="return setAdvanced(false);" name="advancedCohortSupport" id="simpleCohorts"
           value="false"<%=checked(!study.isAdvancedCohorts())%>>Simple: <%= h(subjectNounPlural) %> are
    assigned to a single cohort throughout the study.<br>
    <input type="radio" onclick="return setAdvanced(true);" name="advancedCohortSupport" id="advancedCohorts"
           value="true" <%=checked(study.isAdvancedCohorts())%>>Advanced: <%= h(subjectNounPlural) %> may
    change cohorts mid-study. Note that advanced cohort management requires automatic assignment via a study
    dataset.<br>
    <%
        }

        if (!study.isAdvancedCohorts())
        {
    %>
    <div class="labkey-announcement-title">
        <span>Assignment Type</span>
    </div>
    <input type="radio" onclick="document.manageCohorts.submit();" name="manualCohortAssignment"
           id="manualCohortAssignmentDisabled"
           value="false"<%=checked(!study.isManualCohortAssignment())%>>Automatic: cohort assignments will be
    read from an existing study dataset.<br>
    <input type="radio" onclick="document.manageCohorts.submit();" name="manualCohortAssignment"
           id="manualCohortAssignmentEnabled"
           value="true"<%=checked(study.isManualCohortAssignment())%>>Manual: cohort assignments will be made
    by hand.

    <%
        }
        if (!study.isManualCohortAssignment())
        { // If it's automatic, we need to include the dataset selection widgets
    %>
    <div class="labkey-announcement-title">
        <span>Automatic <%=h(subjectNounSingle)%>/Cohort Assignment</span>
    </div>
    <b>Note:</b> Only users with read access to the selected dataset will be able to view Cohort information.
    <table class="lk-fields-table">
        <tr>
            <th align="right"><%= h(subjectNounSingle) %>/Cohort
                Dataset<%= helpPopup(subjectNounSingle + "/Cohort Dataset",
                        subjectNounPlural + " can be assigned to cohorts based on the data in a field of a single dataset.  " +
                                "If set, cohort assignments will be reloaded for all " + subjectNounPlural + " every time this dataset is re-imported.")%>
            </th>
            <td>
                <select name="participantCohortDatasetId"
                        onchange="document.manageCohorts.participantCohortProperty.value=''; document.manageCohorts.submit()">
                    <option value="-1">[None]</option>
                    <%
                        for (Dataset dataset : manager.getDatasetDefinitions(study))
                        {
                            boolean selected = (study.getParticipantCohortDatasetId() != null &&
                                    dataset.getDatasetId() == study.getParticipantCohortDatasetId());
                    %>
                    <option value="<%= dataset.getDatasetId() %>"<%=selected(selected)%>><%= h(dataset.getLabel()) %>
                    </option>
                    <%
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
                        List<PropertyDescriptor> descriptors;
                        Integer participantCohortDatasetId = study.getParticipantCohortDatasetId();
                        if (participantCohortDatasetId == null || participantCohortDatasetId < 0)
                            descriptors = Collections.emptyList();
                        else
                        {
                            Dataset dataset = StudyManager.getInstance().getDatasetDefinition(study, participantCohortDatasetId);
                            if (dataset != null)
                                descriptors = OntologyManager.getPropertiesForType(dataset.getTypeURI(), study.getContainer());
                            else
                                descriptors = Collections.emptyList();
                        }
                        for (PropertyDescriptor pd : descriptors)
                        {
                            if (pd.getPropertyType() == PropertyType.STRING) // only strings can be cohort labels
                            {
                    %>
                    <option value="<%= h(pd.getName()) %>"<%=selected(pd.getName().equals(study.getParticipantCohortProperty()))%>>
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
            <td colspan="2">
                <%= button("Update Assignments").submit(true).onClick("document.manageCohorts.updateParticipants.value='true'; return true;") %>
                <%= button("Clear Assignments").submit(true).onClick("if (confirm('Are you sure you want to clear cohort information for all " + h(subjectNounPlural) + "?')) { document.manageCohorts.clearParticipants.value='true'; return true; } else return false;") %>
            </td>
        </tr>
    </table>
    <%
        }
    %>

</labkey:form>