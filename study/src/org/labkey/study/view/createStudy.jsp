<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyController.StudyPropertiesForm form = (StudyController.StudyPropertiesForm) getModelBean();
    if (!getViewContext().hasPermission(AdminPermission.class))
    {%>
        This folder does not contain a study. Please contact an administrator.
<%  } else { %>

<style type="text/css">
    .lk-study-property {
        text-align: left;
        width: 18em;
    }
</style>

<labkey:errors/>
<labkey:form action="<%=h(buildURL(StudyController.CreateStudyAction.class))%>" method="POST">
        <labkey:panel title="Look and Feel Properties">
            <table class="lk-fields-table">
                <tr>
                    <td class="lk-study-property">Study Label</td>
                    <td align="left"><input type="text" size="40" name="label" value="<%= h(form.getLabel()) %>"></td>
                </tr>
                <tr>
                    <td class="lk-study-property">Subject Noun (Singular)<%=helpPopup("Subject Noun (Singular)", "The singular noun used to identify subjects.  Examples include \"Participant\", \"Mouse\", or \"Yeast\".  This value cannot be changed after study creation.", true)%></td>
                    <td align="left"><input type="text" size="40" name="subjectNounSingular" value="<%= h(form.getSubjectNounSingular()) %>"></td>
                </tr>
                <tr>
                    <td class="lk-study-property">Subject Noun (Plural)<%=helpPopup("Subject Noun (Plural)", "The plural noun used to identify subjects.  Examples include \"Participants\", \"Mice\", or \"Yeasts\".  This value cannot be changed after study creation.", true)%></td>
                    <td align="left"><input type="text" size="40" name="subjectNounPlural" value="<%= h(form.getSubjectNounPlural()) %>"></td>
                </tr>
                <tr>
                    <td class="lk-study-property">Subject Column Name<%=helpPopup("Subject Column Name", "The column header for subject IDs.  Examples include \"ParticipantId\", \"MouseId\", or \"YeastId\".  This value cannot be changed after study creation.", true)%></td>
                    <td align="left"><input type="text" size="40" name="subjectColumnName" value="<%= h(form.getSubjectColumnName()) %>"></td>
                </tr>
            </table>
        </labkey:panel>

<% if (!form.isShareVisits()) { %>
        <labkey:panel title="Visit/Timepoint Tracking">
            <p>
                Timepoints in the study may be defined using dates, or using pre-determined Visits assigned by the study administrator.
                Alternately, if the study is ongoing without a strong concept of visit, a continuous study can be chosen.
            </p>
            <table class="lk-fields-table">
                <tr>
                    <td class="lk-study-property">Timepoint Style<%=helpPopup("Timepoint Styles", "<p>When using visits, administrators assign a label and a range of numerical \"Sequence Numbers\" that are grouped into visits.</p><p>If using dates, data can be grouped by day or week.</p>", true)%></td>
                    <td align="left">
                        <input type="radio" name="timepointType" id="dateTimepointType" value="<%=TimepointType.DATE%>"<%=checked(form.getTimepointType() == TimepointType.DATE)%> onchange="document.getElementById('defaultDurationRow').style.display = document.getElementById('dateTimepointType').checked ? 'table-row' : 'none'; document.getElementById('defaultDateRow').style.display = document.getElementById('continuousTimepointType').checked ? 'none' : 'table-row';"> Dates &nbsp;&nbsp;
                        <input type="radio" name="timepointType" value="<%=TimepointType.VISIT%>"<%=checked(form.getTimepointType() == TimepointType.VISIT || form.getTimepointType() == null)%> onchange="document.getElementById('defaultDurationRow').style.display = document.getElementById('dateTimepointType').checked ? 'table-row' : 'none'; document.getElementById('defaultDateRow').style.display = document.getElementById('continuousTimepointType').checked ? 'none' : 'table-row';"> Assigned Visits  &nbsp;&nbsp;
                        <input type="radio" name="timepointType" id="continuousTimepointType" value="<%=TimepointType.CONTINUOUS%>"<%=checked(form.getTimepointType() == TimepointType.CONTINUOUS)%> onchange="document.getElementById('defaultDurationRow').style.display = document.getElementById('dateTimepointType').checked ? 'table-row' : 'none'; document.getElementById('defaultDateRow').style.display = document.getElementById('continuousTimepointType').checked ? 'none' : 'table-row';"> Continuous
                    </td>
                </tr>
                <tr id="defaultDateRow" style="display: <%= text(form.getTimepointType() == TimepointType.CONTINUOUS ? "none" : "table-row") %>">
                    <td class="lk-study-property">Start Date<%=helpPopup("Start Date", "A start date is required for studies that are date based.")%></td>
                    <td align="left"><input type="text" name="startDate" value="<%=formatDate(form.getStartDate())%>">
                    </td>
                </tr>
                <tr id="defaultDurationRow" style="display: <%= text(form.getTimepointType() != null && !form.getTimepointType().isVisitBased() ? "table-row" : "none") %>">
                    <td class="lk-study-property">Default Timepoint Duration<%=helpPopup("Default Timepoint Duration", "The default timepoint duration will determine the number of days included in automatically created timepoints.")%></td>
                    <td align="left"><input type="text" name="defaultTimepointDuration" value="<%=form.getDefaultTimepointDuration()%>">
                    </td>
                </tr>
            </table>
        </labkey:panel>
<% } else { %>
    <input type="hidden" name="timepointType" value="<%= form.getTimepointType() == TimepointType.VISIT || form.getTimepointType() == null ? TimepointType.VISIT : form.getTimepointType() %>" >
    <input type="hidden" name="startDate" value="<%=formatDate(form.getStartDate())%>">
    <input type="hidden" name="defaultTimepointDuration" value="<%=form.getDefaultTimepointDuration()%>">
<% } %>

        <labkey:panel title="Specimen Management">
            <p>
                The standard specimen repository allows you to upload a list of available specimens. The advanced specimen repository
                relies on an external set of tools to track movement of specimens between locations. The advanced system also enables a customizable specimen
                request system.
            </p>
            <table class="lk-fields-table">
                <tr>
                    <td style="vertical-align:top;" class="lk-study-property">Repository Type</td>
                    <td align="left">
                        <input type="radio" name="simpleRepository" value="true"<%=checked(form.isSimpleRepository())%>> Standard Specimen Repository<br/>
                        <input type="radio" name="simpleRepository" value="false"<%=checked(!form.isSimpleRepository())%>> Advanced (External) Specimen Repository
                    </td>
                </tr>
            </table>
        </labkey:panel>

        <labkey:panel title="Security">
            <table class="lk-fields-table">
                <tr>
                    <td class="lk-study-property">Security Mode<%=helpPopup("Study Security", SecurityType.getHTMLDescription(), true)%></td>
                    <td align="left">
                        <select name="securityString">
                            <%
                                for (SecurityType securityType : SecurityType.values())
                                {
                            %>
                                    <option value="<%=h(securityType.name())%>"><%=h(securityType.getLabel())%></option>
                            <%
                                }
                            %>
                        </select>
                    </td>
                </tr>
            </table>
        </labkey:panel>

<%
    boolean isProject = getContainer().isProject();

    if (isProject)
    {
        // Issue 22690: Disallow creating a shared study if child studies already exist
        boolean allowCreateSharedStudy = StudyManager.getInstance().getAllStudies(getContainer(), getUser()).isEmpty();
%>
        <labkey:panel title="Shared Study Properties">
            <p>
                Enable sharing of dataset definitions created in this project-level study.
                If this option is enabled, all studies in this project will see the datasets defined in the root folder of the project.
            </p>
            <table class="lk-fields-table">
                <tr>
                    <td class="lk-study-property">Shared Datasets</td>
                    <td align="left">
                        <input type="radio" name="shareDatasets" value="true" <%=disabled(!allowCreateSharedStudy)%>> enabled &nbsp;&nbsp;&nbsp;
                        <input type="radio" name="shareDatasets" value="false" checked <%=disabled(!allowCreateSharedStudy)%>> disabled
                    </td>
                </tr>
                <tr>
                    <td>&nbsp;</td>
                    <td align="left">
                        <% if (!allowCreateSharedStudy) { %><span class="labkey-error">Shared datasets can't be enabled if child studies already exist.</span><%}%>
                    </td>
                </tr>
            </table>
            <p>
                Enable sharing of visits created in this project-level study.
                If this option is enabled, all studies in this project will see the visits defined in the root folder of the project.
            </p>
            <table class="lk-fields-table">
                <tr>
                    <td class="lk-study-property">Shared Timepoints</td>
                    <td align="left">
                        <input type="radio" name="shareVisits" value="true" <%=disabled(!allowCreateSharedStudy)%>> enabled &nbsp;&nbsp;&nbsp;
                        <input type="radio" name="shareVisits" value="false" checked <%=disabled(!allowCreateSharedStudy)%>> disabled
                    </td>
                </tr>
                <tr>
                    <td>&nbsp;</td>
                    <td align="left">
                        <% if (!allowCreateSharedStudy) { %><span class="labkey-error">Shared timepoints can't be enabled if child studies already exist.</span><%}%>
                    </td>
                </tr>
            </table>
        </labkey:panel>
<%}%>

    <%= button("Create Study").disableOnClick(true).submit(true) %>
    <%= generateBackButton()%>
</labkey:form>
<%  } %>
