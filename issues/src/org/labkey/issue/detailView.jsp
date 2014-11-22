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

<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.permissions.InsertPermission"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.wiki.WikiService" %>
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
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.regex.Matcher" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        resources.add(ClientDependency.fromFilePath("issues/detail.js"));
        return resources;
    }
%>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    ViewContext context = getViewContext();
    final Container c = getContainer();
    final User user = getUser();
    final String issueId = Integer.toString(issue.getIssueId());
    final boolean hasUpdatePerms = bean.getHasUpdatePermissions();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c);

    // Create Issue from a Ticket
    Container relatedIssueContainer = IssueManager.getRelatedIssuesList(c);
    ActionURL insertURL = relatedIssueContainer == null ? null : new ActionURL(InsertAction.class, relatedIssueContainer);
    boolean showRelatedIssuesButton = relatedIssueContainer == null ? false : relatedIssueContainer.hasPermission(user, InsertPermission.class);

    List<Issue.Comment> commentLinkedList = IssueManager.getCommentsForRelatedIssues(issue, user);
%>
<% if (!bean.isPrint())
{
%>

<script type="text/javascript">
    var hidden = true;

    /**
     * Create a Related Issue - prompt with a warning before creating the issue
     * if one is not careful one might post sensitive data from a private list to a public one
     */
    function createRelatedIssue() {
        var response = window.confirm("Warning:  When creating a related issue in a public list, one may potentially expose private data.  Are you sure that you wish to continue?");
        if (response == true) {
            document.forms.CreateIssue.submit();
        }
    }

    /**
    * Toggle the hidden flag, set the hide button text to reflect state, and show or hide all related comments.
     */
    function toggleComments() {
        // change the button text
        var button = document.getElementById('showRelatedComments');
        if (!hidden)
            button.text = 'Show Related Comments';
        else
            button.text = 'Hide Related Comments';

        // show/hide comment elements
        var commentDivs = document.getElementsByClassName('relatedIssue');
        for (var i = 0; i < commentDivs.length; i++) {
            if (hidden)
                commentDivs[i].style.display = 'inline';
            else
                commentDivs[i].style.display = 'none';
        }
        hidden = !hidden;
    }
</script>

<%--<script src="<%=contextPath%>/issues/hashbang.js" type="text/javascript"></script>--%>
<labkey:form name="jumpToIssue" action="<%=h(buildURL(IssuesController.JumpToIssueAction.class))%>" method="get">
    <table><tr><%

    if (bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("new " + names.singularName.getSource().toLowerCase(), PageFlowUtil.getLastFilter(context, IssuesController.issueURL(c, InsertAction.class)))%></td><%
    }%>

    <td><%= textLink("return to grid", PageFlowUtil.getLastFilter(context, IssuesController.issueURL(c, ListAction.class)).deleteParameter("error"))%></td><%

    if (bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("update", IssuesController.issueURL(c, UpdateAction.class).addParameter("issueId", issueId))%></td><%
    }

    if (issue.getStatus().equals(Issue.statusOPEN) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("resolve", IssuesController.issueURL(c, ResolveAction.class).addParameter("issueId", issueId))%></td><%
    }
    else if (issue.getStatus().equals(Issue.statusRESOLVED) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("close", IssuesController.issueURL(c, CloseAction.class).addParameter("issueId", issueId))%></td>
        <td><%= textLink("reopen", IssuesController.issueURL(c, ReopenAction.class).addParameter("issueId", issueId))%></td><%
    }
    else if (issue.getStatus().equals(Issue.statusCLOSED) && bean.getHasUpdatePermissions())
    {
        %><td><%= textLink("reopen", IssuesController.issueURL(c, ReopenAction.class).addParameter("issueId", issueId))%></td><%
    }

    if (bean.getHasAdminPermissions() && bean.hasMoveDestinations())
    {
        %><td><%= textLink("move", "javascript:void(0)", "createMoveIssueWindow([" + issueId + "])", "")%></td><%
    }
    %><td><%= textLink("print", context.cloneActionURL().replaceParameter("_print", "1"))%></td>
    <td><%= textLink("email prefs", IssuesController.issueURL(c, EmailPrefsAction.class).addParameter("issueId", issueId))%></td>
    <%
    if (showRelatedIssuesButton)
    {
        %><td><%= textLink("create related issue", "javascript:createRelatedIssue()") %></td><%
    }
    if ( IssueManager.hasRelatedIssues(issue, user))
    {
        %><td><%= PageFlowUtil.textLink("show related comments", "javascript:toggleComments()", "", "showRelatedComments") %></td><%
    }
    %>
    <td>&nbsp;&nbsp;&nbsp;Jump to <%=h(names.singularName)%>: <input type="text" size="5" name="issueId"/></td>
    </tr></table>
</labkey:form><%
}
%>

<table>
    <tr>
        <td valign="top"><table>
            <tr><td class="labkey-form-label">Status</td><td><%=h(issue.getStatus())%></td></tr>
            <tr><td class="labkey-form-label">Assigned&nbsp;To</td><td><%=h(issue.getAssignedToName(user))%></td></tr>
<%
    for (ColumnType type : Arrays.asList(ColumnType.TYPE, ColumnType.AREA, ColumnType.PRIORITY, ColumnType.MILESTONE))
    {
        if (bean.hasKeywords(type) || type.getValue(issue) != null)
        {
            %><tr><td class="labkey-form-label"><%=text(bean.getLabel(type, false))%></td><td><%=h(type.getValue(issue))%></td></tr><%
        }
    }
