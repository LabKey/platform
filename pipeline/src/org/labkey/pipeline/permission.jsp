<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.SecurityPolicy" %>
<%@ page import="org.labkey.api.security.roles.AuthorRole" %>
<%@ page import="org.labkey.api.security.roles.EditorRole" %>
<%@ page import="org.labkey.api.security.roles.NoPermissionsRole" %>
<%@ page import="org.labkey.api.security.roles.ReaderRole" %>
<%@ page import="org.labkey.api.security.roles.Role" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<div width="240px" id="pipelineFilesPermissions">
<%
    PipelineController.PermissionView me = (PipelineController.PermissionView)HttpView.currentView();
    SecurityPolicy policy = me.getModelBean();
    Container c = getContainer();

    boolean enableFTP = !policy.isEmpty();
%>
These permissions control whether pipeline files can be downloaded and updated via the web server.
<p />
<labkey:form id="permissionsForm" action="<%= h(buildURL(PipelineController.UpdateRootPermissionsAction.class))%>" method="POST">
<input type="hidden" name="<%= h(ActionURL.Param.returnUrl) %>" value="<%= h(getViewContext().getActionURL())%>" />
<input id="enabledCheckbox" type="checkbox" name="enable"<%=checked(enableFTP)%> onclick="toggleEnableFTP(this)" onchange="toggleEnableFTP(this)"> Share files via web site<br>
    <%
    List<Group> groups = SecurityManager.getGroups(c.getProject(), true);
    Pair[] optionsFull = new Pair[]
    {
        new Pair<>("no access", RoleManager.getRole(NoPermissionsRole.class)),
        new Pair<>("read files", RoleManager.getRole(ReaderRole.class)),
        new Pair<>("create files", RoleManager.getRole(AuthorRole.class)),
        new Pair<>("create and delete", RoleManager.getRole(EditorRole.class))
    };
    Pair[] optionsGuest = new Pair[] {optionsFull[0], optionsFull[1]};

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
        Role assignedRole = assignedRoles.size() > 0 ? assignedRoles.get(0) : null;
        String name = h(g.getName());
        if (g.isAdministrators())
            name = "Site&nbsp;Administrators";
        else if (g.isUsers())
            name = "All Users";
        %><tr><td><%=text(name)%><input type="hidden" name="groups[<%=i%>]" value="<%=g.getUserId()%>"></td><td><select name="perms[<%=i%>]">
        <%=text(writeOptions(g.isGuests() ? optionsGuest : optionsFull, assignedRole))%>
        </select></td></tr><%
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
        Role assignedRole = assignedRoles.size() > 0 ? assignedRoles.get(0) : RoleManager.getRole(NoPermissionsRole.class);
        %><tr><td><%=h(g.getName())%><input type="hidden" name="groups[<%=i%>]" value="<%=g.getUserId()%>"></td><td><select name="perms[<%=i%>]">
        <%=text(writeOptions(g.isGuests() ? optionsGuest : optionsFull, assignedRole))%>
        </select></td></tr><%
        i++;
    }
    %></table><br><%
%>
<%= button("Submit").submit(true) %>
</labkey:form>
<script type="text/javascript">
toggleEnableFTP(document.getElementById("enabledCheckbox"));
        
function toggleEnableFTP(checkbox)
{
    var i;
    var checked = checkbox.checked;
    var form = document.getElementById("permissionsForm");
    var elements = form.getElementsByTagName("select"); 
    for (i in elements)
    {
        var e = elements[i];
        e.disabled = !checked;
    }
}
</script>


<%!
    String writeOptions(Pair[] options, Role role) throws IOException
    {
        StringBuilder out = new StringBuilder();
        boolean selected = false;
        for (Pair option : options)
        {
            out.append("<option value=\"");
            out.append(h(option.getValue()));
            if (option.getValue().equals(role))
            {
                selected = true;
                out.append("\" selected>");
            }
            else
                out.append("\">");
            out.append(h(option.getKey()));
            out.append("</option>");
        }
        if (!selected && null != role)
        {
            out.append("<option value=\"");
            out.append(h(role.getUniqueName()));
            out.append("\" selected>");
            out.append(h(role.getName()));
            out.append("</option>");
        }
        return out.toString();
    }
%>
</div>