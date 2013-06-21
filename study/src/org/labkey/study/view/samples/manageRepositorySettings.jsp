<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.study.samples.settings.RepositorySettings" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    RepositorySettings settings = (RepositorySettings) getModelBean();
    Study study = StudyService.get().getStudy(settings.getContainer());
%>
<labkey:errors/>

<%
   if (study != null && !study.isAncillaryStudy() && !study.isSnapshotStudy())
   {
%>
<form action="<%=h(buildURL(SpecimenController.ManageRepositorySettingsAction.class))%>" method="POST">
    <table width="100%">
        <tr><td class="labkey-announcement-title" align="left"><span>Repository Type</span></td></tr>
        <tr><td class="labkey-title-area-line"></td></tr>
        <tr><td>
            <div style="padding-bottom: 1em">
                <input type="radio" name="simple" value="true" <%=text(settings.isSimple() ? "CHECKED" : "") %> onChange="document.getElementById('enableRequestsBlock').style.display = 'none';">
                <em>Standard Specimen Repository</em>: allows you to upload a list of available specimens
            </div>
            <div>
                <input type="radio" name="simple" value="false" <%=text(settings.isSimple() ? "" : "CHECKED") %> onChange="document.getElementById('enableRequestsBlock').style.display = 'block';">
                <em>Advanced (External) Specimen Repository</em>:
                        relies on an external set of tools to track movement of specimens between locations. The advanced system also optionally enables a customizable specimen
                        request system.
            </div>
        </td></tr>
    </table>

    <div id="enableRequestsBlock" style="padding-bottom: 1em;;display:<%= h(settings.isSimple() ? "none" : "block") %>">
        <table width="100%">
            <tr><td class="labkey-announcement-title" align="left"><span>Specimen Data</span></td></tr>
            <tr><td class="labkey-title-area-line"></td></tr>
            <tr><td>
                <div style="padding-bottom: 1em">
                    <input type="radio" name="specimenDataEditable" value="false"  <%=text(!settings.isSpecimenDataEditable() ? "CHECKED" : "") %>><em>Read-only</em>:
                    Specimen data is read-only and can only be changed by importing a specimen archive.
                </div>
                <div>
                    <input type="radio" name="specimenDataEditable" value="true"  <%=text(settings.isSpecimenDataEditable() ? "CHECKED" : "") %>><em>Editable</em>:
                    Specimen data is editable.
                </div>
            </td></tr>
            <tr><td class="labkey-announcement-title" align="left"><span>Specimen Requests</span></td></tr>
            <tr><td class="labkey-title-area-line"></td></tr>
            <tr><td>
                <div style="padding-bottom: 1em">
                    <input type="radio" name="enableRequests" value="true"  <%=text(settings.isEnableRequests() ? "CHECKED" : "") %>><em>Enabled</em>:
                    The system will allow users with appropriate permissions to request specimens, and will show counts of available specimens.
                </div>
                <div style="padding-bottom: 1em">
                    <input type="radio" name="enableRequests" value="false"  <%=text(!settings.isEnableRequests() ? "CHECKED" : "") %>><em>Disabled</em>:
                    Specimen request features such as the specimen shopping cart will not appear in the specimen tracking user interface.
                </div>
            </td></tr>
       </table>
    </div>
    <br/>
    <div>
        <%= generateSubmitButton("Submit")%>&nbsp;<%= generateButton("Back", buildURL(SpecimenController.ManageRepositorySettingsAction.class), "window.history.back();return false;")%>
    </div>
</form>
<%
   }
   else
   {
%>
<p><em>NOTE: specimen repository and request settings are not available for ancillary or published studies.</em></p>
<%= generateButton("Back", buildURL(SpecimenController.ManageRepositorySettingsAction.class), "window.history.back();return false;")%>
<%
   }
%>