<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyController.StudyPropertiesForm form = (StudyController.StudyPropertiesForm) getModelBean();
    if (!getViewContext().hasPermission(AdminPermission.class))
    {%>
        A study has not been created in this folder. Please contact an administrator.
<%  } else { %>
<labkey:errors/>
<form action="createStudy.post" method="POST">
    <table>
        <tr>
            <td colspan="2" class="labkey-announcement-title"><span>Look and Feel</span></td>
        </tr>
        <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
        <tr>
            <th style="text-align:left;width:18em">Study Label</th>
            <td align="left"><input type="text" size="40" name="label" value="<%= h(form.getLabel()) %>"></td>
        </tr>
        <tr>
            <th style="text-align:left;width:18em">Subject Noun (Singular)<%=helpPopup("Subject Noun (Singular)", "The singular noun used to identify subjects.  Examples include \"Participant\", \"Mouse\", or \"Yeast\".  This value cannot be changed after study creation.", true)%></th>
            <td align="left"><input type="text" size="40" name="subjectNounSingular" value="<%= h(form.getSubjectNounSingular()) %>"></td>
        </tr>
        <tr>
            <th style="text-align:left;width:18em">Subject Noun (Plural)<%=helpPopup("Subject Noun (Plural)", "The plural noun used to identify subjects.  Examples include \"Participants\", \"Mice\", or \"Yeasts\".  This value cannot be changed after study creation.", true)%></th>
            <td align="left"><input type="text" size="40" name="subjectNounPlural" value="<%= h(form.getSubjectNounPlural()) %>"></td>
        </tr>
        <tr>
            <th style="text-align:left;width:18em">Subject Column Name<%=helpPopup("Subject Column Name", "The column header for subject IDs.  Examples include \"ParticipantId\", \"MouseId\", or \"YeastId\".  This value cannot be changed after study creation.", true)%></th>
            <td align="left"><input type="text" size="40" name="subjectColumnName" value="<%= h(form.getSubjectColumnName()) %>"></td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-announcement-title"><span>Visit/Timepoint Tracking</span></td>
        </tr>
        <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
        <tr>
            <th style="text-align:left;width:18em">Timepoint Style<%=helpPopup("Timepoint Styles", "<p>Timepoints in the study may be defined using dates, or using pre-determined Visits assigned by the study administrator.</p><p>When using visits, administrators assign a label and a range of numerical \"Sequence Numbers\" that are grouped into visits.</p><p>If using dates, data can be grouped by day or week.</p>", true)%></th>
            <td align="left">
                <input type="radio" name="timepointType" value="<%=TimepointType.DATE%>" <%=form.getTimepointType() == TimepointType.DATE ? "CHECKED" : ""%>> Dates &nbsp;&nbsp;
                <input type="radio" name="timepointType" value="<%=TimepointType.VISIT%>" <%=form.getTimepointType() == TimepointType.VISIT ? "" : "CHECKED"%>> Assigned Visits
            </td>
        </tr>
        <tr>
            <th style="text-align:left;width:18em">Start Date<%=helpPopup("Start Date", "A start date is required for studies that are date based.")%></th>
            <td align="left"><input type="text" name="startDate" value="<%=h(DateUtil.formatDate(form.getStartDate()))%>">
            </td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-announcement-title"><span>Specimen Management</span></td>
        </tr>
        <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
        <tr>
            <th style="text-align:left;width:18em">Repository Type</th>
            <td align="left"><input type="radio" name="simpleRepository" value="true" <%=form.isSimpleRepository() ? "CHECKED" : "" %>> Standard Specimen Repository
                <input type="radio" name="simpleRepository" value="false" <%=form.isSimpleRepository() ? "" : "CHECKED" %>> Advanced (External) Specimen Repository</td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td align="left"><p><br>The standard specimen repository allows you to upload a list of available specimens. The advanced specimen repository
                relies on an external set of tools to track movement of specimens between sites. The advanced system also enables a customizable specimen
                request system.</p></td>
        </tr>
        <tr>
            <td colspan="2" class="labkey-announcement-title"><span>Security</span></td>
        </tr>
        <tr><td colspan="3" class="labkey-title-area-line"></td></tr>
        <tr>
            <th style="text-align:left;width:18em">Security Mode<%=helpPopup("Study Security", SecurityType.getHTMLDescription(), true)%></th>
            <td align="left">
                <select name="securityString">
                    <%
                        for (SecurityType securityType : SecurityType.values())
                        {
                            %>
                            <option value="<%= securityType.name() %>"><%= securityType.getLabel() %></option>
                            <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td><br><br><%= generateSubmitButton("Create Study")%>&nbsp;<%= generateButton("Back", "#", "window.history.back();return null;")%></td>
            <td>&nbsp;</td>
        </tr>
    </table>
</form>
<%  } %>