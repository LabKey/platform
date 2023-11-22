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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.SecurityPolicy" %>
<%@ page import="org.labkey.api.security.SecurityPolicyManager" %>
<%@ page import="org.labkey.api.security.roles.AuthorRole" %>
<%@ page import="org.labkey.api.security.roles.EditorRole" %>
<%@ page import="org.labkey.api.security.roles.NoPermissionsRole" %>
<%@ page import="org.labkey.api.security.roles.ReaderRole" %>
<%@ page import="org.labkey.api.security.roles.Role" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.SafeToRender" %>
<%@ page import="org.labkey.api.util.element.Option.OptionBuilder" %>
<%@ page import="org.labkey.api.util.element.Select.SelectBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.pipeline.PipelineController.PermissionView" %>
<%@ page import="org.labkey.pipeline.PipelineController.UpdateRootPermissionsAction" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<div width="240px" id="pipelineFilesPermissions">
<%
    PermissionView me = (PermissionView)HttpView.currentView();
    PipeRoot pipeRoot = me.getModelBean();
    SecurityPolicy policy = SecurityPolicyManager.getPolicy(pipeRoot);
    Container c = getContainer();

    boolean enableFTP = !policy.isEmpty();
%>
These permissions control whether pipeline files can be downloaded and updated via the web server.
<p />
<labkey:form id="permissionsForm" action="<%=urlFor(UpdateRootPermissionsAction.class)%>" method="POST">
<input type="hidden" name="<%=ActionURL.Param.returnUrl%>" value="<%= h(getViewContext().getActionURL())%>" />
<% addHandler("enabledCheckbox", "click", "toggleEnableFTP(this)"); %>
<% addHandler("enabledCheckbox", "change", "toggleEnableFTP(this)"); %>
<input id="enabledCheckbox" type="checkbox" name="enable"<%=checked(enableFTP)%>> Share files via website<br>
    <%
    List<Group> groups = SecurityManager.getGroups(c.getProject(), true);
    List<Pair<String, Role>> optionsFull = List.of(
        new Pair<>("no access", RoleManager.getRole(NoPermissionsRole.class)),
        new Pair<>("read files", RoleManager.getRole(ReaderRole.class)),
        new Pair<>("create files", RoleManager.getRole(AuthorRole.class)),
        new Pair<>("create and delete", RoleManager.getRole(EditorRole.class))
    );
    List<Pair<String, Role>> optionsGuest = List.of(optionsFull.get(0), optionsFull.get(1));

    int i=0;
    %>
        <h4>Global groups</h4>
        <table class="lk-fields-table">
    <%
    for (Group g : groups)
    {
        if (g.isProjectGroup())
            continue;
        List<Role> assignedRoles = policy.getAssignedRoles(g);
        Role assignedRole = !assignedRoles.isEmpty() ? assignedRoles.get(0) : null;
        final HtmlString name;
        if (g.isAdministrators())
            name = HtmlString.unsafe("Site&nbsp;Administrators");
        else if (g.isUsers())
            name = HtmlString.of("All Users");
        else
            name = h(g.getName());
        %><tr>
            <td><%=name%><input type="hidden" name="groups[<%=i%>]" value="<%=g.getUserId()%>"></td>
            <td><%=getSelect(i, g.isGuests() ? optionsGuest : optionsFull, assignedRole, policy, pipeRoot)%></td>
        </tr><%
        i++;
    }
    %>
        </table>
        <h4>Project groups</h4>
        <table class="lk-fields-table">
    <%
    for (Group g : groups)
    {
        if (!g.isProjectGroup())
            continue;
        List<Role> assignedRoles = policy.getAssignedRoles(g);
        Role assignedRole = !assignedRoles.isEmpty() ? assignedRoles.get(0) : RoleManager.getRole(NoPermissionsRole.class);
        %><tr>
            <td><%=h(g.getName())%><input type="hidden" name="groups[<%=i%>]" value="<%=g.getUserId()%>"></td>
            <td><%=getSelect(i, g.isGuests() ? optionsGuest : optionsFull, assignedRole, policy, pipeRoot)%></td>
        </tr><%
        i++;
    }
    %></table><br><%
%>
<%= button("Submit").submit(true) %>
</labkey:form>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
toggleEnableFTP(document.getElementById("enabledCheckbox"));
        
function toggleEnableFTP(checkbox)
{
    const checked = checkbox.checked;
    const form = document.getElementById("permissionsForm");
    const elements = form.getElementsByTagName("select");
    for (let i in elements)
    {
        const e = elements[i];
        e.disabled = !checked;
    }
}
</script>

<%!
    SafeToRender getSelect(int i, List<Pair<String, Role>> options, Role role, SecurityPolicy policy, PipeRoot pipeRoot)
    {
        SelectBuilder select = select()
            .name("perms[" + i + "]")
            .addOptions(
                options.stream()
                    .map(option -> new OptionBuilder(option.getKey(), option.getValue()))
            )
            .className(null);

        if (options.stream().anyMatch(pair -> pair.getValue().equals(role)))
        {
            select.selected(role.getUniqueName());
        }
        else if (role != null && role.isApplicable(policy, pipeRoot))
        {
            select.addOption(role.getName(), role.getUniqueName());
            select.selected(role.getUniqueName());
        }

        return select;
    }
%>
</div>