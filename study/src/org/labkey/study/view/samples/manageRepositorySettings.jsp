<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
    <div>
        <input type="radio" name="simple" value="true" <%=settings.isSimple() ? "CHECKED" : "" %>> Standard Specimen Repository - allows you to upload a list of available specimens
    </div>
    <div>
        <input type="radio" name="simple" value="false" <%=settings.isSimple() ? "" : "CHECKED" %>> Advanced (External) Specimen Repository -
                relies on an external set of tools to track movement of specimens between sites. The advanced system also enables a customizable specimen
                request system.
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