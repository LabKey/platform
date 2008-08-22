<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.query.CustomView"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<IssuesController.ListForm> me = (JspView<IssuesController.ListForm>) HttpView.currentView();
    IssuesController.ListForm bean = me.getModelBean();
    ViewContext context = HttpView.getRootContext();

    String viewName = me.getModelBean().getQuerySettings().getViewName();
    boolean isHidden = bean.getViews().get(viewName) != null ? bean.getViews().get(viewName).isHidden() : false;

    final String OPEN_FILTER = "Issues.Status~eq=open&Issues.sort=Milestone%2CAssignedTo/DisplayName";
    final String RESOLVED_FILTER = "Issues.Status~eq=resolved&Issues.sort=Milestone%2CAssignedTo/DisplayName";

    Map<String, String> views = new LinkedHashMap();
    views.put("all", "value=\"list.view\" " + isFilterSelected("", context));
    views.put("open", "value=\"?" + OPEN_FILTER + "\" " + isFilterSelected(OPEN_FILTER, context));
    views.put("resolved", "value=\"?" + RESOLVED_FILTER + "\" " + isFilterSelected(RESOLVED_FILTER, context));
    if (!context.getUser().isGuest())
    {
        String mineFilter ="Issues.AssignedTo/DisplayName~eq=" +  h(context.getUser().getDisplayName(context)) + "&Issues.Status~neqornull=closed&Issues.sort=-Milestone";
        views.put("mine", "value=\"?" + h(mineFilter) + "\" " + isFilterSelected(mineFilter, context));
    }
    for (CustomView cv : bean.getViews().values())
    {
        String customViewName = cv.getName() != null ? cv.getName() : "";
        views.put(cv.getName(), "value=\"?Issues.viewName=" + h(PageFlowUtil.encode(customViewName)) + "\" " + (customViewName.equals(viewName) ? "selected" : ""));
    }
    for (Map.Entry<String, String> entry : bean.getReports().entrySet())
    {
        views.put(entry.getValue(), "value=\"?Issues.viewName=" + h(PageFlowUtil.encode(entry.getKey())) + "\" " + (entry.getKey().equals(viewName) ? "selected" : ""));
    }
%>

<%
    if (request.getParameter("error") != null)
    {
%>
        <font class="labkey-error"><%=request.getParameter("error")%></font><br/>
<%
    }
%>

<table><tr>
    <td>&nbsp;</td>
    <%
    if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_INSERT))
	{
	%><td><%=PageFlowUtil.generateButton("New Issue", "insert.view")%></td><%
	}
    %>
<%--
    <td>&nbsp;views:</td>
    <td><select onchange="document.location.href=this.options[this.selectedIndex].value">
        <option value="#"></option>
    <%
        for (Map.Entry<String, String> entry : views.entrySet())
        {
    %>
        <option <%=entry.getValue()%>><%=entry.getKey()%></option>
    <%
        }
    %>
    </select></td>
    <%
    if (!bean.getReports().containsKey(viewName) && ((!isHidden && !context.getUser().isGuest()) || (context.hasPermission(ACL.PERM_ADMIN))))
    {
    %>
    <td>[<a href="<%=bean.getCustomizeURL()%>">customize&nbsp;view</a>]</td>
    <%
    }
    %>
--%>
    <td width=100%>&nbsp;</td>
    <td nowrap><form name="jumpToIssue" action="jumpToIssue.view" method="get">Jump&nbsp;to&nbsp;issue:<input type="text" size="5" name="issueId"/></form></td>
    <td align="right" nowrap><form action="search.view" method="get">
        <%=PageFlowUtil.generateSubmitButton("Search", "", "align=\"top\" vspace=\"2\"")%>&nbsp;&nbsp;<input type="text" size="30" name="search" value="">&nbsp;&nbsp;&nbsp;</form></td>
</tr></table>

<%!
    String isFilterSelected(String filter, ViewContext context) {
        String qs = context.getActionURL().getQueryString();
        if (StringUtils.isEmpty(filter) && StringUtils.isEmpty(qs))
            return "selected";

        if (!StringUtils.isEmpty(qs))
            qs = PageFlowUtil.decode(qs);

        if (PageFlowUtil.decode(filter).equals(qs))
            return "selected";

        return "";
    }
%>