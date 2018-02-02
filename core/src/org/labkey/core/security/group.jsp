<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.PrincipalType" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserPrincipal" %>
<%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ page import="org.labkey.core.security.GroupView" %>
<%@ page import="org.labkey.core.security.SecurityApiActions" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("completion");
    }
%>
<%
    GroupView.GroupBean bean = ((JspView<GroupView.GroupBean>)HttpView.currentView()).getModelBean();
    Container c = getContainer();

    ActionURL completionUrl = new ActionURL(SecurityController.CompleteMemberAction.class, c);
    completionUrl.addParameter("groupId", bean.group.getUserId());
    URLHelper returnURL = getActionURL().clone().deleteParameter("returnUrl");
%>
<style type="text/css">
    .lowlight {
        color: #999999
    }
</style>
<script type="text/javascript">
    var form;
    
    function selectAllCheckboxes(form, value)
    {
        var elems = form.elements;
        var l = elems.length;
        for (var i = 0; i < l; i++)
        {
            var e = elems[i];
            if (e.type == 'checkbox' && !e.disabled && e.name == 'delete') e.checked = value;
        }
        return false;
    }

    function selectForRemoval(id, name, value)
    {
        var el = document.getElementById(id);
        if (el && el.type == 'checkbox' && !el.disabled && el.name == 'delete' && el.value == name)
        {
            el.checked = value;
        }
        return false;
    }

    function selectAllForRemoval(value)
    {
        <% for (UserPrincipal redundantMember : bean.redundantMembers.keySet()) {
            %>selectForRemoval('<%= redundantMember.getUserId() %>', <%= q(redundantMember.getName()) %>, value);<%
        } %>
        return false;
    }

    function confirmRemoveUsers()
    {
        var elems = document.getElementsByName("delete");
        var l = elems.length;
        var selected = false;
        for (var i = 0; i < l; i++)
        {
            if (elems[i].checked)
            {
                selected = true;
                break;
            }
        }
        var ok = true;
        if (selected)
            ok = confirm("Permanently remove selected users from this group?");
        if (ok)
            form.setClean(); 
        return ok;
    }

    Ext4.onReady(function()
    {
        form = new LABKEY.Form({ formElement: 'groupMembersForm' });

        Ext4.tip.QuickTipManager.init();
        Ext4.apply(Ext4.tip.QuickTipManager.getQuickTip(), {
            dismissDelay: 15000,
            trackMouse: true
        });
    });

</script>

<labkey:form id="groupMembersForm" action="<%=h(buildURL(SecurityController.UpdateMembersAction.class))%>" method="POST" layout="horizontal">
<%
if (bean.messages.size() > 0)
{
    %><b>System membership status for new group members:</b><br>
    <div id="messages"><%
    for (String message : bean.messages)
    {
        %><%= text(message) %><br><%
    }
    %></div><br><%
}
%><labkey:errors /><%

