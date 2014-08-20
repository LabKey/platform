<%
/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.DemoMode" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    boolean demoMode = DemoMode.isDemoMode(getContainer(), getUser());
    Study study = StudyManager.getInstance().getStudy(getContainer());
    _HtmlString subjectNameFiltered = new _HtmlString(study.getSubjectNounSingular().toLowerCase());
%>
<labkey:form action="" method="post">
    <table width="80%">
        <tr>
            <td>This study is currently <%=text(demoMode ? "" : "not ")%>in demo mode.</td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <td><%  if (!demoMode)
                    {  %>
                Demo mode temporarily obscures <%=subjectNameFiltered%> IDs in many pages of the study, making it easier to create screenshots
                or present live demos in situations where <%=subjectNameFiltered%> IDs can't be shown. Note that demo mode will not hide every
                <%=subjectNameFiltered%> ID throughout the entire study; places where <%=subjectNameFiltered%> IDs may continue to be visible include:

                <ul>
                    <li>The browser address bar when viewing a specific <%=subjectNameFiltered%>
                    <li>The browser status bar when hovering over certain links
                    <li>Free-form text that happens to include <%=subjectNameFiltered%> IDs, for example, comments or notes fields, PDFs,
                        wikis, or messages
                </ul>
                As a result, you should<%
                    }
                    else
                    {  %>
                Remember to<%    
                    }
                %> hide your browser's address bar and status bar (most popular browsers support this) before giving a live
                demo. You should also plan and practice your demo carefully to avoid exposing <%=subjectNameFiltered%> IDs.
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <td><input type="hidden" name="mode" value="<%=!demoMode%>"><%= button((demoMode ? "Leave" : "Enter") + " Demo Mode").submit(true) %>&nbsp;<%= button("Done").href(ManageStudyAction.class, getContainer()) %></td>
        </tr>
    </table>
</labkey:form>
