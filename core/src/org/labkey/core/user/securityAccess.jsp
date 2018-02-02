<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DataRegion"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.api.security.SecurityUrls"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.UserManager"%>
<%@ page import="org.labkey.api.security.UserPrincipal"%>
<%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.user.UserController.AccessDetail" %>
<%@ page import="org.labkey.core.user.UserController.AccessDetailRow" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
/*
 * This JSP is used to display an access report for the following actions:
 *  UserController.UserAccessAction          : displays folder access for a single user (within a project or the full container tree)
 *  SecurityController.GroupPermissionAction : displays folder access for a single group (within a project)
 *  SecurityController.FolderAccessAction    : displays user access for a single folder
 */

    User currentUser = getUser();
    Container c = getContainer();
    JspView<AccessDetail> me = (JspView<AccessDetail>) HttpView.currentView();
    AccessDetail bean = me.getModelBean();
    List<AccessDetailRow> rows = bean.getRows();

    DataRegion accessRegion = new DataRegion();
    accessRegion.setName("access");
    URLHelper returnURL = getActionURL().clone().deleteParameter("returnUrl");

    int cellPadding = 3;
%>

<script type="text/javascript">
    Ext4.QuickTips.init(true, {
        dismissDelay: 15000,
        trackMouse: true
    });
</script>

<labkey:errors />

<% if (!bean.isActive()) {%>
<div class="labkey-error"><b>NOTE:</b> This user account has been disabled, and thus has no permissions.
    However, if this account were re-enabled, it would have the following permissions.</div>
<% } %>

<table id=<%=q(accessRegion.getDomId())%> lk-region-name=<%=q(accessRegion.getName())%> class="labkey-data-region-legacy labkey-show-borders">
    <colgroup><col><col><col></colgroup>
    <tr id="dataregion_column_header_row_access">
        <th>&nbsp;</th>
<%
    if (!bean.showUserCol())
    {
        out.print(text("        <th>Container</th>"));
    }
    else
    {
        out.print(text("        <th>User</th>"));
    }
%>
        <th>Current Access</th>
    </tr>
    <tr id="dataregion_column_header_row_spacer_access" class="dataregion_column_header_row_spacer" style="display:none;"></tr>
<%
    int rowNumber = 0;
    for (AccessDetailRow row : rows)
    {
        boolean inherited = row.isInheritedAcl() && !row.getContainer().isProject();
        boolean isUser = null != UserManager.getUser(row.getUser().getUserId());
        String userColDisplay = isUser ? ((User)row.getUser()).getDisplayName(currentUser) : row.getUser().getName();
%>
    <tr class="<%=getShadeRowClass(rowNumber++ % 2 == 0)%>">
<%
        if (!bean.showUserCol())
        {
            ActionURL containerPermissionsLink = urlProvider(SecurityUrls.class).getPermissionsURL(row.getContainer(), returnURL);
            ActionURL folderAccessLink = urlProvider(SecurityUrls.class).getFolderAccessURL(row.getContainer());
%>
            <td><%= textLink("permissions", containerPermissionsLink) %></td>
            <td style="padding-left:<%= cellPadding + (10 * row.getDepth()) %>px;">
                <a href="<%=h(folderAccessLink)%>"><%= h(row.getContainer().getName()) %></a>
            </td>
<%
        }
        else
        {
            %><td><%= textLink("details", urlProvider(UserUrls.class).getUserDetailsURL(c, row.getUser().getUserId(), returnURL)) %></td>
            <td style='padding-left:"<%=cellPadding%>px;'><%
            if (isUser)
            {
                ActionURL userAccessLink = urlProvider(UserUrls.class).getUserAccessURL(row.getContainer(), row.getUser().getUserId());
                %><a href="<%=h(userAccessLink)%>"><%=h(userColDisplay)%></a><%
            }
            else
            {
                %><%=h(userColDisplay)%><%
            }
            out.print("</td>");
        }

        Map<String, List<Group>> accessGroups = row.getAccessGroups();
        Set<String> roleNames = accessGroups.keySet();
        if (roleNames.size() > 0)
        {
%>
            <td style="padding-left:<%=cellPadding%>px;">
                <table class="labkey-nav-tree" style="width: 100%;">
                    <tbody>
                    <tr class="labkey-nav-tree-row labkey-header" >
                        <td class="labkey-nav-tree-text" align="left" style="border:0 none;">
                            <a  style="color:#000000;" onclick="return LABKEY.Utils.toggleLink(this, false);" href="#">
                                <img src="<%=getWebappURL("_images/plus.gif")%>" alt="" />
                                <span><%=h(row.getAccess() + (inherited ? "*" : ""))%></span>
                            </a>
                        </td>
                    </tr>
                    <tr style="display:none">
                        <td style="padding-left:1em;width: 100%;border:0 none;">
                            <table class='labkey-data-region labkey-show-borders'>
                                <tr><th>Role</th><th>Group(s)</th></tr>
<%
                                for (String roleName : roleNames)
                                {
                                    out.print("<tr><td>" + h(roleName + (inherited ? "*" : "")) + "</td>");
                                    out.print("<td>");
                                    boolean first = true;
                                    for (Group group : accessGroups.get(roleName))
                                    {
                                        Container groupContainer = group.isAdministrators() ? ContainerManager.getRoot() : row.getContainer().getProject();
                                        String displayName = (group.isProjectGroup() ? groupContainer.getName() + "/" : "Site: ") + group.getName();

                                        Set<List<UserPrincipal>> membershipPaths = SecurityManager.getMembershipPathways(row.getUser(), group);
                                        String hoverExplanation = SecurityManager.getMembershipPathwayHTMLDisplay(membershipPaths, userColDisplay, roleName);

                                        if (group.isAdministrators() || group.isProjectGroup())
                                        {
                                            String groupName = group.isProjectGroup() ? groupContainer.getPath() + "/" + group.getName() : group.getName();
                                            ActionURL groupURL = urlProvider(SecurityUrls.class).getManageGroupURL(groupContainer, groupName);
                                            %><%= text(!first ? ", " : "") %><a href="<%=h(groupURL)%>" data-qtip="<%=text(hoverExplanation)%>"><%=h(displayName)%></a><%
                                        }
                                        else
                                        {
                                            %><%= text(!first ? ", " : "") %><span data-qtip="<%=text(hoverExplanation)%>"><%=h(displayName)%></span><%
                                        }
                                        first = false;
                                    }
                                    out.print("</td></tr>");
                                }
%>
                            </table>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </td>
<%
        }
        else
        {
            out.print("<td>&nbsp;</td>");
        }
%>
    </tr>
<%
    }
%>
</table><br>
*Indicates that permissions are inherited from the parent folder
