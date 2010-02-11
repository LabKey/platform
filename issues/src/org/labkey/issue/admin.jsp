<%
/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.security.Group" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
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
<tr><td><%=PageFlowUtil.generateButton("Back to " + bean.entryTypeNames.pluralName, "list.view?.lastFilter=true")%></td></tr>
<tr><td>&nbsp;</td></tr>
</table>
<% me.include(bean.keywordView, out); %>
<br>

<table><tr>
    <td valign=top>
        <% me.include(bean.requiredFieldsView, out); %>
    </td>
    <td valign=top>
        <form action="setCustomColumnConfiguration.post" method="post">
        <table>
        <tr><td colspan=2 align=center><div class="labkey-form-label"><b>Custom Fields</b></div></td></tr>
        <tr><td colspan=2>Enter captions below to use custom fields in this <%=bean.entryTypeNames.pluralName%> list:</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td>Integer1</td><td><input name="int1" value="<%=h(captions.get("int1"))%>" size=20></td></tr>
        <tr><td>Integer2</td><td><input name="int2" value="<%=h(captions.get("int2"))%>" size=20></td></tr>
        <tr><td>String1</td><td><input name="string1" value="<%=h(captions.get("string1"))%>" size=20><input type="checkbox" name="<%=IssueManager.CustomColumnConfiguration.PICK_LIST_NAME%>" value="string1" <%=pickListColumns.contains("string1") ? "checked" : ""%>>Use pick list for this field</td></tr>
        <tr><td>String2</td><td><input name="string2" value="<%=h(captions.get("string2"))%>" size=20><input type="checkbox" name="<%=IssueManager.CustomColumnConfiguration.PICK_LIST_NAME%>" value="string2" <%=pickListColumns.contains("string2") ? "checked" : ""%>>Use pick list for this field</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td colspan=2 align="center"><%=PageFlowUtil.generateSubmitButton("Update Custom Fields")%></td></tr>
        </table>
        </form>
    </td>
</tr>
<tr>
    <td valign="top">
        <form name="entryTypeNames" action="setEntryTypeNames.post" method="POST">
        <table width="100%">
            <tr><td align="center"><div class="labkey-form-label"><b>Entry Type Names</b></div></td></tr>
            <tr>
                <td>
                    <table>
                        <tr>
                            <td>Singular</td>
                            <td><input type="text" name="<%=IssuesController.EntryTypeNamesForm.ParamNames.entrySingularName.name()%>"
                                       value="<%=h(bean.entryTypeNames.singularName)%>" size="20"/></td>
                        </tr>
                        <tr>
                            <td>Plural</td>
                            <td><input type="text" name="<%=IssuesController.EntryTypeNamesForm.ParamNames.entryPluralName.name()%>"
                                       value="<%=h(bean.entryTypeNames.pluralName)%>" size="20"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td align="center"><%=PageFlowUtil.generateSubmitButton("Update Entry Type Names")%></td>
            </tr>
        </table>
        </form>
    </td>
    <td valign="top">
        <form name="assignedToList" action="setAssignedToGroup.post" method="POST">
        <table width="100%">
            <tr><td align="center"><div class="labkey-form-label"><b>Assigned To List</b></div></td></tr>
            <tr>
                <td>
                    <table>
                        <tr><td colspan="2">Populate the assigned to list from:</td></tr>
                        <tr><td colspan="2">&nbsp;</td></tr>
                        <tr>
                            <td>
                                <input onchange="assignedToGroup.disabled=true;" type="radio" name="assignedToMethod" value="ProjectMembers"<%=null == bean.assignedToGroup ? " checked" : ""%> />
                            </td>
                            <td>All Project Members</td>
                        </tr>
                        <tr>
                            <td>
                                <input onchange="assignedToGroup.disabled=false;" type="radio" name="assignedToMethod" value="Group" <%=null != bean.assignedToGroup ? " checked" : ""%> />
                            </td>
                            <td>Specific Group
                                <select<%=Boolean.valueOf(null == bean.assignedToGroup) ? " disabled=\"true\"" : ""%> name="assignedToGroup"><%
                                    for (Group group : SecurityManager.getGroups(c.getProject(), false))
                                    {
                                        out.println("<option value=\"" + group.getUserId() + "\"" + (null != bean.assignedToGroup && group.getUserId() == bean.assignedToGroup.getUserId() ? " selected" : "") + ">" + h(group.getName()) + "</option>");
                                    }
                                %>
                                </select>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td align="center"><%=PageFlowUtil.generateSubmitButton("Update Assigned To List")%></td>
            </tr>
        </table>
        </form>
    </td>
</tr>
</table>
