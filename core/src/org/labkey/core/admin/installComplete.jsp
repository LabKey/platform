<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AdminUrls adminURLs = PageFlowUtil.urlProvider(AdminUrls.class);
    ViewContext viewContext = HttpView.currentContext();
%>
<style type="text/css">
li {
    margin-bottom: 10px;
}
</style>
<p>Congratulations! Your LabKey Server installation is ready to use.</p>

<p>What would you like to do next?</p>

<ul>
    <li>
        <a href="<%= h(adminURLs.getAdminConsoleURL()) %>">Customize LabKey Server</a>
        (<a href="<%= h(adminURLs.getProjectSettingsURL(org.labkey.api.data.ContainerManager.getRoot())) %>">appearance</a>,
        security, <a href="<%= h(adminURLs.getCustomizeSiteURL()) %>">site settings</a>, etc)
    </li>

    <li>
        <a href="<%= h(adminURLs.getCreateProjectURL()) %>">Create a new project</a>
        (analyze MS1, MS2, flow cytometry, plate-based assay data; organize data in a study;
        create messages boards, wikis, or issue trackers, etc).  This is the most common for new servers.
    </li>

    <li>
        <a href="<%= h(ContainerManager.getHomeContainer().getStartURL(viewContext.getUser())) %>">Skip these steps and go to the Home page</a>
    </li>
</ul>