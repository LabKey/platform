<%
/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.Permission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.issue.ColumnType" %>
<%@ page import="org.labkey.issue.IssueUpdateEmailTemplate" %>
<%@ page import="org.labkey.issue.IssuesController.AdminBean" %>
<%@ page import="org.labkey.issue.IssuesController.ConfigureIssuesAction" %>
<%@ page import="org.labkey.issue.IssuesController.ConfigureIssuesForm" %>
<%@ page import="org.labkey.issue.IssuesController.ListAction" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssueManager.CustomColumn" %>
<%@ page import="org.labkey.issue.model.IssueManager.CustomColumnConfiguration" %>
<%@ page import="org.labkey.issue.model.KeywordManager" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminBean> me = (HttpView<AdminBean>) HttpView.currentView();
    AdminBean bean = me.getModelBean();
    CustomColumnConfiguration ccc = bean.ccc;
    Container c = getContainer();
%>
<br>
<table>
<tr><td>
    <%= button("Back to " + bean.entryTypeNames.pluralName.getSource()).href(buildURL(ListAction.class) + DataRegion.LAST_FILTER_PARAM + "=true") %>
    <%= button("Customize Email Template").href(urlProvider(AdminUrls.class).getCustomizeEmailURL(c, IssueUpdateEmailTemplate.class, getActionURL())) %>
</td></tr>
<tr><td>&nbsp;</td></tr>
<%=formatMissedErrorsInTable("form", 1)%>
</table>
<form name="entryTypeNames" action="<%=h(buildURL(ConfigureIssuesAction.class))%>" method="POST">

<table><tr>
    <td valign=top>
        <table>
            <tr><td colspan=2 align=center><div class="labkey-form-label"><b>Required Fields</b></div></td></tr>
            <tr><td colspan=2>Select fields to be required when entering or updating <%=h(bean.getEntryTypeNames().getIndefiniteSingularArticle())%> <%=h(bean.getEntryTypeNames().singularName)%>:</td></tr>
            <tr><td colspan=2>&nbsp;</td></tr>
            <tr>
                <td><input type="checkbox" name="requiredFields"<%=checked(isRequired("comment", bean.getRequiredFields()))%> value="comment">Comments (new issues only)</td><%
            List<ColumnInfo> columns = bean.getColumns();
            for (int i = 0; i < columns.size(); i++)
            {
                ColumnInfo info = columns.get(i);
                boolean startNewRow = i % 2 == 1;
                if (startNewRow)
                {
        %>
            <tr><%

                }
        %>
                <td><input type="checkbox" name="requiredFields"<%=checked(isRequired(info.getName(), bean.getRequiredFields()))%><%=disabled(isPickList(ccc, info) && !hasKeywords(c, info))%> value="<%=h(info.getName())%>"><%=h(getCaption(ccc, info))%></td><%

                if (!startNewRow)
                {
        %>
            </tr><%

                }
            }
        %>
        </table><br>
    </td>
    <td valign=top>
        <table>
        <tr><td colspan=3 align=center><div class="labkey-form-label"><b>Custom Fields</b></div></td></tr>
        <tr><td colspan=3>Enter captions below to use custom fields in this <%=h(bean.entryTypeNames.pluralName)%> list:</td></tr>
        <tr><td colspan=3>&nbsp;</td></tr>
        <tr><td>Type</td><td><input name="type" value="<%=h(ccc.getCaption("type"))%>" size=20></td></tr>
        <tr><td>Area</td><td><input name="area" value="<%=h(ccc.getCaption("area"))%>" size=20></td></tr>
        <tr><td>Priority</td><td><input name="priority" value="<%=h(ccc.getCaption("priority"))%>" size=20></td></tr>
        <tr><td>Milestone</td><td><input name="milestone" value="<%=h(ccc.getCaption("milestone"))%>" size=20></td></tr>
        <tr><td>Resolution</td><td><input name="resolution" value="<%=h(ccc.getCaption("resolution"))%>" size=20></td></tr>
        <tr><td>Integer1</td><td><input name="int1" value="<%=h(ccc.getCaption("int1"))%>" size=20></td></tr>
        <tr><td>Integer2</td><td><input name="int2" value="<%=h(ccc.getCaption("int2"))%>" size=20></td></tr>
        <%=text(getStringFieldHtml(ccc, "string1"))%>
        <%=text(getStringFieldHtml(ccc, "string2"))%>
        <%=text(getStringFieldHtml(ccc, "string3"))%>
        <%=text(getStringFieldHtml(ccc, "string4"))%>
        <%=text(getStringFieldHtml(ccc, "string5"))%>
        <tr><td colspan=2>&nbsp;</td></tr>
        </table>
    </td>
