<%
/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.labkey.study.specimen.settings.RepositorySettings" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    RepositorySettings settings = (RepositorySettings) getModelBean();
    Study study = StudyService.get().getStudy(settings.getContainer());
    ActionURL specimenDetailURL = new ActionURL();
    specimenDetailURL.setContainer(getContainer());
    specimenDetailURL.addParameter("schemaName", "study");
    specimenDetailURL.addParameter("query.queryName", "SpecimenDetail");
%>
<labkey:errors/>

<%
   if (study != null && !study.hasSourceStudy() && !study.isSnapshotStudy())
   {
%>
<labkey:form action="<%=h(buildURL(SpecimenController.ManageRepositorySettingsAction.class))%>" method="POST">
    <table width="100%">
        <tr><td class="labkey-announcement-title" align="left"><span>Repository Type</span></td></tr>
        <tr><td class="labkey-title-area-line"></td></tr>
        <tr><td>
            <div style="padding-bottom: 1em">
                <input type="radio" name="simple" value="true"<%=checked(settings.isSimple())%> onChange="document.getElementById('enableRequestsBlock').style.display = 'none';">
                <em>Standard Specimen Repository</em>: allows you to upload a list of available specimens
            </div>
            <div>
                <input type="radio" name="simple" value="false"<%=checked(!settings.isSimple())%> onChange="document.getElementById('enableRequestsBlock').style.display = 'block';">
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
                    <input type="radio" name="specimenDataEditable" value="false"<%=checked(!settings.isSpecimenDataEditable())%>><em>Read-only</em>:
                    Specimen data is read-only and can only be changed by importing a specimen archive.
                </div>
                <div>
                    <input type="radio" name="specimenDataEditable" value="true"<%=checked(settings.isSpecimenDataEditable())%>><em>Editable</em>:
                    Specimen data is editable. Note: Vials may be deleted only from the query view:
                    <%=textLink("Specimen Detail", PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(specimenDetailURL))%>
                </div>
            </td></tr>
            <tr><td class="labkey-announcement-title" align="left"><span>Specimen Requests</span></td></tr>
            <tr><td class="labkey-title-area-line"></td></tr>
            <tr><td>
                <div style="padding-bottom: 1em">
                    <input type="radio" name="enableRequests" value="true"<%=checked(settings.isEnableRequests())%>><em>Enabled</em>:
                    The system will allow users with appropriate permissions to request specimens, and will show counts of available specimens.
                </div>
                <div style="padding-bottom: 1em">
                    <input type="radio" name="enableRequests" value="false"<%=checked(!settings.isEnableRequests())%>><em>Disabled</em>:
                    Specimen request features such as the specimen shopping cart will not appear in the specimen tracking user interface.
                </div>
            </td></tr>
       </table>
    </div>
    <br/>
    <div>
        <%= button("Submit").submit(true) %>&nbsp;<%= button("Back").href(buildURL(SpecimenController.ManageRepositorySettingsAction.class)).onClick("window.history.back();return false;") %>
    </div>
</labkey:form>
<%
   }
   else
   {
%>
<p>Specimen repository and request settings are not available for ancillary or published studies.</p>
<%= button("Back").href(buildURL(SpecimenController.ManageRepositorySettingsAction.class)).onClick("window.history.back();return false;") %>
<%
   }
%>