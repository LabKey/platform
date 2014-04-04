<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    JspView<String> me = (JspView<String>) HttpView.currentView();
    Container c = getContainer();
    String description = me.getModelBean();
%>
<table>
    <tr><td>You must configure a valid pipeline root before <%=description%>.</td></tr>
    <tr>
        <td>
            <%= button("Pipeline Setup").href(urlProvider(PipelineUrls.class).urlSetup(c)) %>&nbsp;<%= button("Cancel").href(StudyController.ManageStudyAction.class, getContainer()) %>
        </td>
    </tr>
</table>
