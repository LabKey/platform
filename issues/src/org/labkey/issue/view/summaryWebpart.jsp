<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.IssuesController.ListAction" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssueManager.EntryTypeNames" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    IssuesController.SummaryBean bean = ((JspView<IssuesController.SummaryBean>) HttpView.currentView()).getModelBean();
    User user = getUser();
    EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer(), bean.issueDefName);

    if (bean.hasPermission)
    {
        if (bean.bugs.isEmpty())
        {%>
            <div style="margin-bottom: 8px">There are no issues in this list.</div>
            <%= button("New " + names.singularName.toLowerCase()).href(bean.insertURL) %>
        <%}
        else
        {
%>
<table class="table-condensed table-striped table-bordered">
    <tr><td>User</td><td>Open</td><td>Resolved</td>
    <% for (Map<String, Object> bug : bean.bugs) { %>
    <tr>
        <td>
      <% if (null != bug.get("displayName")) {
          ActionURL url = getBaseListURL(bean.issueDefName).addParameter("issues-" + bean.issueDefName + ".AssignedTo/DisplayName~eq", bug.get("displayName").toString());%>
        <a href="<%=h(url)%>"><%=h(bug.get("displayName"))%></a>
      <% } else {
          ActionURL url = getBaseListURL(bean.issueDefName).addParameter("issues-" + bean.issueDefName + ".AssignedTo/DisplayName~isblank", null);%>
         <a href="<%=h(url)%>"><i>Unassigned</i></a>
      <% } %>
        </td>
        <td align="right"><%=bug.get("open")%></td>
        <td align="right"><%=bug.get("resolved")%></td>
    </tr>
<% } %>
</table>
<div class="labkey-button-bar-separate">
<%= button("view open " + names.pluralName.toLowerCase()).href(getBaseListURL(bean.issueDefName).addParameter("issues-" + bean.issueDefName + ".Status~eq", "open")) %>
<%= button("new " + names.singularName.toLowerCase()).href(bean.insertURL) %>
</div>
<%
        }
    }
    else
    {
%>
<span>
  <% if (user.isGuest()) { %>
     Please log in to see this data.
  <% } else { %>
     You do not have permission to see this data.
  <% } %>
</span>
<% } %>
<%!
    public ActionURL getBaseListURL(String issueDefName)
    {
        return new ActionURL(ListAction.class, getContainer()).
            addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName);
    }
%>

