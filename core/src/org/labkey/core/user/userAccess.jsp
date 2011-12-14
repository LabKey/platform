<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.core.user.UserController"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
/*
 * This JSP is used to display an access report for the following actions:
 *  UserController.UserAccessAction          : displays folder access for a single user (within a project or the full container tree)
 *  SecurityController.GroupPermissionAction : displays folder access for a single group (within a project)
 *  SecurityController.FolderAccessAction    : displays user access for a single folder
 */

    ViewContext context = HttpView.currentContext();
    User currentUser = context.getUser();
    Container c = context.getContainer();
    JspView<UserController.AccessDetail> me = (JspView<UserController.AccessDetail>) HttpView.currentView();
    UserController.AccessDetail bean = me.getModelBean();
    List<UserController.AccessDetailRow> rows = bean.getRows();

    int cellPadding = 3;
%>
<labkey:errors />

<% if (!bean.isActive()) {%>
<div class="labkey-error"><b>NOTE:</b> This user account has been disabled, and thus has no permissions.
However, If this account is re-enabled, it would have the following permissions.</div>
<% } %>

<table class="labkey-data-region labkey-show-borders">
    <colgroup><col><col><col></colgroup>
<%
    if (bean.showGroups())
    {
        out.print("<col>");
    }
%>
    <tr>
<%
    out.print("<th>&nbsp;</th>");
    if (!bean.showUserCol())
    {
        out.print("<th>Container</th>");
    }
    else
    {
        out.print("<th>User</th>");
    }
    out.print("<th>Current Access</th>");
    if (bean.showGroups())
    {
        out.print("<th>Relevant Group(s)</th>");
    }
%>
    </tr>
<%
    int rowNumber = 0;
    for (UserController.AccessDetailRow row : rows)
    {
        boolean inherited = row.isInheritedAcl() && !row.getContainer().isProject();
        boolean isUser = null != UserManager.getUser(row.getUser().getUserId());
        String userColDisplay = h(isUser ? ((User)row.getUser()).getDisplayName(currentUser) : row.getUser().getName());
%>
      <tr class="<%= rowNumber++ % 2 == 0 ?  "labkey-alternate-row" : "labkey-row"%>">
<%
        if (!bean.showUserCol())
        {
            ActionURL containerPermissionsLink = urlProvider(SecurityUrls.class).getProjectURL(row.getContainer());
            ActionURL folderAccessLink = urlProvider(SecurityUrls.class).getFolderAccessURL(row.getContainer());
%>
            <td><%= textLink("permissions", containerPermissionsLink) %></td>
            <td style="padding-left:<%= cellPadding + (10 * row.getDepth()) %>px;">
                <a href="<%= folderAccessLink.getLocalURIString() %>"><%= h(row.getContainer().getName()) %></a>
            </td>
<%
        }
        else
        {
            %><td><%= textLink("details", urlProvider(UserUrls.class).getUserDetailsURL(c, row.getUser().getUserId(), context.getActionURL())) %></td><%
            out.print("<td>");
            if (isUser)
            {
                ActionURL userAccessLink = urlProvider(UserUrls.class).getUserAccessURL(row.getContainer(), row.getUser().getUserId());
                %><a href="<%= userAccessLink.getLocalURIString() %>"><%= userColDisplay %></a><%
            }
            else
            {
                %><%= userColDisplay %><%
            }
            out.print("</td>");
        }
    %>
        <td ><%= row.getAccess() %><%= inherited ? "*" : "" %></td>
    <%
        if (bean.showGroups())
        {
            out.print("<td>");
            boolean first = true;
            for (Group group : row.getGroups())
            {
                Container groupContainer = group.isAdministrators() ? ContainerManager.getRoot() : row.getContainer().getProject();
                String displayName = (group.isProjectGroup() ? groupContainer.getName() + "/" : "Site ") + group.getName();
                if (group.isAdministrators() || group.isProjectGroup())
                {
                    String groupName = group.isProjectGroup() ? groupContainer.getPath() + "/" + group.getName() : group.getName();
                    ActionURL groupURL = urlProvider(SecurityUrls.class).getManageGroupURL(groupContainer, groupName);
                    %><%= !first ? ", " : "" %><a href="<%=h(groupURL)%>"><%=h(displayName)%></a><%
                }
                else
                {
                    %><%= !first ? ", " : "" %><%=h(displayName)%><%
                }
                first = false;
            }
            out.print("&nbsp;</td>");
        }
    %>
    </tr>
<%
    }
%>
</table><br>
*Indicates that permissions are inherited from the parent folder
