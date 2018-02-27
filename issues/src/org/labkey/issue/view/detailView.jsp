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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.exp.property.DomainProperty"%>
<%@ page import="org.labkey.api.issues.IssueDetailHeaderLinkProvider" %>
<%@ page import="org.labkey.api.issues.IssuesListDefService" %>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.IssuesController.EmailPrefsAction" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueListDef" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssuePage" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ page import="org.labkey.issue.view.RelatedIssuesView" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("issues/move.js");
        dependencies.add("issues/createRelated.js");
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
    final boolean hasInsertPerms = c.hasPermission(getUser(), InsertPermission.class);

    List<Issue.Comment> commentLinkedList = IssueManager.getCommentsForRelatedIssues(issue, user);
    IssueListDef issueDef = IssueManager.getIssueListDef(issue);
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, issueDef.getName());

    List<DomainProperty> column1Props = new ArrayList<>();
    List<DomainProperty> column2Props = new ArrayList<>();
    List<DomainProperty> extraColumns = new ArrayList<>();

    Map<String, DomainProperty> propertyMap = bean.getCustomColumnConfiguration().getPropertyMap();
    // create collections for additional custom columns and distribute them evenly in the form
    // assigned to, type, area, priority, milestone
    int i=0;

    // todo: don't include if the lookup is empty (was previously IssuePage.hasKeywords)
    extraColumns.addAll(Stream.of("assignedto", "type", "area", "priority", "milestone")
        .filter(propertyMap::containsKey)
        .map(propertyMap::get)
        .collect(Collectors.toList()));

    for (DomainProperty prop : bean.getCustomColumnConfiguration().getCustomProperties())
    {
        if ((i++ % 2) == 0)
            column1Props.add(prop);
        else
            column2Props.add(prop);
    }

    int commentCount = issue.getComments().size();
    boolean hasAttachments = false;

    // Determine if the comment has attachments
    for (Issue.Comment comment : commentLinkedList)
    {
        // Determine if the comment has attachments
        hasAttachments = hasAttachments || !bean.renderAttachments(context, comment).isEmpty();
    }

    String commentTextStr="The related issue has " + commentCount;
    if (commentCount==1)
        commentTextStr += " comment";
    else
        commentTextStr += " comments";
    if (hasAttachments)
        commentTextStr += " and includes attachments."; // no nice way to count these as of now
    else
        commentTextStr +=".";

    StringBuilder relatedIssues = new StringBuilder("javascript:createRelatedIssue(");
    relatedIssues.append(q(issueDef.getName())).append(",");

    relatedIssues.append("{");
    relatedIssues.append("callbackURL : ").append(bean.getCallbackURL() == null ? null : q(bean.getCallbackURL()));
    relatedIssues.append(", body :").append(q(commentTextStr));
    relatedIssues.append(", title :").append(q(issue.getTitle()));
    relatedIssues.append(", skipPost :").append(true);
    relatedIssues.append(", assignedTo :").append(issue.getAssignedTo());
    relatedIssues.append(", priority :").append(issue.getPriority());
    relatedIssues.append(", related :").append(issue.getIssueId());
    relatedIssues.append("})");

    List<NavTree> additionalHeaderLinks = new ArrayList<>();
    for (IssueDetailHeaderLinkProvider provider : IssuesListDefService.get().getIssueDetailHeaderLinkProviders())
    {
        IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), issue.getIssueDefId());
        if (issueListDef != null)
        {
            boolean issueIsOpen = Issue.statusOPEN.equals(issue.getStatus());
            additionalHeaderLinks.addAll(provider.getLinks(issueListDef.getDomain(getUser()), issue.getIssueId(), issueIsOpen, issue.getProperties(), getContainer(), getUser()));
        }
    }

    if (!bean.isPrint())
    {
%>
<script type="text/javascript">
    var hidden = true;

    /**
     * Create a Related Issue - prompt with a warning before creating the issue
     * if one is not careful one might post sensitive data from a private list to a public one
     */
    function createRelatedIssue(issueDefName, params) {
        Ext4.create('Issues.window.CreateRelatedIssue', {
            issueDefName : issueDefName,
            params : params
        }).show();
    }

    function moveIssue() {
        Issues.window.MoveIssue.create(<%=PageFlowUtil.jsString(issueId)%>, <%=PageFlowUtil.jsString(issueDef.getName())%>);
    }

    /**
     * Toggle the hidden flag, set the hide button text to reflect state, and show or hide all related comments.
     */
    function toggleComments(button) {
        button.text = (hidden ? 'Hide' : 'Show') + ' related comments';

        // show/hide comment elements
        var commentDivs = document.getElementsByClassName('relatedIssue');
        for (var i = 0; i < commentDivs.length; i++) {
            commentDivs[i].style.display = hidden ? 'inline' : 'none';
        }
        hidden = !hidden;
    }
</script>
<div style="display:inline-block;margin-bottom:10px;">
    <div style="float:left;">
        <% if (bean.getHasUpdatePermissions()) { %>
        <%= button("Update").href(IssuesController.issueURL(c, IssuesController.UpdateAction.class).addParameter("issueId", issueId)) %>
        <% }
            if (issue.getStatus().equals(Issue.statusOPEN) && bean.getHasUpdatePermissions()) { %>
        <%= button("Resolve").href(IssuesController.issueURL(c, IssuesController.ResolveAction.class).addParameter("issueId", issueId)) %>
        <% }
        else if (issue.getStatus().equals(Issue.statusRESOLVED) && bean.getHasUpdatePermissions()) { %>
        <%= button("Close").href(IssuesController.issueURL(c, IssuesController.CloseAction.class).addParameter("issueId", issueId)) %>
        <%= button("Reopen").href(IssuesController.issueURL(c, IssuesController.ReopenAction.class).addParameter("issueId", issueId)) %>
        <% }
        else if (issue.getStatus().equals(Issue.statusCLOSED) && bean.getHasUpdatePermissions()) { %>
        <%= button("Reopen").href(IssuesController.issueURL(c, IssuesController.ReopenAction.class).addParameter("issueId", issueId)) %>
        <% } %>
    </div>
    <div style="float:left;margin-left:3px;" class="dropdown">
        <button data-toggle="dropdown" class="btn btn-default">More <i class="fa fa-caret-down"></i></button>
        <ul class="dropdown-menu dropdown-menu-left">
            <% NavTree navTree = new NavTree();
                if (bean.getHasUpdatePermissions() && hasInsertPerms)
                {
                    navTree.addChild("New " + names.singularName.toLowerCase(), PageFlowUtil.getLastFilter(context, IssuesController.issueURL(c, IssuesController.InsertAction.class).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDef.getName())));
                }
                if (!getUser().isGuest())
                {
                    navTree.addChild("Create related " + names.singularName.toLowerCase(), relatedIssues.toString());
                    navTree.addChild("Email preferences", IssuesController.issueURL(c, EmailPrefsAction.class).addParameter("issueId", issueId));
                }
                if ( IssueManager.hasRelatedIssues(issue, user))
                {
                    NavTree child = new NavTree("Show related comments", "javascript:void(0);");
                    child.setScript("javascript:toggleComments(this)");
                    navTree.addChild(child);
                }
                if (bean.getHasAdminPermissions() && bean.hasMoveDestinations())
                    navTree.addChild("Move", "javascript:moveIssue()");
                navTree.addChild("Print", context.cloneActionURL().replaceParameter("_print", "1"));
                navTree.addChildren(additionalHeaderLinks);
                PopupMenuView.renderTree(navTree, out); %>
        </ul>
    </div>
    <div style="float:left;margin-left:20px;">
        <labkey:form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" layout="inline">
            <div class="input-group">
                <labkey:input name="issueId" formGroup="false" placeholder="ID # or Search Term"/>
                <div class="input-group-btn">
                    <%= button("Search").addClass("btn btn-default").iconCls("search").submit(true) %>
                </div>
            </div>
        </labkey:form>
    </div>
