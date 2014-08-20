<%
/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    Container c = getContainer();

    String name = form.getName();

    if (null == name)
        name = c.getName();

    String containerDescription = (c.isProject() ? "Project" : "Folder");
    String containerType = containerDescription.toLowerCase();

    // 16221
    if (ContainerManager.isRenameable(c))
    {
%>
<labkey:form action="<%=h(buildURL(AdminController.RenameFolderAction.class))%>" method="post">
    <table>
        <%=formatMissedErrors("form", "<tr><td>", "</td></tr>")%>
        <tr><td>Rename <%=h(containerType)%> <b><%=h(name)%></b> to:&nbsp;<input id="name" name="name" value="<%=h(name)%>"/></td></tr>
        <tr><td><input type="checkbox" id="addAlias" name="addAlias" checked>
            <label for="addAlias">Add a folder alias for the folder's current name. This will make links that still target the old folder name continue to work.</label>
        </td></tr>
    </table>
    <table>
        <tr>
            <td><%= button("Rename").submit(true) %></td>
            <td><%= button("Cancel").href(urlProvider(AdminUrls.class).getManageFoldersURL(c)) %></td>
        </tr>
    </table>
</labkey:form>
<%
    }
    else
    {
%>
This folder may not be renamed as it is reserved by the system.
<%
    }
%>
