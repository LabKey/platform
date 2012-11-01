<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@page import="org.labkey.api.security.permissions.InsertPermission"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.api.search.SearchUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.getRootContext();
    String contextPath = context.getContextPath();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());

    if (request.getParameter("error") != null)
    {
        %><span class="labkey-error"><%=h(request.getParameter("error"))%></span><br/><%
    }
%>

<table><tr>
    <td nowrap><form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, context.getContainer()) %>" method="get">
    <%
        if (context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
        {
    %>
            <%=generateButton("New " + names.singularName.getSource(), new ActionURL(IssuesController.InsertAction.class, context.getContainer()))%>&nbsp;&nbsp;&nbsp;
    <%
        }
    %><input type="text" size="5" name="issueId"/>
        <%=PageFlowUtil.generateSubmitButton("Jump to " + names.singularName.getSource(), "", "align=\"top\" vspace=\"2\"")%></form></td>
    <td width=100%>&nbsp;</td>
    <td align="right" nowrap>
        <form action="<%=h(urlProvider(SearchUrls.class).getSearchURL(context.getContainer(), null))%>" method="get">
            <input type="text" size="30" name="q" value="">
            <input type="hidden" name="template" value="<%=h(IssuesController.IssueSearchResultTemplate.NAME)%>">
            <%=PageFlowUtil.generateSubmitButton("Search", "", "align=\"top\" vspace=\"2\"")%>
        </form>
    </td>
</tr></table>

<%
if ("true".equals(context.getActionURL().getParameter("navigateInPlace")))
{
%><script src="<%=h(contextPath)%>/issues/hashbang.js"></script>
<script>
if (!Ext.isDefined(window.navigationStrategy))
{
    function cacheablePage(hash, el)
    {
        if (-1 == hash.indexOf("_action=list"))
            return false;
        if (!el)
            return true;
        var errors = Ext.DomQuery.jsSelect(".labkey-error", el.dom);
        var hasErrors = errors && errors.length > 0;
        return !hasErrors;
    }

    window.navigationStrategy = new LABKEY.NavigateInPlaceStrategy(
    {
        controller : "issues",
        actions :
        {
            "details": true,
            "list" : true,
            "insert" : true,
            "update" : true,
            "admin" : true,
            "emailPrefs" : true,
            "resolve" : true
        }
        , cacheable : cacheablePage
    });
}
</script><%
}
%>