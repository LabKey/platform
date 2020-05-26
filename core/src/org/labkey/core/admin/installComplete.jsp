<%
/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.Constants" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    // Build links for what's new and the release notes
    String versionSuffix = Constants.getDocumentationVersion().replace(".", "");
    boolean newInstall = ModuleLoader.getInstance().isNewInstall();
%>
<p>Congratulations! Your LabKey Server installation <%=h(newInstall ? "is ready to use" : "has been successfully upgraded")%>.</p>

<ul>
    <li style="margin-bottom: 10px;">
        <a href="<%= h(ContainerManager.getHomeContainer().getStartURL(getUser())) %>">Go to the server's Home page</a>
    </li>

    <li style="margin-bottom: 10px;">
        <%=helpLink("whatsnew" + versionSuffix, "Learn what's new in this version of LabKey Server")%>
    </li>

    <li style="margin-bottom: 10px;">
        <%=helpLink("releaseNotes" + versionSuffix, "Read the full release notes for this version of LabKey Server")%>
    </li>
</ul>