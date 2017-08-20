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
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.security.SecurityManager"%>
<%@ page import="org.labkey.api.security.SecurityPolicy"%>
<%@ page import="org.labkey.api.security.SecurityPolicyManager"%>
<%@ page import="org.labkey.api.security.permissions.ReadPermission"%>
<%@ page import="org.labkey.api.security.permissions.ReadSomePermission"%>
<%@ page import="org.labkey.api.security.roles.EditorRole"%>
<%@ page import="org.labkey.api.security.roles.ReaderRole"%>
<%@ page import="org.labkey.api.security.roles.Role" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    String groupName(Group g)
    {
        if (g.getUserId() == Group.groupUsers)
            return "All site users";
        else
            return g.getName();
    }
%>
<%
    Pair<StudyImpl, ActionURL> pair = ((HttpView<Pair<StudyImpl, ActionURL>>) HttpView.currentView()).getModelBean();
    StudyImpl study = pair.first;
    ActionURL returnUrl = pair.second;
    SecurityPolicy studyPolicy = SecurityPolicyManager.getPolicy(study);

    List<Group> groups = SecurityManager.getGroups(study.getContainer().getProject(), true);

    ArrayList<Group> readGroups = new ArrayList<>();
    ArrayList<Group> restrictedGroups = new ArrayList<>();
    ArrayList<Group> noReadGroups = new ArrayList<>();
    for (Group g : groups)
    {
        if (g.getUserId() == Group.groupAdministrators)
            continue;
        if (studyPolicy.hasNonInheritedPermission(g, ReadPermission.class))
            readGroups.add(g);
        else if (studyPolicy.hasNonInheritedPermission(g, ReadSomePermission.class))
            restrictedGroups.add(g);
        else
            noReadGroups.add(g);
    }
%>
These groups can read ALL datasets.
<ul class="minus">
<%
    boolean guestsCanRead = false;
    boolean usersCanRead = false;
    if (readGroups.size() == 0)
    {
        %><li><i>none</i></li><%
    }
    for (Group g : readGroups)
    {
        if (g.getUserId() == Group.groupUsers)
            usersCanRead = true;
        if (g.getUserId() == Group.groupGuests)
            guestsCanRead = true;
        %><li><%=h(groupName(g))%></li><%
    }
