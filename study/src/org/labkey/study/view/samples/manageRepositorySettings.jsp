<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    SampleManager.RepositorySettings settings = (SampleManager.RepositorySettings) getModelBean();
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>

<form action="manageRepositorySettings.post" method="POST">
    <table cellspacing="3" class="normal">
        <tr>
            <th align="left">Specimen Repository</th>
        </tr>
        <tr>
            <td align="left"><input type="radio" name="simple" value="true" <%=settings.isSimple() ? "CHECKED" : "" %>> Standard Specimen Repository
                <input type="radio" name="simple" value="false" <%=settings.isSimple() ? "" : "CHECKED" %>> Advanced (External) Specimen Repository</td>
        </tr>
        <tr>
            <td colspan="2" align="left">The standard specimen repository allows you to upload a list of available specimens. The advanced specimen repository
                relies on an external set of tools to track movement of specimens between sites. The advanced system also enables a customizable specimen
                request system.</td>
        </tr>
        <tr>
            <td><%= buttonImg("Submit")%>&nbsp;<%= buttonLink("Back", "", "window.history.back();return false;")%></td>
        </tr>
    </table>
</form>
