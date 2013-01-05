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

<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.issue.ColumnType" %>
<%@ page import="org.labkey.issue.IssuePage" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.IssuesController.CloseAction" %>
<%@ page import="org.labkey.issue.IssuesController.EmailPrefsAction" %>
<%@ page import="org.labkey.issue.IssuesController.InsertAction" %>
<%@ page import="org.labkey.issue.IssuesController.ListAction" %>
<%@ page import="org.labkey.issue.IssuesController.ReopenAction" %>
<%@ page import="org.labkey.issue.IssuesController.ResolveAction" %>
<%@ page import="org.labkey.issue.IssuesController.UpdateAction" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    final Container c = context.getContainer();
    final User user = context.getUser();
    final String issueId = Integer.toString(issue.getIssueId());
    final boolean hasUpdatePerms = bean.getHasUpdatePermissions();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c);
%>
<% if (!bean.isPrint())
{
%>
<%--<script src="<%=contextPath%>/issues/hashbang.js" type="text/javascript"></script>--%>
<form name="jumpToIssue" action="jumpToIssue.view" method="get">
    <table><tr><%

    if (bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("new " + names.singularName.getSource().toLowerCase(), PageFlowUtil.getLastFilter(context, IssuesController.issueURL(context.getContainer(), InsertAction.class)))%></td><%
    }%>

    <td><%= textLink("return to grid", PageFlowUtil.getLastFilter(context, IssuesController.issueURL(context.getContainer(), ListAction.class)).deleteParameter("error"))%></td><%

    if (bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("update", IssuesController.issueURL(context.getContainer(), UpdateAction.class).addParameter("issueId", issueId))%></td><%
    }

    if (issue.getStatus().equals(Issue.statusOPEN) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("resolve", IssuesController.issueURL(context.getContainer(), ResolveAction.class).addParameter("issueId", issueId))%></td><%
    }
    else if (issue.getStatus().equals(Issue.statusRESOLVED) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("close", IssuesController.issueURL(context.getContainer(), CloseAction.class).addParameter("issueId", issueId))%></td>
        <td><%= textLink("reopen", IssuesController.issueURL(context.getContainer(), ReopenAction.class).addParameter("issueId", issueId))%></td><%
    }
    else if (issue.getStatus().equals(Issue.statusCLOSED) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("reopen", IssuesController.issueURL(context.getContainer(), ReopenAction.class).addParameter("issueId", issueId))%></td><%
    }
    %><td><%= textLink("print", context.cloneActionURL().replaceParameter("_print", "1"))%></td>
    <td><%= textLink("email prefs", IssuesController.issueURL(context.getContainer(), EmailPrefsAction.class).addParameter("issueId", issueId))%></td>
    <td>&nbsp;&nbsp;&nbsp;Jump to <%=h(names.singularName)%>: <input type="text" size="5" name="issueId"/></td>
    </tr></table>
</form><%
}
%>

<table>
    <tr>
        <td valign="top"><table>
            <tr><td class="labkey-form-label">Status</td><td><%=h(issue.getStatus())%></td></tr>
            <tr><td class="labkey-form-label">Assigned&nbsp;To</td><td><%=h(issue.getAssignedToName(user))%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.TYPE)%></td><td><%=h(issue.getType())%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.AREA)%></td><td><%=h(issue.getArea())%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.PRIORITY)%></td><td><%=h(issue.getPriority())%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.MILESTONE)%></td><td><%=h(issue.getMilestone())%></td></tr>
        </table></td>
        <td valign="top"><table>
            <tr><td class="labkey-form-label"><%=bean.getLabel("Opened")%></td><td nowrap="true"><%=bean.writeDate(issue.getCreated())%> by <%=h(issue.getCreatedByName(user))%></td></tr>
            <tr><td class="labkey-form-label">Changed</td><td nowrap="true"><%=bean.writeDate(issue.getModified())%> by <%=h(issue.getModifiedByName(user))%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel("Resolved")%></td><td nowrap="true"><%=bean.writeDate(issue.getResolved())%><%= issue.getResolvedBy() != null ? " by " : ""%> <%=h(issue.getResolvedByName(user))%></td></tr>
            <tr><td class="labkey-form-label"><%=bean.getLabel(ColumnType.RESOLUTION)%></td><td><%=h(issue.getResolution())%></td></tr><%
            if (bean.isEditable("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {
                %><tr><td class="labkey-form-label">Duplicate</td><td>
                <% if (bean.isEditable("duplicate")) { %>
                    <%=bean.writeInput("duplicate", String.valueOf(issue.getDuplicate()), 10)%>
                <% } else { %>
                    <a href="<%=IssuesController.getDetailsURL(context.getContainer(), issue.getDuplicate(), false)%>"><%=issue.getDuplicate()%></a>
                <% } %>
                </td></tr><%
            }
            if (!issue.getDuplicates().isEmpty())
            {
                %><tr><td class="labkey-form-label">Duplicates</td><td><%=bean.renderDuplicates(issue.getDuplicates())%></td></tr><%
            }
%>
            <%=bean.writeCustomColumn(ColumnType.INT1, 10)%>
            <%=bean.writeCustomColumn(ColumnType.INT2, 10)%>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Closed</td><td nowrap="true"><%=bean.writeDate(issue.getClosed())%><%= issue.getClosedBy() != null ? " by " : "" %><%=h(issue.getClosedByName(user))%></td></tr>

            <%
                if (hasUpdatePerms)
                {
                    %><tr><td class="labkey-form-label">Notify</td><td><%=bean.getNotifyList()%></td></tr><%
            }
            %><%=bean.writeCustomColumn(ColumnType.STRING1, 30)%>
            <%=bean.writeCustomColumn(ColumnType.STRING2, 30)%>
            <%=bean.writeCustomColumn(ColumnType.STRING3, 30)%>
            <%=bean.writeCustomColumn(ColumnType.STRING4, 30)%>
            <%=bean.writeCustomColumn(ColumnType.STRING5, 30)%>
        </table></td>
    </tr>
</table>
<%
    if (bean.getCallbackURL() != null)
    {
        %><input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/><%
    }

    for (Issue.Comment comment : issue.getComments())
    {
        %><hr><table width="100%"><tr><td align="left"><b>
        <%=bean.writeDate(comment.getCreated())%>
        </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(user))%>
        </b></td></tr></table>
        <%=comment.getComment()%>
        <%=bean.renderAttachments(context, comment)%><%
    }
%>