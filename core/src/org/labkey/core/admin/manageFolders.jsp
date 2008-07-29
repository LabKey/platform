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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.util.ContainerTreeSelected"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.core.admin.AdminController.*"%>
<%@ page import="java.util.List"%>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    final ManageFoldersForm form = (ManageFoldersForm) __form;
    final Container c = getContainer();
    final ViewContext context = HttpView.currentContext();
    final ActionURL currentUrl = context.cloneActionURL();
    final ContainerTreeSelected ct = new ContainerTreeSelected(form.getProjectName(), getUser(), ACL.PERM_ADMIN, currentUrl, "managefolders");
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
        <td><%= buttonLink("Rename", rename)%></td>
        <td><%= buttonLink("Move", move)%></td>
        <td><%= buttonLink("Create Subfolder", create)%></td>
        <td><%= buttonLink("Delete", delete)%></td><%

        if (hasSiblings && !context.getContainer().isRoot() && !context.getContainer().getParent().isRoot())
        {
            %><td><%= buttonLink("Change Display Order", reorder)%></td><%
        }
    }
    else 
    {
        %><td><%= buttonLink("Create Subfolder", create)%></td><%
    }
%>
    <td><%= buttonLink("Aliases", aliases)%></td>
</tr></table>