%>
</ul>
<%
if (guestsCanRead || usersCanRead)
{
    restrictedGroups.clear();
%>
    Since <i>All site users</i> and/or <i>Guests</i> have read permissions, this is effectively an <b>open</b> study.<p/>
    To restrict access to individual datasets, these groups should not have permissions.
<%
}
else
{
%>
    These groups do not have read permissions. (Note: a user may belong to more than one group, see documentation.)
    <ul class="minus">
    <%
        if (noReadGroups.size() == 0)
        {
    %>
        <li><i>none</i></li>
    <%
        }
        for (Group g : noReadGroups)
        {
    %>
        <li><%=h(groupName(g))%></li>
    <%
        }
    %>
    </ul>
    <%
        if (restrictedGroups.isEmpty())
        {
    %>
        No group has been given 'per dataset' access. To grant access to individual datasets, select the 'per dataset' option for one or more groups above.
    <%
        }
        else
        {
            if (restrictedGroups.size() == 1)
            {
    %>
        The group given 'per dataset' access above is listed below. The
    <%
            }
            else
            {
    %>
        Each group given 'per dataset' access above is listed in the columns below. Each
    <%
            }
%>group's access to each dataset is controlled by the drop-down list in each cell.
<labkey:form id="datasetSecurityForm" action="<%=h(buildURL(SecurityController.ApplyDatasetPermissionsAction.class))%>" method="POST">
<%
    if (returnUrl != null)
        out.write("<input type=\"hidden\" name=\"returnUrl\" value=\"" + h(returnUrl) + "\">");

    int row = 0;
    %><br/><table class="labkey-data-region-legacy labkey-show-borders" id="datasetSecurityFormTable"><colgroup>
    <%
    for (int i = 0; i < restrictedGroups.size() + 1; i++)
    {
        %><col><%
    }
    %></colgroup>
    <tr class="<%=getShadeRowClass(row++ % 2 == 0)%>"><th>&nbsp;</th><%
    for (Group g : restrictedGroups)
    {
        %><th style="padding: 0 5px 0 5px;"><%=h(groupName(g))%></th><%
    }

    java.util.List<Role> possibleRoles = new ArrayList<>();
    List<DatasetDefinition> datasets = new ArrayList<>(study.getDatasets());
    datasets.sort(Comparator.comparing(DatasetDefinition::getLabel, String.CASE_INSENSITIVE_ORDER));

    if (!datasets.isEmpty())
    {
        DatasetDefinition ds = datasets.get(0);
        SecurityPolicy dsPolicy = SecurityPolicyManager.getPolicy(ds);
        for (Role role : RoleManager.getAllRoles())
        {
            if (role.isApplicable(dsPolicy, ds) && role.getClass() != ReaderRole.class && role.getClass() != EditorRole.class)
            {
                possibleRoles.add(role);
            }
        }
    }

    %></tr>
    <tr class="<%=getShadeRowClass(row++ % 2 == 0)%>"><th>&nbsp;</th><%
    for (Group g : restrictedGroups)
    {
        %><td style="padding: 0 5px 0 5px; text-align: left"><select name="<%= h(g.getName()) %>" onchange="setColumnSelections(this)">
            <option value="" selected>&lt;set all to...&gt;</option>
            <option value="None">None</option>
            <option value="Read">Read</option><%
            if (study.getSecurityType() == SecurityType.ADVANCED_WRITE)
            {
            %>
                <option value="Edit">Edit</option>
            <%
            }
            for (Role role : possibleRoles)
            {
                // Filter out roles that can't be assigned to this user/group
                if (!role.getExcludedPrincipals().contains(g))
                {%>
                    <option value="<%= h(role.getName()) %>"><%= h(role.getName()) %></option>
                <% }
            }%>
        </select></td><%
    }
    %></tr><%
    for (Dataset ds : datasets)
    {
        SecurityPolicy dsPolicy = SecurityPolicyManager.getPolicy(ds);

        String inputName = "dataset." + ds.getDatasetId();
        %><tr class="<%=getShadeRowClass(row++ % 2 == 0)%>"><td><%=h(ds.getLabel())%></td><%

        for (Group g : restrictedGroups)
        {
            java.util.List<Role> roles = dsPolicy.getAssignedRoles(g);
            Role assignedRole = roles.isEmpty() ? null : roles.get(0);

            boolean writePerm = assignedRole != null && assignedRole.getClass() == EditorRole.class;
            boolean readPerm = !writePerm && dsPolicy.hasNonInheritedPermission(g, ReadPermission.class);

            if (study.getSecurityType() == SecurityType.ADVANCED_READ && writePerm)
                readPerm = true;

            boolean noPerm = !writePerm && !readPerm && assignedRole == null;
            int id = g.getUserId();
            %><td style="text-align: left;">
                <select name="<%=h(inputName)%>">
                    <option value="<%=id%>_NONE"<%=selected(noPerm)%>>None</option>
                    <option value="<%=id%>_<%= h(ReaderRole.class.getName()) %>"<%=selected(readPerm)%>>Read</option><%
                    if (study.getSecurityType() == SecurityType.ADVANCED_WRITE)
                    {
                        %>
                        <option value="<%=id%>_<%= h(EditorRole.class.getName()) %>"<%=selected(writePerm)%>>Edit</option>
                        <% for (Role possibleRole : possibleRoles)
                        {
                            // Filter out roles that can't be assigned to this user/group
                            if (!possibleRole.getExcludedPrincipals().contains(g))
                            { %>
                                <option value="<%=id%>_<%= h(possibleRole.getClass().getName()) %>"<%=selected(possibleRole == assignedRole)%>><%=h(possibleRole.getName()) %></option><%
                            }
                        }
                    }
                    %>
                </select>
              </td><%
        }
        %></tr><%
    }
    %>
    </table>
    <table>
        <tr>
            <td><%= button("Save").submit(true) %></td>
            <td><%= button("Set all to Read").href("#").onClick("return setAllSelections('Read');") %></td><%
            if (study.getSecurityType() == SecurityType.ADVANCED_WRITE)
            {
            %>
                <td><%= button("Set all to Edit").href("#").onClick("return setAllSelections('Edit');") %></td><%
            }
            %>
            <td><%= button("Clear All").href("#").onClick("return setAllSelections('None');") %></td>
        </tr></table>
</labkey:form>

    <%
        }
}
%>

<script type="text/javascript">
function setAllSelections(value)
{
    var form = document.getElementById("datasetSecurityForm");
    var elements = form.elements;

    for (var i=0; i<elements.length; i++)
    {
        var elem = elements[i];
        if (elem.nodeName == 'SELECT')
        {
            var options = elem.options;
            for (var optionIndex = 0; optionIndex < options.length; optionIndex++)
            {
                if (options[optionIndex].text == value)
                {
                    elem.selectedIndex = optionIndex;
                }
            }
        }
    }
    return false;
}

function setColumnSelections(select)
{
    var value = select.value;
    if(!value)
        return;

    var colIdx = select.parentNode.cellIndex;
    var table = document.getElementById("datasetSecurityFormTable");
    for (var i=2; i<table.rows.length; i++)
    {
        var elem;
        // firstElementChild supported by IE9, FF 3.6, else use firstChild 
        if ('firstElementChild' in table.rows[i].cells[colIdx])
            elem = table.rows[i].cells[colIdx].firstElementChild;
        else
            elem = table.rows[i].cells[colIdx].firstChild;

        if (elem && elem.nodeName == 'SELECT')
        {
            var options = elem.options;
            for (var optionIndex = 0; optionIndex < options.length; optionIndex++)
            {
                if (options[optionIndex].text == value)
                {
                    elem.selectedIndex = optionIndex;
                }
            }
        }
    }
    return false;
}
</script>
