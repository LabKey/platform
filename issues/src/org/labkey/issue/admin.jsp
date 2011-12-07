<%
/*
 * Copyright (c) 2004-2011 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.issue.IssueUpdateEmailTemplate" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.HString" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<IssuesController.AdminBean> me = (HttpView<IssuesController.AdminBean>) HttpView.currentView();
    IssuesController.AdminBean bean = me.getModelBean();
    Set<String> pickListColumns = bean.ccc.getPickListColumns();
    Map<String, String> captions = bean.ccc.getColumnCaptions();
    Container c = me.getViewContext().getContainer();
%>
<br>
<table>
<tr><td>
    <%=generateButton("Back to " + bean.entryTypeNames.pluralName, "list.view?" + DataRegion.LAST_FILTER_PARAM + "=true")%>
    <%=generateButton("Customize Email Template", urlProvider(AdminUrls.class).getCustomizeEmailURL(c, IssueUpdateEmailTemplate.class, me.getViewContext().getActionURL()))%>
</td></tr>
<tr><td>&nbsp;</td></tr>
<%=formatMissedErrorsInTable("form", 1)%>
</table>
<form name="entryTypeNames" action="configureIssues.post" method="POST">

<table><tr>
    <td valign=top>
        <table>
            <tr><td colspan=2 align=center><div class="labkey-form-label"><b>Required Fields</b></div></td></tr>
            <tr><td colspan=2>Select fields to be required when entering or updating <%=bean.getEntryTypeNames().getIndefiniteSingularArticle()%> <%=bean.getEntryTypeNames().singularName%>:</td></tr>
            <tr><td colspan=2>&nbsp;</td></tr>
            <tr><td><input type="checkbox" name="requiredFields" <%=isRequired("comment", bean.getRequiredFields()) ? "checked " : ""%> value="comment">Comments (new issues only)</td>
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
                <td><input type="checkbox" name="requiredFields" <%=isRequired(info.getName(), bean.getRequiredFields()) ? "checked " : ""%> <%=isPickList(info) && !hasKeywords(info) ? "disabled" : "" %> value="<%=info.getName()%>"><%=getCaption(info)%></td>
        <%
                if (!startNewRow)
                {
        %>
            </tr>
        <%
                }
            }
        %>
        </table><br>
    </td>
    <td valign=top>
        <table>
        <tr><td colspan=2 align=center><div class="labkey-form-label"><b>Custom Fields</b></div></td></tr>
        <tr><td colspan=2>Enter captions below to use custom fields in this <%=bean.entryTypeNames.pluralName%> list:</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td>Type</td><td><input name="type" value="<%=h(captions.get("type"))%>" size=20></td></tr>
        <tr><td>Area</td><td><input name="area" value="<%=h(captions.get("area"))%>" size=20></td></tr>
        <tr><td>Priority</td><td><input name="priority" value="<%=h(captions.get("priority"))%>" size=20></td></tr>
        <tr><td>Milestone</td><td><input name="milestone" value="<%=h(captions.get("milestone"))%>" size=20></td></tr>
        <tr><td>Resolution</td><td><input name="resolution" value="<%=h(captions.get("resolution"))%>" size=20></td></tr>
        <tr><td>Integer1</td><td><input name="int1" value="<%=h(captions.get("int1"))%>" size=20></td></tr>
        <tr><td>Integer2</td><td><input name="int2" value="<%=h(captions.get("int2"))%>" size=20></td></tr>
        <tr><td>String1</td><td><input name="string1" value="<%=h(captions.get("string1"))%>" size=20><input type="checkbox" name="<%=IssueManager.CustomColumnConfiguration.PICK_LIST_NAME%>" value="string1" <%=pickListColumns.contains("string1") ? "checked" : ""%>>Use pick list for this field</td></tr>
        <tr><td>String2</td><td><input name="string2" value="<%=h(captions.get("string2"))%>" size=20><input type="checkbox" name="<%=IssueManager.CustomColumnConfiguration.PICK_LIST_NAME%>" value="string2" <%=pickListColumns.contains("string2") ? "checked" : ""%>>Use pick list for this field</td></tr>
        <tr><td>String3</td><td><input name="string3" value="<%=h(captions.get("string3"))%>" size=20><input type="checkbox" name="<%=IssueManager.CustomColumnConfiguration.PICK_LIST_NAME%>" value="string3" <%=pickListColumns.contains("string3") ? "checked" : ""%>>Use pick list for this field</td></tr>
        <tr><td>String4</td><td><input name="string4" value="<%=h(captions.get("string4"))%>" size=20><input type="checkbox" name="<%=IssueManager.CustomColumnConfiguration.PICK_LIST_NAME%>" value="string4" <%=pickListColumns.contains("string4") ? "checked" : ""%>>Use pick list for this field</td></tr>
        <tr><td>String5</td><td><input name="string5" value="<%=h(captions.get("string5"))%>" size=20><input type="checkbox" name="<%=IssueManager.CustomColumnConfiguration.PICK_LIST_NAME%>" value="string5" <%=pickListColumns.contains("string5") ? "checked" : ""%>>Use pick list for this field</td></tr>
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
                            <td><input type="text" name="<%=IssuesController.ConfigureIssuesForm.ParamNames.entrySingularName.name()%>"
                                       value="<%=h(bean.entryTypeNames.singularName)%>" size="20"/></td>
                        </tr>
                        <tr>
                            <td>Plural items name</td>
                            <td><input type="text" name="<%=IssuesController.ConfigureIssuesForm.ParamNames.entryPluralName.name()%>"
                                       value="<%=h(bean.entryTypeNames.pluralName)%>" size="20"/></td>
                        </tr>
                        <tr>
                            <td>Comment sort direction</td>
                            <td>
                                <%=PageFlowUtil.strSelect(IssuesController.ConfigureIssuesForm.ParamNames.direction.name(), Arrays.asList(Sort.SortDirection.values()), java.util.Arrays.asList("Oldest first", "Newest first"), bean.commentSort) %>
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
                                <input onchange="assignedToGroup.disabled=true;" type="radio" name="assignedToMethod" value="ProjectUsers"<%=null == bean.assignedToGroup ? " checked" : ""%> />
                            </td>
                            <td>All Project Users</td>
                        </tr>
                        <tr>
                            <td>
                                <input onchange="assignedToGroup.disabled=false;" type="radio" name="assignedToMethod" value="Group" <%=null != bean.assignedToGroup ? " checked" : ""%> />
                            </td>
                            <td>Specific Group
                                <select<%=Boolean.valueOf(null == bean.assignedToGroup) ? " disabled=\"true\"" : ""%> name="assignedToGroup"><%
                                    for (Group group : SecurityManager.getGroups(c.getProject(), true))
                                    {
                                        if (!group.isGuests())
                                        {
                                            String displayText = (group.isProjectGroup() ? "" : "Site:") + group.getName();
                                            out.println("<option value=\"" + group.getUserId() + "\"" + (null != bean.assignedToGroup && group.getUserId() == bean.assignedToGroup.getUserId() ? " selected" : "") + ">" + h(displayText) + "</option>");
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
                <td><%=generateSubmitButton("Update")%></td>
            </tr>
        </table>
    </td>
</tr>
</table>
</form>

<%!
    public boolean isRequired(String name, HString requiredFields) {
        if (requiredFields != null) {
            return requiredFields.indexOf(name.toLowerCase()) != -1;
        }
        return false;
    }

    public String getCaption(ColumnInfo col) throws java.sql.SQLException
    {
        final IssueManager.CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(HttpView.getRootContext().getContainer());
        if (ccc.getColumnCaptions().containsKey(col.getName()))
        {
            return ccc.getColumnCaptions().get(col.getName());
        }
        return col.getLabel();
    }

    public boolean hasKeywords(ColumnInfo col){
        //IssueManager.getKeywords(getContainer().getId(), ISSUE_AREA).length < 1
        Container c = HttpView.getRootContext().getContainer();
        if (col.getColumnName().equalsIgnoreCase(IssuesController.STRING_1_STRING) && IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_STRING1).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.STRING_2_STRING) && IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_STRING2).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.STRING_3_STRING)&& IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_STRING3).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.STRING_4_STRING)&& IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_STRING4).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.STRING_5_STRING)&& IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_STRING5).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.TYPE_STRING)&& IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_TYPE).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.AREA_STRING)&& IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_AREA).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.PRIORITY_STRING)&& IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_PRIORITY).length < 1)
        {
                return false;
        }
        else if (col.getColumnName().equalsIgnoreCase(IssuesController.MILESTONE_STRING)&& IssueManager.getKeywords(c.getId(), IssuesController.ISSUE_MILESTONE).length < 1)
        {
                return false;
        }

        return true;
    }

    public boolean isPickList(ColumnInfo col){
        final IssueManager.CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(HttpView.getRootContext().getContainer());
        String name = col.getColumnName();
        if (ccc.getPickListColumns().contains(col.getColumnName().toLowerCase()))
        {
            //If the column actually is a pick list return true.
            return true;
        }
        else if(col.getColumnName().equalsIgnoreCase(IssuesController.TYPE_STRING) || col.getColumnName().equalsIgnoreCase(IssuesController.AREA_STRING)
                || col.getColumnName().equalsIgnoreCase(IssuesController.PRIORITY_STRING) || col.getColumnName().equalsIgnoreCase(IssuesController.MILESTONE_STRING))
        {
            //If the column is Type, Area, Priority, or Milestone also return true
            // this way if they don't have keywords they are also greyed out.
            return true;
        }
        else
        {
            return false;
        }

    }
%>
<br>
<% me.include(bean.keywordView, out); %>