</div>
<% } else { %>
<div class="labkey-nav-page-header-container"><span class="labkey-nav-page-header"><%=h(names.singularName + " " + issue.getIssueId() + ": " +issue.getTitle())%></span><p></div>
<% } %>
<labkey:panel type="portal">
<table class="issue-fields" style="width:75%; max-width: 90vw">
    <tr>
        <td valign="top"><table class="lk-fields-table">
            <tr><%=text(bean.renderLabel(bean.getLabel("Status", false)))%><td><%=h(issue.getStatus())%></td></tr><%
            for (DomainProperty prop : extraColumns)
            {%>
                <%=text(bean.renderColumn(prop, getViewContext()))%><%
            }%>

            <%=text(bean.renderAdditionalDetailInfo())%>
        </table></td>
        <td valign="top"><table class="lk-fields-table">
            <tr><%=text(bean.renderLabel(bean.getLabel("Opened", false)))%><td nowrap="true"><%=h(bean.writeDate(issue.getCreated()))%> by <%=h(issue.getCreatedByName(user))%></td></tr>
            <tr><%=text(bean.renderLabel(bean.getLabel("Changed", false)))%><td nowrap="true"><%=h(bean.writeDate(issue.getModified()))%> by <%=h(issue.getModifiedByName(user))%></td></tr>
            <tr><%=text(bean.renderLabel(bean.getLabel("Resolved", false)))%><td nowrap="true"><%=h(bean.writeDate(issue.getResolved()))%><%=text(issue.getResolvedBy() != null ? " by " : "")%> <%=h(issue.getResolvedByName(user))%></td></tr>
            <tr><%=text(bean.renderLabel(bean.getLabel("Resolution", false)))%><td><%=h(issue.getResolution())%></td></tr><%
            if (bean.isVisible("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {%>
            <tr><%=text(bean.renderLabel("Duplicate"))%><td><%
                if (bean.isVisible("duplicate"))
                {%>
                <%=text(bean.writeInput("duplicate", String.valueOf(issue.getDuplicate()), 10))%><%
                }
                else
                {%>
                <%=bean.renderDuplicate(issue.getDuplicate())%><%
                    }%>
            </td></tr><%
            }
            if (!issue.getDuplicates().isEmpty())
            {%>
            <tr><%=text(bean.renderLabel(bean.getLabel("Duplicates", false)))%><td><%=bean.renderDuplicates(issue.getDuplicates())%></td></tr><%
            }
            if (!issue.getRelatedIssues().isEmpty())
            {%>
            <tr><%=text(bean.renderLabel(bean.getLabel("Related", false)))%><td><%=bean.renderRelatedIssues(issue.getRelatedIssues())%></td></tr><%
            }
            for (DomainProperty prop : column1Props)
            {%>
            <%=text(bean.renderColumn(prop, getViewContext()))%><%
            }%>
        </table></td>
        <td valign="top" width="33%"><table class="lk-fields-table">
            <tr><%=text(bean.renderLabel(bean.getLabel("Closed", false)))%><td nowrap="true"><%=h(bean.writeDate(issue.getClosed()))%><%= issue.getClosedBy() != null ? " by " : "" %><%=h(issue.getClosedByName(user))%></td></tr>
            <%
                if (hasUpdatePerms)
                {%>
            <tr><%=text(bean.renderLabel(bean.getLabel("Notify", false)))%><td><%=bean.getNotifyList()%></td></tr><%
            }

            for (DomainProperty prop : column2Props)
            {%>
            <%=text(bean.renderColumn(prop, getViewContext()))%><%
            }%>
        </table></td>
    </tr>
</table>
<%
if (!issue.getRelatedIssues().isEmpty())
{
include(new RelatedIssuesView(context, issue.getRelatedIssues()), out);
}
%>
</labkey:panel>
<labkey:panel>
<%
for (int j = 0; j < commentLinkedList.size(); j++)
{
    Issue.Comment comment = commentLinkedList.get(j);
if (!issue.getComments().contains(comment))
{%>
<div class="relatedIssue" style="display: none; word-break: break-word; overflow-wrap: break-word"><%
        }
        else
        {%>
    <div class="currentIssue" style="display: inline; word-break: break-word; overflow-wrap: break-word"><%
        }%>

        <table width="100%"><tr><td class="comment-created" align="left"><b>
            <%=h(bean.writeDate(comment.getCreated()))%>
        </b></td><td class="comment-created-by" align="right"><b>
            <%=h(comment.getCreatedByName(user))%>
        </b></td></tr></table>
        <%
            if (!issue.getComments().contains(comment))
            {%>
        <div style="font-weight:bold;">Related # <%=comment.getIssue().getIssueId()%> </div><%
            }%>
        <%=comment.getComment()%>
        <%=bean.renderAttachments(context, comment)%>
        <%if (j != commentLinkedList.size() - 1) {%>
        <hr>
        <%}%>
    </div>
<% }
if (bean.getCallbackURL() != null) { %>
    <input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/>
<% } %>
</labkey:panel>