%>
        </table></td>
        <td valign="top"><table>
            <tr><td class="labkey-form-label"><%=text(bean.getLabel("Opened", false))%></td><td nowrap="true"><%=bean.writeDate(issue.getCreated())%> by <%=h(issue.getCreatedByName(user))%></td></tr>
            <tr><td class="labkey-form-label">Changed</td><td nowrap="true"><%=h(bean.writeDate(issue.getModified()))%> by <%=h(issue.getModifiedByName(user))%></td></tr>
            <tr><td class="labkey-form-label"><%=text(bean.getLabel("Resolved", false))%></td><td nowrap="true"><%=h(bean.writeDate(issue.getResolved()))%><%=text(issue.getResolvedBy() != null ? " by " : "")%> <%=h(issue.getResolvedByName(user))%></td></tr>
            <tr><td class="labkey-form-label"><%=text(bean.getLabel(ColumnType.RESOLUTION, false))%></td><td><%=h(issue.getResolution())%></td></tr><%
            if (bean.isEditable("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {
                %><tr><td class="labkey-form-label">Duplicate</td><td>
                <% if (bean.isEditable("duplicate")) { %>
                    <%=text(bean.writeInput("duplicate", String.valueOf(issue.getDuplicate()), 10))%>
                <% } else { %>
                    <%=bean.renderDuplicate(issue.getDuplicate())%>
                <% } %>
                </td></tr><%
            }
            if (!issue.getDuplicates().isEmpty())
            {
                %><tr><td class="labkey-form-label">Duplicates</td><td><%=bean.renderDuplicates(issue.getDuplicates())%></td></tr><%
            }
            if (!issue.getRelatedIssues().isEmpty())
            {
                %><tr><td class="labkey-form-label"><%=text(bean.getLabel(ColumnType.RELATED, false))%></td><td><%=bean.renderRelatedIssues(issue.getRelatedIssues())%></td></tr><%
            }
%>
            <%=bean.writeCustomColumn(ColumnType.INT1, 10, false)%>
            <%=bean.writeCustomColumn(ColumnType.INT2, 10, false)%>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Closed</td><td nowrap="true"><%=h(bean.writeDate(issue.getClosed()))%><%= issue.getClosedBy() != null ? " by " : "" %><%=h(issue.getClosedByName(user))%></td></tr>

            <%
                if (hasUpdatePerms)
                {
                    %><tr><td class="labkey-form-label">Notify</td><td><%=bean.getNotifyList()%></td></tr><%
            }
            %><%=bean.writeCustomColumn(ColumnType.STRING1, 30, false)%>
            <%=bean.writeCustomColumn(ColumnType.STRING2, 30, false)%>
            <%=bean.writeCustomColumn(ColumnType.STRING3, 30, false)%>
            <%=bean.writeCustomColumn(ColumnType.STRING4, 30, false)%>
            <%=bean.writeCustomColumn(ColumnType.STRING5, 30, false)%>
        </table></td>
    </tr>
</table>
<%
    if (bean.getCallbackURL() != null)
    {
        %><input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/><%
    }

    StringBuilder commentText = new StringBuilder();
    boolean hasAttachments = false;
    for (Issue.Comment comment : commentLinkedList)
    {
        if (!issue.getComments().contains(comment))
        {
            %><div class="relatedIssue" style="display: none;"><%
        }
        else
        {
            %><div class="currentIssue" style="display: inline;"><%
        }
        %><hr><table width="100%"><tr><td align="left"><b>
        <%=h(bean.writeDate(comment.getCreated()))%>
        </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(user))%>
        </b></td></tr></table>
        <%
        if (!issue.getComments().contains(comment))
        {
            %><div style="color:blue;font-weight:bold;">Related # <%=comment.getIssue().getIssueId()%> </div><%
        }
        %>
        <%=comment.getComment()%>
        <%=bean.renderAttachments(context, comment)%>
        </div><%

        // Determine if the comment has attachments
        hasAttachments = hasAttachments ? true : !bean.renderAttachments(context, comment).isEmpty();

        // Extract the string value from the last comment entry
        Pattern pattern = Pattern.compile("(?s)(" + WikiService.WIKI_PREFIX + ")(.*?)(" + WikiService.WIKI_SUFFIX + ")");
        Matcher matcher = pattern.matcher(comment.getComment());
        if (matcher.find() && !matcher.group(2).isEmpty()) // add the contexts if we find it
        {
            commentText.append(matcher.group(2));
            commentText.append("\n\n");
        }
    }
    if (hasAttachments)
        commentText.append("** The related issue has attachments.");
    String commentTextStr = commentText.toString().replaceAll("<br>", "");
%>

<labkey:form method="POST" id="CreateIssue" action="<%=insertURL%>">
    <input type="hidden" name="callbackURL" value="<%=h(bean.getCallbackURL())%>"/>
    <input type="hidden" name="body" value="<%=commentTextStr%>"/>
    <input type="hidden" name="title" value="<%=h(issue.getTitle())%>"/>
    <input type="hidden" name="skipPost" value="true"/>
    <input type="hidden" name="assignedTo" value="<%=issue.getAssignedTo()%>"/>
    <input type="hidden" name="priority" value="<%=issue.getPriority()%>"/>
    <input type="hidden" name="related" value="<%=issue.getIssueId()%>"/>
</labkey:form>
