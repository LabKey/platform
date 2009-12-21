<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.util.ContainerTreeSelected"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.core.admin.AdminController.*"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<ManageFoldersForm> me = (HttpView<ManageFoldersForm>) HttpView.currentView();
    final ViewContext ctx = me.getViewContext();
    final Container c = ctx.getContainer();
    final ActionURL currentUrl = ctx.cloneActionURL();
    final ContainerTreeSelected ct = new ContainerTreeSelected(c.getProject().getName(), ctx.getUser(), AdminPermission.class, currentUrl, "managefolders");
    Container parent = c.getParent();
    List<Container> siblings = parent == null ? null : parent.getChildren();
    boolean hasSiblings = siblings != null && siblings.size() > 1;

    ct.setCurrent(c);
    ct.setInitialLevel(1);
%>

<table class="labkey-data-region">
    <tr><td style="padding-left:0">Pick a folder to modify:</td></tr>
    <tr><td>&nbsp;</td></tr>
    <%= ct.render()%>
</table><br>

<table><tr>
<%
    ActionURL rename  = urlFor(RenameFolderAction.class);
    ActionURL move    = urlFor(ShowMoveFolderTreeAction.class);
    ActionURL create  = urlFor(CreateFolderAction.class);
    ActionURL delete  = urlFor(DeleteFolderAction.class);
    ActionURL reorder = urlFor(ReorderFoldersAction.class);
    ActionURL aliases = urlFor(FolderAliasesAction.class);

    if (!ContainerManager.getHomeContainer().equals(c) && !ContainerManager.getSharedContainer().equals(c))
    {
%>
        <td><%= generateButton("Rename", rename)%></td>
        <td><%= generateButton("Move", move)%></td>
        <td><%= generateButton("Create Subfolder", create)%></td>
        <td><%= generateButton("Delete", delete)%></td><%

        if (hasSiblings && !c.isRoot() && !parent.isRoot())
        {
            %><td><%= generateButton("Change Display Order", reorder)%></td><%
        }
    }
    else 
    {
        %><td><%= generateButton("Create Subfolder", create)%></td><%
    }
%>
    <td><%= generateButton("Aliases", aliases)%></td>
</tr></table>
