<%
/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container proj = getContainer().getProject();

    Study[] studies = StudyManager.getInstance().getAllStudies(proj, getUser());
    if (studies.length == 0)
    {
        out.print("No Studies found in project " + proj.getName());
    }
    int i = 0;
    FolderType studyFolderType = ModuleLoader.getInstance().getFolderType("Study");
%> <div style="vertical-align:top;display:inline-block;margin-right:1em" ><%
    for (Study study: studies)
    {
        ActionURL url;
        if (studyFolderType.equals(study.getContainer().getFolderType()))
            url = studyFolderType.getStartURL(study.getContainer(), getUser());
        else
            url = new ActionURL(StudyController.BeginAction.class, study.getContainer());
        %>
<span class="highlightregion"></span><b><a href="<%=url%>"><%=h(study.getLabel())%></a></b>
            <br>(<%=h(study.getContainer().getPath())%>)
        <br>
<%
    }
%>
