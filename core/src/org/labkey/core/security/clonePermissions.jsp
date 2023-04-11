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
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.util.Button.ButtonBuilder" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page import="org.labkey.core.security.SecurityController.ClonePermissionsAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("completion");
        dependencies.add("clonePermissions.js");
    }
%>
<%
    SecurityController.ClonePermissionsForm form = (SecurityController.ClonePermissionsForm)HttpView.currentModel();
    User target = UserManager.getUser(form.getTargetUser());
    boolean excludeSiteAdmins = !getUser().hasSiteAdminPermission(); // App admins can't clone permissions from site admins
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    Ext4.onReady(function(){
        createCloneUserField(false, true, <%=excludeSiteAdmins%>, <%=target.getUserId()%>);
    });
</script>

<labkey:form action="<%=urlFor(ClonePermissionsAction.class)%>" method="POST">
    <table>
        <%
            if (getErrors("form").hasErrors());
            {
        %>
        <tr><td><labkey:errors /></td></tr>
        <%
            }
        %>
        <tr>
            <td>
                Warning! Cloning permissions will delete <strong>all</strong> group memberships and direct role assignments for <strong><%=h(target.getDisplayName(getUser()) + " (" + target.getEmail() + ")")%></strong>
                and replace them with the group memberships and direct role assignments of the user selected below.
            </td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td>Clone permissions from user:<span id="auto-completion-div"></span>
            <%=button("Permissions").onClick("showUserAccess();")%>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td>
                <input type="hidden" name="targetUser" value="<%=form.getTargetUser()%>">
                <%=ReturnUrlForm.generateHiddenFormField(form.getReturnActionURL())%>
<%
    ButtonBuilder submit = button("Clone Permissions");
    submit.submit(true);
%>
                <%=submit%>
                <%= button("Cancel").href(form.getReturnURLHelper()) %>
            </td>
        </tr>
    </table>

</labkey:form>
