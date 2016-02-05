<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<labkey:errors/>
You can import a visit map in XML format to quickly configure a study. The visit map XML must match the <%=helpLink("studySerializationFormats", "study serialization format")%> used by study import/export.<br><br>
<labkey:form action="<%=h(buildURL(StudyController.ImportVisitMapAction.class))%>" method="post">
    Paste visit map content here:<br>
    <textarea name="content" cols="80" rows="30"></textarea><br>
    <%= button("Import").submit(true) %>&nbsp;<%= button("Cancel").href(StudyController.ManageVisitsAction.class, getContainer()) %>
</labkey:form>