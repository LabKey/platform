<%
    /*
     * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    String issueListDef = (String)getModelBean();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, issueListDef);

    if (request.getParameter("error") != null)
    {
%>
<span class="labkey-error"><%=h(request.getParameter("error"))%></span><br/>
<%  } %>
<%--<% if (false) { %>--%>
<% if (PageFlowUtil.useExperimentalCoreUI()) { %>
<div class="row" style="margin-bottom: 15px;">
    <div class="col-sm-7">
        <% if (c.hasPermission(getUser(), InsertPermission.class)) { %>
        <a class="btn btn-primary" href="<%=new ActionURL(IssuesController.InsertAction.class, c).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueListDef).toString()%>">
            <%="New " + names.singularName%>
        </a>
        <% } %>
    </div>
    <div class="col-sm-5">
        <labkey:form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" layout="inline">
            <labkey:input name="issueId" placeholder="ID # or Search Term"/>
            <%= button("Search").iconCls("search").submit(true) %>
        </labkey:form>
    </div>
</div>
<% } else { %>
<%--OLD UI--%>

<table>
    <tr>
    <td nowrap><labkey:form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" layout="horizontal">
        <%
        if (c.hasPermission(getUser(), InsertPermission.class))
        {%>
        <%= button("New " + names.singularName).href(new ActionURL(IssuesController.InsertAction.class, c).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueListDef)) %>&nbsp;&nbsp;&nbsp;<%
        }%>
        <input type="text" size="5" name="issueId"/>
        <%= button("Jump to " + names.singularName).submit(true).attributes("align=\"top\" vspace=\"2\"") %></labkey:form></td>
    <td width=100%>&nbsp;</td>
    <td align="right" nowrap>
        <labkey:form action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>">
            <input type="text" size="30" name="q" value="">
            <input type="hidden" name="template" value="<%=h(IssuesController.IssueSearchResultTemplate.NAME)%>">
            <%= button("Search").submit(true).attributes("align=\"top\" vspace=\"2\"")%>
        </labkey:form>
    </td>
    </tr>
</table>
<% } %>