</tr>
<tr>
    <td valign="top" colspan="2">
        <table width="100%">
            <tr><td align="center"><div class="labkey-form-label"><b>Additional Configuration</b></div></td></tr>
            <tr>
                <td>
                    <table>
                        <tr>
                            <td>Singular item name</td>
                            <td><input type="text" name="<%=text(ConfigureIssuesForm.ParamNames.entrySingularName.name())%>"
                                       value="<%=h(bean.entryTypeNames.singularName)%>" size="20"/></td>
                        </tr>
                        <tr>
                            <td>Plural items name</td>
                            <td><input type="text" name="<%=text(ConfigureIssuesForm.ParamNames.entryPluralName.name())%>"
                                       value="<%=h(bean.entryTypeNames.pluralName)%>" size="20"/></td>
                        </tr>
                        <tr>
                            <td>Comment sort direction</td>
                            <td>
                                <%=PageFlowUtil.strSelect(ConfigureIssuesForm.ParamNames.direction.name(), Arrays.asList(Sort.SortDirection.values()), java.util.Arrays.asList("Oldest first", "Newest first"), bean.commentSort) %>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td>
                    <table>
                        <tr><td colspan="2">Populate the assigned to list from:</td></tr>
                        <tr>
                            <td>
                                <input onchange="assignedToGroup.disabled=true;updateAssignedToUser();" type="radio" name="assignedToMethod" value="ProjectUsers"<%=checked(null == bean.assignedToGroup)%> />
                            </td>
                            <td>All Project Users</td>
                        </tr>
                        <tr>
                            <td>
                                <input onchange="assignedToGroup.disabled=false;updateAssignedToUser();" type="radio" name="assignedToMethod" value="Group"<%=checked(null != bean.assignedToGroup)%> />
                            </td>
                            <td>Specific Group
                                <select name="assignedToGroup" onchange="updateAssignedToUser();"<%=disabled(null == bean.assignedToGroup)%> ><%
                                    for (Group group : SecurityManager.getGroups(c.getProject(), true))
                                    {
                                        // 19532 partial. Only show Site: Users option to site admins
                                        if (!group.isGuests() && (!group.isUsers() || getUser().isSiteAdmin()))
                                        {
                                            String displayText = (group.isProjectGroup() ? "" : "Site:") + group.getName();
                                            out.println("<option value=\"" + group.getUserId() + "\"" + selected(null != bean.assignedToGroup && group.getUserId() == bean.assignedToGroup.getUserId()) + ">" + h(displayText) + "</option>");
                                        }
                                    }
                                %>
                                </select>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td>
                    <table>
                        <tr><td colspan="2">Set default assigned to user:</td></tr>
                        <tr>
                            <td>
                                <input onchange="defaultUser.disabled=true;" type="radio" name="assignedToUser" value="NoDefaultUser"<%=checked(null == bean.defaultUser)%> />
                            </td>
                            <td>No default</td>
                        </tr>
                        <tr>
                            <td>
                                <input onchange="defaultUser.disabled=false;" type="radio" name="assignedToUser" value="SpecificUser"<%=checked(null != bean.defaultUser)%> />
                            </td>
                            <td>Specific User
                                <select name="defaultUser"<%=disabled(null == bean.defaultUser)%> ></select>
                                <script>
                                    function updateAssignedToUser() {
                                        //NOTE: need to handle special user groups
                                        var e = document.getElementsByName("assignedToGroup")[0];
                                        var groupId = e.options[e.selectedIndex].value;
                                        var config = {allMembers: true};
                                        // if "All project Users" is selected than groupId is not used to obtain all project users
                                        if (!document.getElementsByName("assignedToMethod")[0].checked)
                                        {
                                            config["groupId"] = parseInt(groupId)
                                        }
                                        config["success"] = function(data) {
                                            var e = document.getElementsByName("defaultUser")[0];
                                            e.options.length = 0;

                                            Ext4.each(data.users, function(user){
                                                var option = document.createElement("option");
                                                option.text = user.displayName;
                                                option.value = user.userId;
                                                e.add(option);
                                            }, this);
                                        }

                                        LABKEY.Security.getUsers(config);
                                    }
                                    Ext4.onReady(updateAssignedToUser);
                                </script>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td><%= button("Update").submit(true) %></td>
            </tr>
        </table>
    </td>
