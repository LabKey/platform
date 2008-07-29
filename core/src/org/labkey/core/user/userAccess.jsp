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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.core.user.UserController"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<UserController.AccessDetail> me =
            (JspView<UserController.AccessDetail>) HttpView.currentView();
    UserController.AccessDetail bean = me.getModelBean();
    List<UserController.AccessDetailRow> rows = bean.getRows();

    int cellPadding = 3;
%>
<labkey:errors />

<% if(!bean.isActive()) {%>
<div class="labkey-error"><b>NOTE:</b> This user account has been disabled, and thus has no permissions.
However, If this account is re-enabled, it would have the following permissions.</div>
<% } %>

<table class="labkey-alternating-grid">
    <tr>
        <th class="labkey-alternating-grid-header">Container</th>
        <th class="labkey-alternating-grid-header">Current Access</th>
<%
    if (bean.showGroups())
    {
%>
        <th class="labkey-alternating-grid-header">Relevant Group(s)</th>
<%
    }
%>
    </tr>
<%
    int rowNumber = 0;
    for (UserController.AccessDetailRow row : rows)
    {
        boolean inherited = row.isInheritedAcl() && !row.getContainer().isProject();
        ActionURL containerPermissionsLink = urlProvider(SecurityUrls.class).getProjectURL(row.getContainer());
%>
    <tr class="<%= rowNumber++ % 2 == 0 ?  "labkey-alternating-row" : "labkey-row"%>">
        <td class="labkey-alternating-grid-cell" style="padding-left:<%= cellPadding + (10 * row.getDepth()) %>;">
            <a href="<%= containerPermissionsLink.getLocalURIString() %>"><%= row.getContainer().getName() %></a>
        </td>
        <td class="labkey-alternating-grid-cell"><%= row.getAccess() %><%= inherited ? "*" : "" %></td>
    <%
        if (bean.showGroups())
        {
            out.print("<td class=\"labkey-alternating-grid-cell\">");
            boolean first = true;
            for (Group group : row.getGroups())
            {
                Container groupContainer = group.isAdministrators() ? ContainerManager.getRoot() : row.getContainer().getProject();
                String displayName = (group.isProjectGroup() ? groupContainer.getName() + "/" : "Site ") + group.getName();
                if (group.isAdministrators() || group.isProjectGroup())
                {
                    String groupName = group.isProjectGroup() ? groupContainer.getPath() + "/" + group.getName() : group.getName();
                    ActionURL groupURL = PageFlowUtil.urlProvider(SecurityUrls.class).getManageGroupURL(groupContainer, groupName);
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
*Indicates that this group's permissions are inherited from the parent folder
