<%
/*
 * Copyright (c) 2004-2015 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="java.util.LinkedList" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("issues/admin.js"));
        return resources;
    }
%>
<%
    HttpView<AdminBean> me = (HttpView<AdminBean>) HttpView.currentView();
    AdminBean bean = me.getModelBean();
    CustomColumnConfiguration ccc = bean.ccc;
    Container c = getContainer();

    List<String> moveToContainerIds = new LinkedList<>();
    if (bean.moveToContainers != null)
    {
        for (Container container : bean.moveToContainers)
            moveToContainerIds.add(container.getId());
    }

%>

<script type="text/javascript">
    // NOTE: needed for admin.js
    var curDefaultUser = <%=bean.defaultUser == null ? null : bean.defaultUser.getUserId()%>;
    var curDefaultContainers = <%= new JSONArray(moveToContainerIds.toString())%>;
    var curInheritContainer = "<%= h(bean.inheritFromContainer == null ? "" : bean.inheritFromContainer.getId())%>";
    function submitFormUserConfirm()
    {

        var inheritingContainerExists = "<%=h(bean.inheritingContainersExists)%>";

        var customFieldValType = document.querySelector("input[name=type]").value;
        var customFieldValTypeStored = "<%=h(ccc.getCaption("type"))%>";

        var customFieldValArea = document.querySelector("input[name=area]").value;
        var customFieldValAreaStored = "<%=h(ccc.getCaption("area"))%>";

        var customFieldValPriority = document.querySelector("input[name=priority]").value;
        var customFieldValPriorityStored = "<%=h(ccc.getCaption("priority"))%>";

        var customFieldValMilestone = document.querySelector("input[name=milestone]").value;
        var customFieldValMilestoneStored = "<%=h(ccc.getCaption("milestone"))%>";

        var customFieldValResolution = document.querySelector("input[name=resolution]").value;
        var customFieldValResolutionStored = "<%=h(ccc.getCaption("resolution"))%>";

        var customFieldValRelated = document.querySelector("input[name=related]").value;
        var customFieldValRelatedStored = "<%=h(ccc.getCaption("related"))%>";

        var customFieldValInteger1 = document.querySelector("input[name=int1]").value;
        var customFieldValInteger1Stored = "<%=h(ccc.getCaption("int1"))%>";

        var customFieldValInteger2 = document.querySelector("input[name=int2]").value;
        var customFieldValInteger2Stored = "<%=h(ccc.getCaption("int2"))%>";

        var customFieldValString1 = document.querySelector("input[name=string1]").value;
        var customFieldValString1Stored = "<%=h(ccc.getCaption("string1"))%>";

        var customFieldValString2 = document.querySelector("input[name=string2]").value;
        var customFieldValString2Stored = "<%=h(ccc.getCaption("string2"))%>";

        var customFieldValString3 = document.querySelector("input[name=string3]").value;
        var customFieldValString3Stored = "<%=h(ccc.getCaption("string3"))%>";

        var customFieldValString4 = document.querySelector("input[name=string4]").value;
        var customFieldValString4Stored = "<%=h(ccc.getCaption("string4"))%>";

        var customFieldValString5 = document.querySelector("input[name=string5]").value;
        var customFieldValString5Stored = "<%=h(ccc.getCaption("string5"))%>";

        //for current folder inheriting settings from another folder for the very first time
        if(document.querySelector("input[name=inheritFromContainerSelect]").disabled == false && curInheritContainer == "")
        {
            if(isCustomColumnOccupied(customFieldValType) || isCustomColumnOccupied(customFieldValArea) ||
                    isCustomColumnOccupied(customFieldValPriority) || isCustomColumnOccupied(customFieldValMilestone) ||
                    isCustomColumnOccupied(customFieldValResolution) || isCustomColumnOccupied(customFieldValRelated) ||
                    isCustomColumnOccupied(customFieldValInteger1) || isCustomColumnOccupied(customFieldValInteger2) ||
                    isCustomColumnOccupied(customFieldValString1) || isCustomColumnOccupied(customFieldValString2) ||
                    isCustomColumnOccupied(customFieldValString3) || isCustomColumnOccupied(customFieldValString4) ||
                    isCustomColumnOccupied(customFieldValString5))
            {

                var result =  confirm("Custom Fields of current folder will get overridden.");

                //submit only if user clicks on OK
                if(result == true)
                {
                    document.getElementById("adminViewOfIssueList").submit();
                    return;
                }
                //do nothing otherwise.
                else
                {
                    location.reload();
                    return;
                }
            }
        }

        //for current folder with inheriting folders
        if(inheritingContainerExists == "true")
        {
            if(isCustomColumnModified(customFieldValType, customFieldValTypeStored) ||
                    isCustomColumnModified(customFieldValArea, customFieldValAreaStored) ||
                    isCustomColumnModified(customFieldValPriority, customFieldValPriorityStored) ||
                    isCustomColumnModified(customFieldValMilestone, customFieldValMilestoneStored) ||
                    isCustomColumnModified(customFieldValResolution, customFieldValResolutionStored) ||
                    isCustomColumnModified(customFieldValRelated, customFieldValRelatedStored) ||
                    isCustomColumnModified(customFieldValInteger1, customFieldValInteger1Stored) ||
                    isCustomColumnModified(customFieldValInteger2, customFieldValInteger2Stored) ||
                    isCustomColumnModified(customFieldValString1, customFieldValString1Stored) ||
                    isCustomColumnModified(customFieldValString2, customFieldValString2Stored) ||
                    isCustomColumnModified(customFieldValString3, customFieldValString3Stored) ||
                    isCustomColumnModified(customFieldValString4, customFieldValString4Stored) ||
                    isCustomColumnModified(customFieldValString5, customFieldValString5Stored))
            {

                    var result2 = confirm("Found one or more folders with settings inherited from the current folder: Adding new Custom Fields will override Custom Fields of inheriting folders.");


                    if (result2 == true)
                    {
                        document.getElementById("adminViewOfIssueList").submit();
                        return;
                    }
                    //do nothing otherwise.
                    else
                    {
                        location.reload();
                        return;
                    }
            }

        }

        //'submit' by default when user clicks on "Update" - only exception is the above one where we ask user to confirm whether
        //to submit or not.
        document.getElementById("adminViewOfIssueList").submit();
    }

    function isCustomColumnModified(str1, str2)
    {
     if(str1 != "" && str2 =="")
        return true;

        return false;
    }

    function isCustomColumnOccupied(str1)
    {
        if(str1 != "")
            return true;

        return false;
    }



</script>

<br>
<table>
<tr><td>
    <%= button("Back to " + bean.entryTypeNames.pluralName.getSource()).href(buildURL(ListAction.class) + DataRegion.LAST_FILTER_PARAM + "=true") %>
    <%= button("Customize Email Template").href(urlProvider(AdminUrls.class).getCustomizeEmailURL(c, IssueUpdateEmailTemplate.class, getActionURL())) %>
</td></tr>
<tr><td>&nbsp;</td></tr>
<%=formatMissedErrorsInTable("form", 1)%>
</table>
<labkey:form id="adminViewOfIssueList" name="entryTypeNames" action="<%=h(buildURL(ConfigureIssuesAction.class))%>" method="POST">

<table>
    <tr>
        <td valign="top" colspan="2">
            <table width="110%">
                <tr><td align="center" colspan="2"><div class="labkey-form-label"><b>Configuration</b></div></td></tr>
                <tr>
                    <td valign=top>
                        <table>
                            <tr>
                                <td>Singular item name</td>
                                        <td><input type="text" name="<%=text(ConfigureIssuesForm.ParamNames.entrySingularName.name())%>"
                                           value="<%=h(bean.entryTypeNames.singularName)%>" size="20" <%=disabled(bean.inheritFromContainerExists)%> /></td>


                            </tr>
                            <tr>
                                <td>Plural items name</td>
                                       <td><input type="text" name="<%=text(ConfigureIssuesForm.ParamNames.entryPluralName.name())%>"
                                           value="<%=h(bean.entryTypeNames.pluralName)%>" size="20" <%=disabled(bean.inheritFromContainerExists)%> /></td>
                            </tr>
                            <tr>
                                <td> Comment sort direction </td>
                                <td>
                                    <%
                                        if(bean.inheritFromContainerExists)
                                        {
                                    %>
                                            <input type="text" value="<%=h(bean.commentSort.getSqlDir().equals("ASC") ? "Oldest first" : "Newest first")%>" disabled>
                                    <%
                                        }
                                        else
                                        {
                                    %>
                                        <%=PageFlowUtil.strSelect(ConfigureIssuesForm.ParamNames.direction.name(),
                                                Arrays.asList(Sort.SortDirection.values()), java.util.Arrays.asList("Oldest first", "Newest first"), bean.commentSort)%>
                                    <%
                                        }
                                    %>

                                </td>
                            </tr>
                        </table>
                    </td>
                    <td>
                        <table align="right">

                            <tr><td colspan="2">Inherit Admin Setting from folder:</td></tr>
                            <tr>
                                <td>
                                    <input onchange="updateInheritFromContainerSelect()" type="radio" name="inheritFromContainer" value="DoNotInheritFromContainer"<%=checked(bean.inheritFromContainer == null)%>/>
                                </td>
                                <td>None</td>
                            </tr>
                            <% // if inheriting containers exists, show this message
                                if(bean.inheritingContainersExists)
                                {
                            %>
                                    <tr><td colspan="2"><span style="font-style: italic" color=#4169e1> Unable to inherit admin settings from other folders: Found one or more </span></td></tr>
                                    <tr><td colspan="2"><span style="font-style: italic" color=#4169e1> folders with settings inherited from the current folder. </span></td></tr>
                            <%
                                }
                            %>
                                <tr>
                                    <td>
                                        <input onchange="updateInheritFromContainerSelect()" type="radio" name="inheritFromContainer" value="InheritFromSpecificContainer"<%=checked(bean.inheritFromContainer != null)%> <%=disabled(bean.inheritingContainersExists)%> />
                                    </td>
                                    <td>
                                        <div class="inheritFromContainerCheckCombo"></div>
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
                                    <input onchange="assignedToGroup.disabled=false;updateAssignedToUser();" type="radio" name="assignedToMethod" value="Group"<%=checked(null != bean.assignedToGroup)%>/>
                                    <td>Specific Group
                                        <select name="assignedToGroup" onchange="updateAssignedToUser()"<%=disabled(null == bean.assignedToGroup)%> >
                                        <%
                                            for (Group group : org.labkey.api.security.SecurityManager.getGroups(c.getProject(), true))
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
                                </td>

                            </tr>
                        </table>
                    </td>
                    <td>
                        <table align="right">
                            <tr><td colspan="2">Set move to folder:</td></tr>
                            <tr>
                                <td>
                                   <input onchange="toggleMoveToContainerSelect()" type="radio" name="moveToContainer" value="NoMoveToContainer"<%=checked(bean.moveToContainers.size() == 0)%> <%=disabled(bean.inheritFromContainerExists)%> />
                                </td>
                                <td>None</td>
                            </tr>
                            <tr>
                                <td>
                                     <input onchange="toggleMoveToContainerSelect()" type="radio" name="moveToContainer" value="SpecificMoveToContainer"<%=checked(bean.moveToContainers.size() > 0)%> <%=disabled(bean.inheritFromContainerExists)%> />
                                </td>
                                <td>
                                    <div class="moveToContainerCheckCombo"></div>
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
                                    <select onchange="updateCurDefaultUser();" name="defaultUser"<%=disabled(null == bean.defaultUser)%> ></select>
                                </td>
                            </tr>
                        </table>
                    </td>
                    <td>
                        <table align="right">
                            <td>Folder of related issues list</td>
                            <td>
                                <input type="text" name="relatedIssuesList"
                                            <%
                                                Container related = IssueManager.getRelatedIssuesList(c);
                                                String relatedStr = related == null ? "" : related.getPath();
                                            %>
                                        value="<%=h(relatedStr)%>" size="45" <%=disabled(bean.inheritFromContainerExists)%> />
                            </td>
                        </table>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
    <td valign=top>
        <table width="100%">
            <tr><td colspan=2 align=center><div class="labkey-form-label"><b>Required Fields</b></div></td></tr>
            <tr><td colspan=2>Select fields to be required when entering or updating <%=h(bean.getEntryTypeNames().getIndefiniteSingularArticle())%> <%=h(bean.getEntryTypeNames().singularName)%>:</td></tr>
            <tr><td colspan=2>&nbsp;</td></tr>
            <tr>
                    <td><input type="checkbox" name="requiredFields"<%=checked(isRequired("comment", bean.getRequiredFields()))%>
                            <%=disabled(bean.inheritFromContainerExists)%> value="comment">Comments (new issues only)</td>
            <%
            List<ColumnInfo> columns = bean.getColumns();
            for (int i = 0; i < columns.size(); i++)
            {
                ColumnInfo info = columns.get(i);
                boolean startNewRow = i % 2 == 1;
                if (startNewRow)
                {
            %>
            <tr>
            <%
                }
            %>
            <td><input type="checkbox" name="requiredFields"<%=checked(isRequired(info.getName(), bean.getRequiredFields()))%>
            <%=disabled((isPickList(ccc, info) && !hasKeywords(c, info)) || (bean.inheritFromContainerExists && bean.isRequiredFieldInherited(info.getName())) || (bean.getInheritedFlag(info.getColumnName())))%>
                       value="<%=h(info.getName())%>"><%=h(getCaption(ccc, info))%>

            </td>
            <%
                if (!startNewRow)
                {
            %>
            </tr>
            <%

                }
            }
            %>

        </table><br/>
    </td>
    <td valign=top>
        <table width="120%">
        <tr><td colspan=2 align="center" ><div class="labkey-form-label"><b>Custom Fields</b></div></td></tr>
        <tr><td colspan=2>Enter captions below to use custom fields in this <%=h(bean.entryTypeNames.pluralName)%> list:</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
            <tr><td>Type</td><td><input name="type" value="<%=h(ccc.getCaption("type"))%>" size=20 <%=disabled(bean.inheritFromContainerExists)%>></td></tr>
            <tr><td>Area</td><td><input name="area" value="<%=h(ccc.getCaption("area"))%>" size=20 <%=disabled(bean.inheritFromContainerExists)%>></td></tr>
            <tr><td>Priority</td><td><input name="priority" value="<%=h(ccc.getCaption("priority"))%>" size=20 <%=disabled(bean.inheritFromContainerExists)%>></td></tr>
            <tr><td>Milestone</td><td><input name="milestone" value="<%=h(ccc.getCaption("milestone"))%>" size=20 <%=disabled(bean.inheritFromContainerExists)%>></td></tr>
            <tr><td>Resolution</td><td><input name="resolution" value="<%=h(ccc.getCaption("resolution"))%>" size=20 <%=disabled(bean.inheritFromContainerExists)%>></td></tr>
            <tr><td>Related</td><td><input name="related" value="<%=h(ccc.getCaption("related"))%>" size=20 <%=disabled(bean.inheritFromContainerExists)%>></td></tr>
            <tr><td>Integer1</td><td><input name="int1" value="<%=h(ccc.getCaption("int1"))%>" size=20 <%=disabled(bean.isInt1Inherited())%>></td></tr>
            <tr><td>Integer2</td><td><input name="int2" value="<%=h(ccc.getCaption("int2"))%>" size=20 <%=disabled(bean.isInt2Inherited())%>></td></tr>
            <%=text(getStringFieldHtml(bean, ccc, "string1"))%>
            <%=text(getStringFieldHtml(bean, ccc, "string2"))%>
            <%=text(getStringFieldHtml(bean, ccc, "string3"))%>
            <%=text(getStringFieldHtml(bean, ccc, "string4"))%>
            <%=text(getStringFieldHtml(bean, ccc, "string5"))%>
        <tr><td colspan=2>&nbsp;</td></tr>
        </table>
    </td>
</tr>
    <tr>
        <td colspan="2">
            <%=button("Update").onClick("submitFormUserConfirm()") %>
        </td>
    </tr>
</table>
</labkey:form>

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
        Container con = IssueManager.getInheritFromOrCurrentContainer(c);//get "parent's" settings, if any.

        return (null == type || KeywordManager.getKeywords(con, type).size() > 0);
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


    public String getStringFieldHtml(AdminBean bean, CustomColumnConfiguration ccc, String name)
    {

        String input = null;
        String select = null;
        String inputCheckbox = null;
        StringBuilder sb = new StringBuilder("        <tr><td>");
        sb.append(StringUtils.capitalize(name));
        sb.append("</td><td>");

        if(bean.getInheritedFlag(name))
        {
            input = "<input disabled name=\"";
            select = "<td><select disabled name=\"permissions\">";
            inputCheckbox = "\" size=20> <input disabled type=\"checkbox\" name=\"";
        }
        else
        {
            input = "<input name=\"";
            select = "<td><select name=\"permissions\">";
            inputCheckbox = "\" size=20> <input type=\"checkbox\" name=\"";
        }

        sb.append(input);
        sb.append(name);
        sb.append("\" value=\"");
        sb.append(h(ccc.getCaption(name)));
        sb.append(inputCheckbox);
        sb.append(IssueManager.PICK_LIST_NAME);
        sb.append("\" value=\"");
        sb.append(name);
        sb.append("\"");
        sb.append(checked(ccc.hasPickList(name)));
        sb.append("> Pick list</td>");

        CustomColumn cc = ccc.getCustomColumn(name);
        Class<? extends Permission> perm = null != cc ? cc.getPermission() : ReadPermission.class;

        sb.append(select);
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
