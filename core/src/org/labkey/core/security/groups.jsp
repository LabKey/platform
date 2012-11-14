<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<SecurityController.GroupsBean> me = (JspView<SecurityController.GroupsBean>) HttpView.currentView();
    SecurityController.GroupsBean groupsBean = me.getModelBean();
    Container container = groupsBean.getContainer();
    ActionURL completionUrl = new ActionURL(SecurityController.CompleteUserAction.class, container);

    for (String message : groupsBean.getMessages())
    {
%><b><%= message %></b><br>
<% } %>

<labkey:errors />

<table width="100%">
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
                            <td><%= textLink("remove", urlProvider(SecurityUrls.class).getUpdateMembersURL(container, groupPath, member.getValue(), true),
                                    "return confirm(" + h(PageFlowUtil.jsString("Remove " + member.getValue() + " from group " + group.getName() + "?")) + ")", null) %></td>
                            <td><%= textLink("permissions", urlProvider(UserUrls.class).getUserAccessURL(container, member.getKey())) %></td>
                        </tr>
                    <%
                    }
                    %>
                        <tr>
                            <td colspan="3">
                                <form action="<%=h(buildURL(SecurityController.UpdateMembersAction.class))%>" method="POST">
                                    New member email:
                                    <input type="hidden" name="group" value="<%= groupPath %>">
                                    <input type="hidden" name="quickUI" value="true">
                                    <labkey:autoCompleteText name="names" size="30" url="<%=completionUrl.getLocalURIString()%>"/>
                                    <input type="hidden" name="sendEmail" value="true">
                                    <%= generateSubmitButton("Add User")%>
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

<form action="<%=h(buildURL(SecurityController.NewGroupAction.class))%>" method=POST>
    <labkey:csrf/>
    <table>
        <tr>
            <td class="labkey-form-label">Create new group</td>
            <td><input type="text" size="30" name="name"></td>
            <td><%= this.generateSubmitButton("Create")%></td>
        </tr>
    </table>
</form>
