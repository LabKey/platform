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
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<SecurityController.GroupsBean> me = (JspView<SecurityController.GroupsBean>) org.labkey.api.view.HttpView.currentView();
    SecurityController.GroupsBean groupsBean = me.getModelBean();
    Container container = groupsBean.getContainer();
    for (String message : groupsBean.getMessages())
    {
%><b><%= message %></b><br>
<% } %>
<labkey:errors />

<script type="text/javascript">
LABKEY.requiresScript('completion.js');
</script>
<table class="labkey-manage-groups">
    <%
        for (Group group : groupsBean.getGroups())
        {
            if (!group.isUsers() && !group.isGuests())
            {
                String groupPath = (container.isRoot() ? group.getName() : container.getPath() + "/" + group.getName());
                List<Pair<Integer, String>> members = SecurityManager.getGroupMemberNamesAndIds(groupPath);
    %>
            <tr class="labkey-header">
                <td width="10">
                    <a href="#" onclick="return toggleLink(this, false);">
                        <img src="<%=request.getContextPath()%>/_images/<%= groupsBean.isExpandedGroup(groupPath) ? "minus.gif" : "plus.gif" %>">
                    </a>
                </td>
                <td><%= h(group.getName()) %></td>
                <td><%= members.size() %> member<%= members.size() != 1 ? "s" : "" %></td>
                <td><%= textLink("manage group", urlProvider(SecurityUrls.class).getManageGroupURL(container, groupPath), "managegroup" + groupPath)%></td>
                <td><%= textLink("permissions", urlProvider(SecurityUrls.class).getGroupPermissionURL(container, group.getUserId()))%></td>
            </tr>
            <tr style="display:<%= groupsBean.isExpandedGroup(groupPath) ? "" : "none" %>">
                <td>&nbsp;</td>
                <td colspan="3">
                    <table class="labkey-alternate-row">
                    <%
                    for (Pair<Integer, String> member : members)
                    {
                    %>
                        <tr>
                            <td><%= h(member.getValue()) %></td>
                            <td><%= textLink("remove", ActionURL.toPathString("Security", "updateMembers", container) + "?quickUI=true&group=" + u(groupPath) + "&delete=" + u(member.getValue()),
                                    "return confirm(" + h(PageFlowUtil.jsString("Remove " + member.getValue() + " from group " + group.getName() + "?")) + ")", null) %></td>
                            <td><%= textLink("permissions", ActionURL.toPathString("User", "userAccess", container) + "?userId=" + member.getKey()) %></td>
                        </tr>
                    <%
                    }
                    %>
                        <tr>
                            <td colspan="3">
                                <form action="updateMembers.post" method="POST">
                                    New member email:
                                    <input type="hidden" name="group" value="<%= groupPath %>">
                                    <input type="hidden" name="quickUI" value="true">
                                    <input type="text" name="names" size="30"
                                           onKeyDown="return ctrlKeyCheck(event);"
                                           onBlur="hideCompletionDiv();"
                                           autocomplete="off"
                                           onKeyUp="return handleChange(this, event, 'completeUser.view?prefix=');">
                                    <input type="hidden" name="sendEmail" value="true">
                                    <%= buttonImg("Add User")%>
                                </form>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        <%
    }
        }
    %>
</table>

<form action=newGroup.post method=POST>
    <table class="labkey-form">
        <tr>
            <td class="labkey-form-label">Create new group</td>
            <td><input type="text" size="30" name="name"></td>
            <td><%= this.buttonImg("Create")%></td>
        </tr>
    </table>
</form>
