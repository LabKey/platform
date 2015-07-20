<%
/*
 * Copyright (c) 2005-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    IssuesController.SummaryBean bean = ((JspView<IssuesController.SummaryBean>) HttpView.currentView()).getModelBean();
    User user = getUser();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());

    if (bean.hasPermission)
    {
%>
<table class="labkey-data-region">
    <tr><td>User</td><td>Open</td><td>Resolved</td>
<%
        for (Map bug : bean.bugs)
        {
%>
    <tr>
      <td>
      <% if (null != bug.get("displayName")) { %>
         <a href="<%=h(bean.listURL)%>Issues.AssignedTo/DisplayName~eq=<%=bug.get("displayName")%>"><%=bug.get("displayName")%></a>
      <% } else { %>
         <a href="<%=h(bean.listURL)%>Issues.AssignedTo/DisplayName~isblank"><i>Unassigned</i></a>
      <% } %>
      </td>
    <td align="right"><%=bug.get("open")%></td>
    <td align="right"><%=bug.get("resolved")%></td>
    </tr>
<% } %>
</table>
<%=textLink("view open " + names.pluralName.getSource(), bean.listURL + "Issues.Status~eq=open")%>
<%=textLink("submit new " + names.singularName.getSource(), bean.insertURL)%>
<%
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
