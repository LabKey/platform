<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController.CreateFolderAction" %>
<%@ page import="org.labkey.core.admin.AdminController.DeleteFolderAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ExportFolderAction" %>
<%@ page import="org.labkey.core.admin.AdminController.FolderAliasesAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersAction" %>
<%@ page import="org.labkey.core.admin.AdminController.RenameFolderAction" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.admin.FolderSettingsAction" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.core.admin.ProjectSettingsAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<Object> me = (JspView<Object>) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    User user = HttpView.currentContext().getUser();
%>
<table>
    <%
    if (c.hasPermission(user, AdminPermission.class))
    {
    %>
    <tr>
        <th align="left">Aliases</th>
        <td>Manage aliases for the current folder</td>
        <td><%= textLink("Manage Aliases", FolderAliasesAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Create Subfolder</th>
        <td>Create a subfolder for the current folder</td>
        <td><%= textLink("Create Subfolder", CreateFolderAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Delete</th>
        <td>Delete the current folder</td>
        <td><%= textLink("Delete Folder", DeleteFolderAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Folder Settings</th>
        <td>Manage folder settings (folder type, missing value indicators, etc.)</td>
        <td><%= textLink("Folder Settings", FolderSettingsAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Groups/Permissions</th>
        <td>Manage groups and permissions for the current folder</td>
        <td><%= textLink("Manage Permissions", SecurityController.ProjectAction.class) %></td>
    </tr>

    <% if (c.isProject())
    {
    %>
    <tr>
        <th align="left">Project Settings</th>
        <td>Manage project settings (properties, resources, menu bar, files)</td>
        <td><%= textLink("Project Settings", ProjectSettingsAction.class) %></td>
    </tr>
    <%
    }
    %>
    <tr>
        <th align="left">Rename</th>
        <td>Rename the current folder</td>
        <td><%= textLink("Rename Folder", RenameFolderAction.class) %></td>
    </tr>
    <tr>
        <th align="left">Manage Subfolders</th>
        <td>Manage and organize subfolders (move, reorder, etc.)</td>
        <td><%= textLink("Manage Subfolders", ManageFoldersAction.class) %></td>
    </tr>
    <%
    }
    %>
</table><br/>
<%=generateButton("Export Folder", ExportFolderAction.class)%>
<% ActionURL importUrl = new ActionURL("pipeline", "importFolder", c);%>
<%=generateButton("Import Folder", importUrl)%>
