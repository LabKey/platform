<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.InsertPermission"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.getRootContext();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());

    if (request.getParameter("error") != null)
    {
%>
        <font class="labkey-error"><%=request.getParameter("error")%></font><br/>
<%
    }
%>

<table><tr>
    <%
        if (context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
        {
    %>
            <td><%=PageFlowUtil.generateButton("New " + names.singularName, "insert.view")%></td><td>&nbsp;</td>
    <%
        }
    %>
    <td nowrap><form name="jumpToIssue" action="jumpToIssue.view" method="get">
        <input type="text" size="5" name="issueId"/>
        <%=PageFlowUtil.generateSubmitButton("Jump to " + names.singularName, "", "align=\"top\" vspace=\"2\"")%></form></td>
    <td width=100%>&nbsp;</td>
    <td align="right" nowrap><form action="search.view" method="get">
        <input type="text" size="30" name="q" value="">
        <%=PageFlowUtil.generateSubmitButton("Search", "", "align=\"top\" vspace=\"2\"")%></form></td>
</tr></table>