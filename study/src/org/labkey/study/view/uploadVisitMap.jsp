<%@ page import="org.labkey.study.controllers.StudyController" %>
<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<labkey:errors/>
VisitMap data can be imported to quickly define a study.  VisitMap data generally follows the form of this sample:
<p><pre>
    0|B|Baseline|1|9 (mm/dd/yy)|0|0| 1 2 3 4 5 6 7 8||99
    10|S|One Week Followup|9|9 (mm/dd/yy)|7|0| 9 10 14||
    20|S|Two Week Followup|9|9 (mm/dd/yy)|14|0| 9 10||
    30|T|Termination Visit|9|9 (mm/dd/yy)|21|0| 11 12||
</pre></p>
<labkey:errors/>
<labkey:form action="<%=h(buildURL(StudyController.UploadVisitMapAction.class))%>" method="post">
    Paste VisitMap content here:<br>
    <textarea name="content" cols="80" rows="30"></textarea><br>
    <%= button("Import").submit(true) %>&nbsp;<%= button("Cancel").href(StudyController.ManageVisitsAction.class, getContainer()) %>
</labkey:form>