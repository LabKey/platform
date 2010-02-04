<%
/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.*" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    MoveFolderTreeView me = (MoveFolderTreeView) HttpView.currentView();
    ManageFoldersForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();

    String name = form.getName();
    if (null == name)
        name = c.getName();
    String containerType = (c.isProject() ? "project" : "folder");
%>

<table class="labkey-data-region">
    <%=formatMissedErrors("form")%>
    <tr><td><form name="moveAddAlias" action="showMoveFolderTree.view">
        <input type="checkbox" onchange="document.forms.moveAddAlias.submit()" name="addAlias" <% if (form.isAddAlias()) { %>checked<% } %>> Add a folder alias for the folder's current location. This will make links that still target the old folder location continue to work.
        <% if (form.isShowAll()) { %>
            <input type="hidden" name="showAll" value="1" />
        <% } %>
    </form></td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td>Move <%=containerType%> <b><%=h(name)%></b> to:</td></tr>
    <tr><td>&nbsp;</td></tr>
    <%
        boolean showAll = form.isShowAll() || c.isProject();
        ActionURL moveURL = AdminController.getMoveFolderURL(c, form.isAddAlias());    // Root is placeholder -- will get replaced by container tree
        AdminController.MoveContainerTree ct = new AdminController.MoveContainerTree(showAll ? "/" : c.getProject().getName(), ctx.getUser(), moveURL);
        ct.setIgnore(c);        // Can't set a folder's parent to itself or its children
        ct.setInitialLevel(1);  // Use as left margin
    %>
    <%=ct.render()%>
    <tr><td>&nbsp;</td></tr>
    </table>

    <table><tr>
    <td><%=PageFlowUtil.generateButton("Cancel", AdminController.getManageFoldersURL(c))%></td><%
    if (form.isShowAll())
    {
        if (!c.isProject())
        {
            %><td><%=PageFlowUtil.generateButton("Show Current Project Only", AdminController.getShowMoveFolderTreeURL(c, form.isAddAlias(), false))%></td><%
        }
    }
    else
    {
        %><td><%=PageFlowUtil.generateButton("Show All Projects", AdminController.getShowMoveFolderTreeURL(c, form.isAddAlias(), true))%></td><%
    }
    %></tr>
</table>