if (!bean.group.isDevelopers())
{
%>
    <br/>
    <%if (!bean.group.isSystemGroup()){%><%= button("Rename Group").href(buildURL(SecurityApiActions.RenameGroupAction.class, "id=" + bean.group.getUserId())) %><%}%>
    <%= button("View Permissions").href(urlProvider(SecurityUrls.class).getGroupPermissionURL(c, bean.group.getUserId())) %>
<%
}

    FrameFactoryClassic.startTitleFrame(out, "Group members", null, "100%", null);
    if (bean.members.size() <= 0)
{
    %><p>This group currently has no members.</p><%
}
else
{
    %>

    <div id="current-members">
    <table>
        <tr>
            <th width=70>Remove</th>
            <th>Name</th>
            <th width="110px">&nbsp;</th>
        </tr>
    <%
    for (UserPrincipal member : bean.members)
        {
        Integer userId = member.getUserId();
        String memberName = member.getName();
        boolean isGroup = member.getPrincipalType() == PrincipalType.GROUP;
        %>
        <tr>
            <td align="center">
                <input type="checkbox" name="delete" id="<%= userId %>" value="<%= h(memberName) %>">
            </td>
            <td>
        <%
        if (isGroup)
        {
            Group g = (Group)member;
            if (g.isProjectGroup())
            {
                %><a href="<%= urlProvider(SecurityUrls.class).getManageGroupURL(c, c.getPath() + "/" + h(memberName)) %>">
                <span style="font-weight:bold;">
                    <%= h(memberName) %>
                </span>
                </a><%
            }
            else
            {
                %><a href="<%= urlProvider(SecurityUrls.class).getManageGroupURL(ContainerManager.getRoot(), h(memberName)) %>">
                <span style="font-weight:bold;">
                  Site: <%= h(memberName) %>
                </span>
              </a><%
            }
            %>

            <%
        }
        else
        {
            User u = (User)member;
            String displayName = u.getDisplayName(getUser());
            if (!u.isActive()) // issue 13849
            {
                %><span class="lowlight" data-qtitle="User Inactive" data-qtip="This user account has been disabled."><%= h(memberName) %></span>&nbsp;<%
            }
            else
            {
                %>
                <%= h(memberName) %>&nbsp;
                <%= h(!memberName.equalsIgnoreCase(displayName) && StringUtils.isNotBlank(displayName) ? "(" + displayName + ")" : "") %>
                <%
            }
        }

        if (bean.redundantMembers.containsKey(member))
        {
            %><a data-qtitle="Redundant Member" data-qtip="<%=text(bean.displayRedundancyReasonHTML(member))%>">*</a><%
        }
        %>
            </td>
            <td>
                <% if (!isGroup)
                   {
                    %><%= textLink("permissions", urlProvider(UserUrls.class).getUserAccessURL(c, userId).addReturnURL(returnURL)) %><%
                   }
                   else
                   {
                    %><%= textLink("permissions", urlProvider(SecurityUrls.class).getGroupPermissionURL(c, userId).addReturnURL(returnURL)) %><%
                   }
                %>
            </td>
        </tr>
        <%
        }
        ActionURL urlGroup = getViewContext().cloneActionURL();
        urlGroup.setAction(SecurityController.GroupExportAction.class);
        urlGroup.replaceParameter("group", bean.groupName);

        ActionURL urlGroupActive = urlGroup.clone();
        urlGroupActive.addParameter("exportActive", true);
    %>
    </table>
    </div><%
    if (bean.redundantMembers.size() > 0)
    {
        %><div style="padding:5px;">* These group members already appear in other included member groups and can be safely removed.</div><%
    }

    %><div style="padding:5px;"><%
    if (bean.redundantMembers.size() > 0)
    {
        %><%= button("Select Redundant Members").submit(true).onClick("return selectAllForRemoval(true);") %><%
    }
    %>
    <%= button("Select All").submit(true).onClick("return selectAllCheckboxes(this.form, true);") %>
    <%= button("Clear All").submit(true).onClick("return selectAllCheckboxes(this.form, false);") %>
    <%= button("Export All to Excel").href(urlGroup) %>
    <%= button("Export Active to Excel").href(urlGroupActive) %>
    </div><%
}
%><br>
<div id="add-members">
<span style="font-weight:bold">Add New Members</span> (enter one email address or group per line):<br>
    <labkey:autoCompleteTextArea name="names" url="<%=h(completionUrl.getLocalURIString())%>" rows="8" cols="70"/>
    <input type="checkbox" name="sendEmail" value="true" checked>Send notification emails to all new<%
if (null != bean.ldapDomain && bean.ldapDomain.length() != 0 && !org.labkey.api.security.AuthenticationManager.ALL_DOMAINS.equals(bean.ldapDomain))
{
    %>, non-<%= h(bean.ldapDomain) %><%
}
%> users.<br><br>
<span style="font-weight:bold">Include a message</span> with the new user mail (optional):<br>
    <textarea rows="8" cols="72" name="mailPrefix"></textarea><br>
<input type="hidden" name="group" value="<%= h(bean.groupName) %>">
<br>
<%= button("Update Group Membership").submit(true).onClick("return confirmRemoveUsers();") %>
</div>
</labkey:form>
<%
if (!bean.isSystemGroup)
{
    %><br><br><div id="delete-group"><%
    if (bean.members.size() == 0)
    {
        %>
        <labkey:form action="<%=h(buildURL(SecurityController.StandardDeleteGroupAction.class))%>" method="POST">
        <%= button("Delete Empty Group").submit(true).onClick("return confirm('Permanently delete group " + bean.groupName + "?')") %>
        <input type="hidden" name="group" value="<%= h(bean.groupName) %>">
        </labkey:form>
        <%
    }
    else
    {
        %>To delete this group, first remove all members.<%
    }
    %></div><%
}

    FrameFactoryClassic.endTitleFrame(out);
%>
