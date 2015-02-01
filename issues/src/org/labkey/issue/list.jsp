<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.search.SearchUrls"%>
<%@ page import="org.labkey.api.security.permissions.InsertPermission"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c);

    if (request.getParameter("error") != null)
    {
        %><span class="labkey-error"><%=h(request.getParameter("error"))%></span><br/><%
    }
%>

<table><tr>
    <td nowrap><labkey:form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" method="get">
    <%
        if (c.hasPermission(getUser(), InsertPermission.class))
        {
    %>
            <%= button("New " + names.singularName.getSource()).href(new ActionURL(IssuesController.InsertAction.class, c)) %>&nbsp;&nbsp;&nbsp;
    <%
        }
    %><input type="text" size="5" name="issueId"/>
        <%= button("Jump to " + names.singularName.getSource()).submit(true).attributes("align=\"top\" vspace=\"2\"") %></labkey:form></td>
    <td width=100%>&nbsp;</td>
    <td align="right" nowrap>
        <labkey:form action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>" method="get">
            <input type="text" size="30" name="q" value="">
            <input type="hidden" name="template" value="<%=h(IssuesController.IssueSearchResultTemplate.NAME)%>">
            <%= button("Search").submit(true).attributes("align=\"top\" vspace=\"2\"")%>
        </labkey:form>
    </td>
</tr></table>