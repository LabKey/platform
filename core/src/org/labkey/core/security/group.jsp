<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.security.GroupView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    GroupView.GroupBean bean = ((JspView<GroupView.GroupBean>)HttpView.currentView()).getModelBean();
%>
<script type="text/javascript">
    LABKEY.requiresScript('completion.js');

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
        if (selected) return confirm("Permanently remove selected users from this group?");
        return true;
    }

</script>
<form action="updateMembers.post" method="POST">
<%
if (bean.messages.size() > 0)
    {
    %>
    <b>System membership status for new group members:</b><br>
    <div id="messages">
    <%
    for (String message : bean.messages)
        {
        %>
        <%= message %><br>
        <%
        }
    %>
    </div><br>
    <%
    }
%>
<labkey:errors />
<%
if (bean.members.size() <= 0)
    {
    %><p>This group currently has no members.</p><%
    }
else
    {
    %>
    <div id="current-members">
    Group members
    <br><table class="labkey-form">
        <tr>
            <th>Remove</th>
            <th>Email</th>
            <th>&nbsp;</th>
        </tr>
    <%
    for (Pair<Integer, String> member : bean.members)
        {
        Integer userId = member.getKey();
        String email = member.getValue();
        %>
        <tr>
            <td>
                <input type="checkbox" name="delete" value="<%= h(email) %>">
            </td>
            <td>
                <%= h(email) %>
            </td>
            <td>
                <a href="<%= bean.basePermissionsURL + userId %>">[Permissions]</a>
            </td>
        </tr>
        <%
        }
        ActionURL urlGroup = getViewContext().cloneActionURL();
        urlGroup.setAction("groupExport");
        urlGroup.replaceParameter("group", bean.groupName);
    %>
        <tr>
            <td colspan=3>
                <input type="image" src="<%=PageFlowUtil.buttonSrc("Select All")%>" onclick="return selectAllCheckboxes(this.form, true);">
                <input type="image" src="<%=PageFlowUtil.buttonSrc("Clear All")%>" onclick="return selectAllCheckboxes(this.form, false);">
                <%= PageFlowUtil.buttonLink("Export All to Excel", urlGroup.toString())%>
            </td>
        </tr>

    </table>
    </div>
    <%
    }
    %>
<br>
<div id="add-members">
Add New Members (enter one email address per line):<br>
<textarea name="names" cols="30" rows="8"
         onKeyDown="return ctrlKeyCheck(event);"
         onBlur="hideCompletionDiv();"
         autocomplete="off"
         onKeyUp="return handleChange(this, event, 'completeUser.view?prefix=');">
</textarea><br>
<input type="checkbox" name="sendEmail" value="true" checked>Send notification emails to all new<%
if (null != bean.ldapDomain)
    {
%>, non-<%= bean.ldapDomain %>
<%
    }
%>
users.<br><br>
Include the following message with the new user mail (optional):<br>
    <textarea rows="8" cols="30" name="mailPrefix"></textarea><br>
<input type="hidden" name="group" value="<%= bean.groupName %>">
<input type="image" src="<%= PageFlowUtil.buttonSrc("Update Group Membership","large") %>" onclick="return confirmRemoveUsers();">
</div>
</form>
<%
if (!bean.isGlobalGroup)
    {
%>
    <br><br><div id="delete-group">
    <%
        if (bean.members.size() == 0)
            {
    %>
    <form action="deleteGroup.post" method="POST">
    <input onclick="return confirm('Permanently delete group <%= bean.groupName %>?')" type="image" src="<%= PageFlowUtil.buttonSrc("Delete Empty Group","large") %>">
    <input type="hidden" name="group" value="<%= bean.groupName %>">
    </form>
    <%
            }
        else
            {
    %>
    To delete this group, first remove all members.
    <%
            }
    %>
    </div>
<%
    }
%>