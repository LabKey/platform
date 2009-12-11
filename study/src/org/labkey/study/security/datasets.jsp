<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.ReadPermission"%>
<%@ page import="org.labkey.api.security.permissions.ReadSomePermission"%>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission"%>
<%@ page import="org.labkey.api.study.DataSet"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="java.util.ArrayList" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
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
    HttpView<StudyImpl> me = (HttpView<StudyImpl>) HttpView.currentView();
    StudyImpl study = me.getModelBean();
    SecurityPolicy studyPolicy = SecurityManager.getPolicy(study);

    Group[] groups = SecurityManager.getGroups(study.getContainer().getProject(), true);

    ArrayList<Group> readGroups = new ArrayList<Group>();
    ArrayList<Group> restrictedGroups = new ArrayList<Group>();
    ArrayList<Group> noReadGroups = new ArrayList<Group>();
    for (Group g : groups)
    {
        if (g.getUserId() == Group.groupAdministrators)
            continue;
        if (studyPolicy.hasPermission(g, ReadPermission.class))
            readGroups.add(g);
        else if (studyPolicy.hasPermission(g, ReadSomePermission.class))
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
    These groups do not have read permissions.  (Note: a user may belong to more that one group, see documentation.)
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
        if (restrictedGroups.size() == 0)
        {
    %>
        There are no other groups with access to some datasets. To grant access to individual datasets select the
        'some' option for the specific group above.
    <%
        }
        else
        {
    %>
        These groups may be given access to individual datasets.
    <%
        }
}
%>

<form id="datasetSecurityForm" action="applyDatasetPermissions.post" method="POST">
<%
    String redir = (String)HttpView.currentContext().get("redirect");
    if (redir != null)
        out.write("<input type=\"hidden\" name=\"redirect\" value=\"" + h(redir) + "\">");

    int row = 0;
    %><br/><table class="labkey-data-region labkey-show-borders"><colgroup>
    <%
    for (int i = 0; i < restrictedGroups.size() + 1; i++)
    {
        %><col><%
    }
    %></colgroup>
    <tr class="<%=row++%2==0?"labkey-alternate-row":"labkey-row"%>"><th>&nbsp;</th><%
    for (Group g : restrictedGroups)
    {
        %><th style="padding: 0 5px 0 5px;"><%=h(groupName(g))%></th><%
    }
    %></tr><%
    DataSet[] datasets = study.getDataSets();
    for (DataSet ds : datasets)
    {
        SecurityPolicy dsPolicy = SecurityManager.getPolicy(ds);

        String inputName = "dataset." + ds.getDataSetId();
        %><tr class="<%=row++%2==0?"labkey-alternate-row":"labkey-row"%>"><td><%=h(ds.getLabel())%></td><%
        for (Group g : restrictedGroups)
        {
            boolean writePerm = dsPolicy.hasPermission(g, UpdatePermission.class);
            boolean readPerm = !writePerm && dsPolicy.hasPermission(g, ReadPermission.class);

            if (study.getSecurityType() == SecurityType.ADVANCED_READ && writePerm)
                readPerm = true;

            boolean noPerm = !writePerm && !readPerm;
            int id = g.getUserId();
            %><td align=center>
                <select name="<%=inputName%>">
                    <option value="NONE_<%=id%>" <%=noPerm ? "selected" : ""%>>None</option>
                    <option value="READ_<%=id%>" <%=readPerm ? "selected" : ""%>>Read</option><%
                    if (study.getSecurityType() == SecurityType.ADVANCED_WRITE)
                    {
                    %>
                    <option value="WRITE_<%=id%>" <%=writePerm ? "selected" : ""%>>Edit</option>
                    <%
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
            <td><%=PageFlowUtil.generateSubmitButton("Save")%></td>
            <td><%=PageFlowUtil.generateButton("Set all to Read", "#", "return setAllSelections('Read');")%></td>
            <td><%=PageFlowUtil.generateButton("Set all to Edit", "#", "return setAllSelections('Edit');")%></td>
            <td><%=PageFlowUtil.generateButton("Clear All", "#", "return setAllSelections('None');")%></td>
        </tr></table>
</form>

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
</script>
