<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.PrincipalType" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserPrincipal" %>
<%@ page import="org.labkey.api.security.UserUrls" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ page import="org.labkey.core.security.GroupView.GroupBean" %>
<%@ page import="org.labkey.core.security.SecurityApiActions" %>
<%@ page import="org.labkey.core.security.SecurityController.CompleteMemberAction" %>
<%@ page import="org.labkey.core.security.SecurityController.GroupAction" %>
<%@ page import="org.labkey.core.security.SecurityController.GroupExportAction" %>
<%@ page import="org.labkey.core.security.SecurityController.StandardDeleteGroupAction" %>
<%@ page import="org.labkey.core.user.LimitActiveUsersSettings" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
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
    GroupBean bean = ((JspView<GroupBean>)HttpView.currentView()).getModelBean();
    Container c = getContainer();

    ActionURL completionUrl = urlFor(CompleteMemberAction.class);
    completionUrl.addParameter("groupId", bean.group.getUserId());
    URLHelper returnURL = getActionURL().clone().deleteParameter(ActionURL.Param.returnUrl);
%>
<style type="text/css">
    .lowlight {
        color: #999999
    }
</style>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    let form;
    
    function selectAllCheckboxes(form, value)
    {
        const elems = form.elements;
        const l = elems.length;
        for (let i = 0; i < l; i++)
        {
            const e = elems[i];
            if (e.type === 'checkbox' && !e.disabled && e.name === 'delete') e.checked = value;
        }
        return false;
    }

    function selectForRemoval(id, name, value)
    {
        const el = document.getElementById(id);
        if (el && el.type === 'checkbox' && !el.disabled && el.name === 'delete' && el.value === name)
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
        const elems = document.getElementsByName("delete");
        const l = elems.length;
        let selected = 0;
        let deletingSelf = false;
        for (let i = 0; i < l; i++)
        {
            if (elems[i].checked)
            {
                selected++;
                if (elems[i].id === LABKEY.user.id.toString())
                    deletingSelf = true;
            }
        }
        let ok = true;
        if (selected > 0)
        {
            let msg = "Are";
            if (deletingSelf)
            {
                msg = "Are you sure you want to delete yourself from this group? You will likely lose all permissions granted to this group.";
                selected--;

                if (selected > 0)
                    msg = msg + "\n\nIn addition, are"
            }

            // Deleting other users case
            if (selected > 0)
            {
                let who = deletingSelf ? "other user" : "selected user";

                if (selected > 1)
                    who = who + "s";

                msg = msg + " you sure you want to permanently remove the " + who + " from this group?"
            }
            // self
            // self+1
            // self+more than 1
            // 1
            // more than 1
            ok = confirm(msg);
        }
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

<labkey:form id="groupMembersForm" action="<%=urlFor(GroupAction.class)%>" method="POST" layout="horizontal">
<%
if (!bean.messages.isEmpty())
{
    %><b>System membership status for new group members:</b><br>
    <div id="messages"><%
    for (HtmlString message : bean.messages)
    {
        %><%= message %><br><%
    }
    %></div><br><%
}
%><labkey:errors />
    <br/>
    <%if (!bean.group.isSystemGroup()){%><%= button("Rename Group").href(urlFor(SecurityApiActions.RenameGroupAction.class).addParameter("id", bean.group.getUserId())) %><%}%>
    <%= button("View Permissions").href(urlProvider(SecurityUrls.class).getGroupPermissionURL(c, bean.group.getUserId())) %>
<%

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

    // Issue 49845: Sort the members by name
    List<UserPrincipal> members = new ArrayList<>(bean.members);
    Collections.sort(members);

    for (UserPrincipal member : members)
    {
        int userId = member.getUserId();
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
                %><a href="<%=h(urlProvider(SecurityUrls.class).getManageGroupURL(c, c.getPath() + "/" + memberName))%>">
                <span style="font-weight:bold;">
                    <%= h(memberName) %>
                </span>
                </a><%
            }
            else
            {
                %><a href="<%=h(urlProvider(SecurityUrls.class).getManageGroupURL(ContainerManager.getRoot(), memberName))%>">
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
            %><a data-qtitle="Redundant Member" data-qtip="<%=unsafe(bean.displayRedundancyReasonHTML(member))%>">*</a><%
        }
        %>
            &nbsp;&nbsp;</td>
            <td>
                <% if (!isGroup)
                   {
                    %><%= link("permissions", urlProvider(UserUrls.class).getUserAccessURL(c, userId).addReturnURL(returnURL)) %><%
                   }
                   else
                   {
                    %><%= link("permissions", urlProvider(SecurityUrls.class).getGroupPermissionURL(c, userId).addReturnURL(returnURL)) %><%
                   }
                %>
            </td>
        </tr>
        <%
    }
    ActionURL urlGroup = getViewContext().cloneActionURL();
    urlGroup.setAction(GroupExportAction.class);
    urlGroup.replaceParameter("group", bean.groupName);

    ActionURL urlGroupActive = urlGroup.clone();
    urlGroupActive.addParameter("exportActive", true);
    %>
    </table>
    </div><%
    if (!bean.redundantMembers.isEmpty())
    {
        %><div style="padding:5px;">* These group members already appear in other included member groups and can be safely removed.</div><%
    }

    %><div style="padding:5px;"><%
    if (!bean.redundantMembers.isEmpty())
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

<%
    LimitActiveUsersSettings settings = new LimitActiveUsersSettings();
    if (settings.isUserLimit())
    {
%>
    Number of new users that can be added: <%=settings.getRemainingUserCount()%><br><br>
<%
    }
%>

<div id="add-members">
<span style="font-weight:bold">Add New Members</span> (enter one email address or group per line):<br>
    <labkey:autoCompleteTextArea name="names" url="<%=completionUrl%>" rows="8" cols="70"/>
    <input type="checkbox" name="sendEmail" value="true" checked><%=AuthenticationManager.getStandardSendVerificationEmailsMessage()%><br><br>
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
    if (bean.members.isEmpty())
    {
        %>
        <labkey:form action="<%=urlFor(StandardDeleteGroupAction.class)%>" method="POST">
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
