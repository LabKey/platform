<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<script>
    function deleteStudy_onSubmit()
    {
        var confirmed = document.querySelector("#deleteStudyConfirm").checked ? true : false;
        if (!confirmed)
        {
            alert("Check 'confirm delete' to continue.");
        }
        return confirmed;
    }
</script>
<labkey:form action="<%=h(buildURL(StudyController.DeleteStudyAction.class))%>" method="post" onsubmit="return deleteStudy_onSubmit();">
This will delete all study data in this folder.
<ul>
<%
Collection<String> summaries = ModuleLoader.getInstance().getCurrentModule().getSummary(getStudy().getContainer());
for (String s : summaries)
{
%>
    <li><%=h(s)%></li>
<%
}
%>
</ul>
    <br>
    Check the box below to confirm that you want to delete this study. <br>
<input type=checkbox name=confirm id=deleteStudyConfirm value=true> Confirm Delete<br><br>
<%= button("Delete").submit(true) %> <%= button("Cancel").href(StudyController.ManageStudyAction.class, getContainer()) %>
</labkey:form>