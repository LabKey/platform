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
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.element.Option.OptionBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageStudyAction" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController.ApplyDatasetPermissionsAction" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="javax.annotation.Nullable" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Objects" %>
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

    String getTooltip(Dataset ds, Group group)
    {
        return "Dataset: " + ds.getLabel() + ", " + "Group: " + groupName(group);
    }

    // Used to highlight special datasets in per-dataset permissions
    @Nullable String getTooltip(StudyImpl study, Integer datasetId)
    {
        String participant = study.getSubjectNounSingular().toLowerCase();
        List<String> messages = new LinkedList<>();
        if (datasetId.equals(study.getParticipantCohortDatasetId()))
            messages.add("This is the " + participant + "/cohort dataset");
        if (datasetId.equals(study.getParticipantAliasDatasetId()))
            messages.add("This is the " + participant + " alias dataset");
        if (datasetId.equals(study.getParticipantCommentDatasetId()))
            messages.add("This is the " + participant + " comment dataset");
        if (datasetId.equals(study.getParticipantVisitCommentDatasetId()))
            messages.add("This is the " + participant + "/visit comment dataset");
        return messages.isEmpty() ? null : String.join("&#013;", messages);
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
<%
    // Count the number of "special" datasets (participant/cohort, altId, comment, etc.)
    long specialDatasetCount = study.getDatasets().stream().map(dd->getTooltip(study, dd.getDatasetId())).filter(Objects::nonNull).count();
    if (specialDatasetCount > 0)
    {
%>
<br><br>The dataset<%=h(specialDatasetCount > 1 ? "s" : "")%> highlighted in <b>bold</b> below may warrant special attention.
For example, permissions set on the <%=h(study.getSubjectNounSingular().toLowerCase())%>/cohort dataset dictate who can view and<br>filter on cohorts. Read permissions
set on the alternate ID dataset will affect who can edit other datasets. Hover over the highlighted dataset labels for details.
<%
    }
%>
<style type="text/css">
    table.table {
        width: auto !important;
    }

    td.dataset-permission {
        padding: 5px 5px 0 5px !important;
    }
</style>
<labkey:form id="datasetSecurityForm" action="<%=urlFor(ApplyDatasetPermissionsAction.class)%>" onsubmit="LABKEY.setSubmit(true);" method="POST">
<%
    if (returnUrl != null)
        out.print(generateReturnUrlFormField(returnUrl));

    int row = 0;
    %><br/><table class="table table-striped table-bordered table-hover" id="datasetSecurityFormTable"><colgroup>
    <%
    for (int i = 0; i < restrictedGroups.size() + 1; i++)
    {
        %><col><%
    }
    %></colgroup>
    <tr class="<%=getShadeRowClass(row++)%>"><th>&nbsp;</th><%
    for (Group g : restrictedGroups)
    {
        %><th style="padding: 0 5px 0 5px;"><%=h(groupName(g))%></th><%
    }

    List<Role> possibleRoles = new ArrayList<>();
    List<DatasetDefinition> datasets = new ArrayList<>(study.getDatasets());
    datasets.sort(Comparator.comparing(DatasetDefinition::getLabel, String.CASE_INSENSITIVE_ORDER));

    if (!datasets.isEmpty())
    {
        if (study.getSecurityType() == SecurityType.ADVANCED_WRITE)
        {
            DatasetDefinition ds = datasets.get(0);
            SecurityPolicy dsPolicy = SecurityPolicyManager.getPolicy(ds);
            for (Role role : RoleManager.getAllRoles())
            {
                if (role.isApplicable(dsPolicy, ds))
                {
                    possibleRoles.add(role);
                }
            }
            possibleRoles.sort(RoleManager.ROLE_COMPARATOR);
        }
        else
        {
            possibleRoles.add(RoleManager.getRole(ReaderRole.class));
            // minor hack since we don't have "isApplicableReaderRole()" method
            var rdrr = RoleManager.getRole("org.labkey.niaid.permissions.RestrictedDatasetReaderRole");
            if (null != rdrr)
                possibleRoles.add(rdrr);
        }
    }

    %></tr>
    <tr class="<%=getShadeRowClass(row++)%>"><th>&nbsp;</th><%
    for (Group g : restrictedGroups)
    {
        %><td class="dataset-permission" data-toggle="tooltip" title='Set all values in column'>
        <%=select()
            .name(g.getName())
            .addOption(new OptionBuilder("<set all to...>", "").selected(true))
            .addOption("None")
            .addOptions(
                possibleRoles.stream()
                    .filter(pr->!pr.getExcludedPrincipals().contains(g)) // Filter out roles that can't be assigned to this group
                    .map(Role::getName)
            )
            .onChange("setColumnSelections(this); LABKEY.setDirty(true);")
            .className(null)
        %>
    </td><%
    }
    %></tr><%

    for (Dataset ds : datasets)
    {
        SecurityPolicy dsPolicy = SecurityPolicyManager.getPolicy(ds);

        String inputName = "dataset." + ds.getDatasetId();
        %><tr class="<%=getShadeRowClass(row++)%>"><%
        // Highlight "special" datasets (cohort, altid, comments, etc.) in the grid
        String toolTip = getTooltip(study, ds.getDatasetId());
        HtmlString toolTipHtml = null == toolTip ? HtmlString.EMPTY_STRING : unsafe(" data-toggle=\"tooltip\" title=\"" + toolTip + "\"");
        HtmlString label = null == toolTip ? h(ds.getLabel()) : HtmlStringBuilder.of().append(unsafe("<b>")).append(ds.getLabel()).append(unsafe("</b>")).getHtmlString();
        %><td<%=toolTipHtml%>><%=label%></td><%

        for (Group g : restrictedGroups)
        {
            List<Role> roles = dsPolicy.getAssignedRoles(g);
            Role assignedRole = roles.isEmpty() ? null : roles.get(0);

            boolean writePerm = assignedRole != null && assignedRole.getClass() == EditorRole.class;
            boolean readPerm = !writePerm && dsPolicy.hasNonInheritedPermission(g, ReadPermission.class);

            if (study.getSecurityType() == SecurityType.ADVANCED_READ && writePerm)
                readPerm = true;

            boolean noPerm = !writePerm && !readPerm && assignedRole == null;
            int id = g.getUserId();
            %><td style="text-align: left;" data-toggle="tooltip" title='<%=h(getTooltip(ds, g))%>'>
                <%=select()
                    .name(inputName)
                    .addOption(new OptionBuilder("None", id + "_NONE").selected(noPerm))
                    .addOptions(
                        possibleRoles.stream()
                            .filter(pr->!pr.getExcludedPrincipals().contains(g)) // Filter out roles that can't be assigned to this group
                            .map(r->new OptionBuilder(r.getName(), id + "_" + r.getClass().getName()).selected(r == assignedRole))
                    )
                    .onChange("LABKEY.setDirty(true);")
                    .className(null)
                %>
              </td><%
        }
        %></tr><%
    }
    %>
    </table>
    <%=button("Save").submit(true)%>
    <%=button("Set all to Reader").href("#").onClick("return setAllSelections('Reader');")%>
    <%
    if (study.getSecurityType() == SecurityType.ADVANCED_WRITE)
    {
    %>
        <%=button("Set all to Editor").href("#").onClick("return setAllSelections('Editor');")%><%
    }
    %>
    <%=button("Clear All").href("#").onClick("return setAllSelections('None');")%>
    <%=button("Cancel").href(urlFor(ManageStudyAction.class)).onClick("LABKEY.setSubmit(true);")%>
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
    LABKEY.setDirty(true);
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
    LABKEY.setDirty(true);
    return false;
}
</script>
