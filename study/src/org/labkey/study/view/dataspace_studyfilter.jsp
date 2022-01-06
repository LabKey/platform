<%
/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.query.DataspaceQuerySchema" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.regex.Pattern" %>
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
    Study study = StudyManager.getInstance().getStudy(getContainer());

    boolean hasStudy = study != null;
    boolean isSharedStudy = study != null && study.isDataspaceStudy();
    String studyLabel = null==study?"":study.getLabel();

    Module immport = ModuleLoader.getInstance().getModule("immport");
    Container project = getContainer().getProject();
    ActionURL finderURL = null;
    if (immport != null && project != null && project.getActiveModules().contains(immport))
        finderURL = new ActionURL("immport", "dataFinderRedirect.view", project);
    List<Study> studies = new ArrayList<>();
    if (hasStudy)
    {
        DataspaceQuerySchema schema = new DataspaceQuerySchema((StudyImpl)study, getUser());
        ContainerFilter cf = schema.getDefaultContainerFilter();
        Collection<GUID> containerIds = cf.getIds();
        if (null != containerIds)
        {
            for (GUID id : containerIds)
            {
                Container c = ContainerManager.getForId(id);
                if (c.equals(project))
                    continue;
                // container filter doesn't know to skip empty study, but this never shows up elsewhere because it's empty
                if (c.getName().toLowerCase().contains("template"))
                    continue;
                Study s = StudyManager.getInstance().getStudy(c);
                if (s != null)
                    studies.add(s);
            }
        }
    }
    Pattern p = Pattern.compile("SDY\\d+");
    studies.sort((s1, s2) -> {
        String n1 = s1.getContainer().getName();
        String n2 = s2.getContainer().getName();
        if (p.matcher(n1).matches() && p.matcher(n2).matches())
            return Integer.compare(Integer.parseInt(n1.substring(3)), Integer.parseInt(n2.substring(3)));
        return n1.compareTo(n2);
    });

    ParticipantGroup sessionGroup = ParticipantGroupManager.getInstance().getSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest());

%>
<h4 style="margin-top:0px; margin-bottom:8px; border-bottom:1px solid #e5e5e5;">Selected  <%= h(study==null ? "Participants" : study.getSubjectNounPlural()) %></h4>

<% if (!hasStudy) { %>
    <div>No study found in this folder</div>
<% } else if (!isSharedStudy) { %>
    <div>Current folder: <%=h(studyLabel)%></div>
<% } else if (studies.size() == 0) { %>
    <div>All studies</div>
<% } else { %>
    <% int count = 1; %>
    Studies:<ul style="list-style-type:none;padding-left:1em;margin:4px;">
    <% for (Study s : studies) { %>
        <% if (studies.size() != count) { %>
            <span data-container="<%=s.getContainer().getEntityId()%>"><%=h(s.getLabel())%>,&nbsp;</span>
        <% } else { %>
            <span data-container="<%=s.getContainer().getEntityId()%>"><%=h(s.getLabel())%></span>
        <% } %>
        <% count++; %>
    <% } %>
    </ul>
<% } %>


<% if (sessionGroup != null) { %>
    <div><%= h(study==null ? "Participants" : study.getSubjectNounPlural()) %>: <%=sessionGroup.getParticipantIds().length%></div>
<% } %>

<div id="summaryData"></div>

<br>
<% if (finderURL != null) { %><%=link("data finder", finderURL)%><% } %>