</tr>
</table>
</form>

<%!
    public boolean isRequired(String name, String requiredFields)
    {
        if (requiredFields != null)
        {
            return requiredFields.indexOf(name.toLowerCase()) != -1;
        }
        return false;
    }

    public String getCaption(CustomColumnConfiguration ccc, ColumnInfo col)
    {
        CustomColumn cc = ccc.getCustomColumn(col.getName().toLowerCase());

        if (null != cc)
            return cc.getCaption();

        return col.getLabel();
    }

    public boolean hasKeywords(Container c, ColumnInfo col)
    {
        ColumnType type = ColumnType.forName(col.getColumnName());

        return (null == type || KeywordManager.getKeywords(c, type).size() > 0);
    }

    public boolean isPickList(CustomColumnConfiguration ccc, ColumnInfo col)
    {
        String name = col.getColumnName();

        if (ccc.hasPickList(name.toLowerCase()))
        {
            //If the column actually is a pick list return true.
            return true;
        }

        ColumnType type = ColumnType.forName(name);

        // If the column is Type, Area, Priority, or Milestone also return true
        // this way if they don't have keywords they are also greyed out.
        return (null != type && type.isStandard());
    }

    public String getStringFieldHtml(CustomColumnConfiguration ccc, String name)
    {
        StringBuilder sb = new StringBuilder("        <tr><td>");
        sb.append(StringUtils.capitalize(name));
        sb.append("</td><td>");
        sb.append("<input name=\"");
        sb.append(name);
        sb.append("\" value=\"");
        sb.append(h(ccc.getCaption(name)));
        sb.append("\" size=20> <input type=\"checkbox\" name=\"");
        sb.append(IssueManager.PICK_LIST_NAME);
        sb.append("\" value=\"");
        sb.append(name);
        sb.append("\"");
        sb.append(checked(ccc.hasPickList(name)));
        sb.append("> Pick list</td>");

        CustomColumn cc = ccc.getCustomColumn(name);
        Class<? extends Permission> perm = null != cc ? cc.getPermission() : ReadPermission.class;

        sb.append("<td><select name=\"permissions\">");
        sb.append("<option value=\"read\"" + selected(perm.equals(ReadPermission.class)) + ">Read</option>");
        sb.append("<option value=\"insert\"" + selected(perm.equals(InsertPermission.class)) + ">Insert</option>");
        sb.append("<option value=\"admin\"" + selected(perm.equals(AdminPermission.class)) + ">Admin</option>");
        sb.append("</select></td>");

        sb.append("</tr>");

        return sb.toString();
    }
%>
<br>
<% me.include(bean.keywordView, out); %>
