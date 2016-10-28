<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.query.DataspaceQuerySchema" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext4");
    }
%>
<%
    Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
    int uuid = this.getRequestScopedUID();

    boolean hasStudy = study != null;
    boolean isSharedStudy = study != null && study.isDataspaceStudy();
    String studyLabel = null==study?"":study.getLabel();

    Module immport = ModuleLoader.getInstance().getModule("immport");
    Container project = getContainer().getProject();
    ActionURL finderURL = null;
    if (immport != null && project != null && project.getActiveModules().contains(immport))
        finderURL = new ActionURL("immport", "dataFinder.view", project);

    String key = DataspaceQuerySchema.SHARED_STUDY_CONTAINER_FILTER_KEY + getContainer().getProject().getRowId();
    Object o = getViewContext().getSession().getAttribute(key);
    List<Study> studies = new ArrayList<>();
    if (o instanceof List)
    {
        List<GUID> containerIds = (List)o;
        for (GUID id : containerIds)
        {
            Container c = ContainerManager.getForId(id);
            Study s = StudyManager.getInstance().getStudy(c);
            if (s != null)
                studies.add(s);
        }
    }

    ParticipantGroup sessionGroup = ParticipantGroupManager.getInstance().getSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest());

%>
<h4 style="margin-top:0px; margin-bottom:8px; border-bottom:1px solid #e5e5e5;">Selected Subjects</h4>

<% if (!hasStudy) { %>
    <div>No study found in this folder</div>
<% } else if (!isSharedStudy) { %>
    <div>Current folder: <%=h(studyLabel)%></div>
<% } else if (studies.size() == 0) { %>
    <div>All studies</div>
<% } else { %>
    Studies:<ul style="list-style-type:none;padding-left:1em;margin:4px;">
    <% for (Study s : studies) { %>
        <li data-container="<%=s.getContainer().getEntityId()%>"><%=h(s.getLabel())%></li>
    <% } %>
    </ul>
<% } %>


<% if (sessionGroup != null) { %>
    <div>Participants: <%=sessionGroup.getParticipantIds().length%></div>
<% } %>

<div id="summaryData"></div>

<br>
<% if (finderURL != null) { %><%=this.textLink("data finder", finderURL)%><% } %>

