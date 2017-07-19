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
<%@ page import="java.util.function.Function" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
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
    final boolean newUI = PageFlowUtil.useExperimentalCoreUI();

    List<Issue.Comment> commentLinkedList = IssueManager.getCommentsForRelatedIssues(issue, user);
    IssueListDef issueDef = IssueManager.getIssueListDef(issue);
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, issueDef.getName());

    //rip these out when transitioning to newUI
    List<DomainProperty> column1Props = new ArrayList<>();
    List<DomainProperty> column2Props = new ArrayList<>();
    List<DomainProperty> extraColumns = new ArrayList<>();

    //keep these
    List<DomainProperty> propertiesList = new ArrayList<>(bean.getCustomColumnConfiguration().getCustomProperties());
    Map<String, DomainProperty> propertyMap = bean.getCustomColumnConfiguration().getPropertyMap();
    if (newUI)
    {

        propertiesList.addAll(Stream.of("type", "area", "priority", "milestone")
                .filter(propertyMap::containsKey)
                .map((Function<String, DomainProperty>) propertyMap::get)
                .collect(Collectors.toList()));
    }
    else
    {
        // create collections for additional custom columns and distribute them evenly in the form
        // assigned to, type, area, priority, milestone
        int i=0;

        propertyMap = bean.getCustomColumnConfiguration().getPropertyMap();
        // todo: don't include if the lookup is empty (was previously IssuePage.hasKeywords)
        extraColumns.addAll(Stream.of("assignedto", "type", "area", "priority", "milestone")
                .filter(propertyMap::containsKey)
                .map((Function<String, DomainProperty>) propertyMap::get)
                .collect(Collectors.toList()));

        for (DomainProperty prop : bean.getCustomColumnConfiguration().getCustomProperties())
        {
            if ((i++ % 2) == 0)
                column1Props.add(prop);
            else
                column2Props.add(prop);
        }
    }


    int commentCount = issue.getComments().size();
    boolean hasAttachments = false;

    // Determine if the comment has attachments
    for (Issue.Comment comment : commentLinkedList)
    {
        // Determine if the comment has attachments
        hasAttachments = hasAttachments ? true : !bean.renderAttachments(context, comment).isEmpty();
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
    relatedIssues.append(", assignedTo :").append(issue.getAssignedTo() == null ? null : issue.getAssignedTo());
    relatedIssues.append(", priority :").append(issue.getPriority() == null ? null : issue.getPriority());
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
%>
<% if (!newUI) {%>

<% if (!bean.isPrint())
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

<labkey:form name="jumpToIssue" action="<%=h(buildURL(IssuesController.JumpToIssueAction.class))%>" method="get">
    <table><tr><%
        if (bean.getHasUpdatePermissions())
        {%>
            <td><%= textLink("new " + names.singularName.toLowerCase(), PageFlowUtil.getLastFilter(context, IssuesController.issueURL(c, IssuesController.InsertAction.class).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDef.getName())))%></td><%
        }%>
            <td><%= textLink("return to grid", PageFlowUtil.getLastFilter(context, IssuesController.issueURL(c, IssuesController.ListAction.class).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDef.getName())).deleteParameter("error"))%></td><%
        if (bean.getHasUpdatePermissions())
        {%>
            <td><%= textLink("update", IssuesController.issueURL(c, IssuesController.UpdateAction.class).addParameter("issueId", issueId))%></td><%
        }
        if (issue.getStatus().equals(Issue.statusOPEN) && bean.getHasUpdatePermissions())
        {%>
            <td><%= textLink("resolve", IssuesController.issueURL(c, IssuesController.ResolveAction.class).addParameter("issueId", issueId))%></td><%
        }
        else if (issue.getStatus().equals(Issue.statusRESOLVED) && bean.getHasUpdatePermissions())
        {%>
            <td><%= textLink("close", IssuesController.issueURL(c, IssuesController.CloseAction.class).addParameter("issueId", issueId))%></td>
            <td><%= textLink("reopen", IssuesController.issueURL(c, IssuesController.ReopenAction.class).addParameter("issueId", issueId))%></td><%
        }
        else if (issue.getStatus().equals(Issue.statusCLOSED) && bean.getHasUpdatePermissions())
        {%>
            <td><%= textLink("reopen", IssuesController.issueURL(c, IssuesController.ReopenAction.class).addParameter("issueId", issueId))%></td><%
        }
        if (bean.getHasAdminPermissions() && bean.hasMoveDestinations())
        {%>
            <td><%= textLink("move", "javascript:void(0)", "Issues.window.MoveIssue.create(" + issueId + ", " + PageFlowUtil.jsString(issueDef.getName()) + ")", "")%></td><%
        }%>
            <td><%= textLink("print", context.cloneActionURL().replaceParameter("_print", "1"))%></td><%

        if (!getUser().isGuest())
        {%>
            <td><%= textLink("email prefs", IssuesController.issueURL(c, EmailPrefsAction.class).addParameter("issueId", issueId))%></td>
            <td><%= textLink("create related issue", relatedIssues.toString()) %></td><%
        }
        if ( IssueManager.hasRelatedIssues(issue, user))
        {%>
            <td><%= PageFlowUtil.textLink("show related comments", "javascript:toggleComments()", "", "showRelatedComments") %></td><%
        }

        for (NavTree headerLink : additionalHeaderLinks)
        {
            String linkText = headerLink.isDisabled()
                    ? "<span class='labkey-disabled-text-link'>" + headerLink.getText() + "</span>"
                    : textLink(headerLink.getText(), headerLink.getHref());
        %>
            <td><%= linkText %></td>
        <%}
        %>
        <td>&nbsp;&nbsp;&nbsp;Jump to <%=h(names.singularName)%>: <input type="text" size="5" name="issueId"/></td>
    </tr></table>
</labkey:form><%
    }
    else
    {
%>
<div class="labkey-nav-page-header-container"><span class="labkey-nav-page-header"><%=h(names.singularName + " " + issue.getIssueId() + ": " +issue.getTitle())%></span><p></div>
<%
    }
%>
<table class="issue-fields" style="width: 60%;">
    <tr>
        <td valign="top"><table>
            <tr><td class="labkey-form-label">Status</td><td><%=h(issue.getStatus())%></td></tr><%
            for (DomainProperty prop : extraColumns)
            {%>
                <%=text(bean.renderColumn(prop, getViewContext()))%><%
            }%>

            <%=text(bean.renderAdditionalDetailInfo())%>
        </table></td>
        <td valign="top"><table>
            <tr><td class="labkey-form-label"><%=text(bean.getLabel("Opened", false))%></td><td nowrap="true"><%=bean.writeDate(issue.getCreated())%> by <%=h(issue.getCreatedByName(user))%></td></tr>
            <tr><td class="labkey-form-label">Changed</td><td nowrap="true"><%=h(bean.writeDate(issue.getModified()))%> by <%=h(issue.getModifiedByName(user))%></td></tr>
            <tr><td class="labkey-form-label"><%=text(bean.getLabel("Resolved", false))%></td><td nowrap="true"><%=h(bean.writeDate(issue.getResolved()))%><%=text(issue.getResolvedBy() != null ? " by " : "")%> <%=h(issue.getResolvedByName(user))%></td></tr>
            <tr><td class="labkey-form-label"><%=text(bean.getLabel("Resolution", false))%></td><td><%=h(issue.getResolution())%></td></tr><%
            if (bean.isVisible("resolution") || !"open".equals(issue.getStatus()) && null != issue.getDuplicate())
            {%>
            <tr><td class="labkey-form-label">Duplicate</td><td><%
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
            <tr><td class="labkey-form-label">Duplicates</td><td><%=bean.renderDuplicates(issue.getDuplicates())%></td></tr><%
            }
            if (!issue.getRelatedIssues().isEmpty())
            {%>
            <tr><td class="labkey-form-label"><%=text(bean.getLabel("Related", false))%></td><td><%=bean.renderRelatedIssues(issue.getRelatedIssues())%></td></tr><%
            }
            for (DomainProperty prop : column1Props)
            {%>
            <%=text(bean.renderColumn(prop, getViewContext()))%><%
            }%>
        </table></td>
        <td valign="top" width="33%"><table>
            <tr><td class="labkey-form-label">Closed</td><td nowrap="true"><%=h(bean.writeDate(issue.getClosed()))%><%= issue.getClosedBy() != null ? " by " : "" %><%=h(issue.getClosedByName(user))%></td></tr>
            <%
                if (hasUpdatePerms)
                {%>
            <tr><td class="labkey-form-label">Notify</td><td><%=bean.getNotifyList()%></td></tr><%
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
RelatedIssuesView view = new RelatedIssuesView(context, issue.getRelatedIssues());
include(view, out);
}

for (Issue.Comment comment : commentLinkedList)
{
if (!issue.getComments().contains(comment))
{%>
<div class="relatedIssue" style="display: none;"><%
        }
        else
        {%>
    <div class="currentIssue" style="display: inline;"><%
        }%>
        <hr><table width="100%"><tr><td class="comment-created" align="left"><b>
            <%=h(bean.writeDate(comment.getCreated()))%>
        </b></td><td class="comment-created-by" align="right"><b>
            <%=h(comment.getCreatedByName(user))%>
        </b></td></tr></table>
        <%
            if (!issue.getComments().contains(comment))
            {%>
        <div style="color:blue;font-weight:bold;">Related # <%=comment.getIssue().getIssueId()%> </div><%
            }%>
        <%=comment.getComment()%>
        <%=bean.renderAttachments(context, comment)%>
    </div>
<%  }
}
else
    {
%>
<script type="text/javascript">
    var hidden = true;
    var showLess = true;

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

    /**
     * Toggle the hidden flag, set the hide button text to reflect state, and show or hide all related comments.
     */
    function toggleComments() {
        // change the button text
        var toggle = document.getElementById('relatedCommentsToggle');
        if (!hidden)
            toggle.text = 'Show Related Comments';
        else
            toggle.text = 'Hide Related Comments';

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

    function showMoreTimestamps() {
        var toggle = document.getElementById("timestampsToggle");
        var allStampsDiv = document.getElementById("allTimeStamps");
        var stampExpandIcon = document.getElementById("stampExpandIcon");

        if (!showLess) {
            stampExpandIcon.className = 'fa fa-caret-up';
            allStampsDiv.style.display = "block";
        } else {
            stampExpandIcon.className = 'fa fa-caret-down';
            allStampsDiv.style.display = "none";
        }

        showLess = !showLess;
    }
</script>

    <%if (bean.getHasUpdatePermissions())
    {%>
    <div class="row">
        <div class="col-sm-4" style="margin-bottom: 5px">
            <div class="btn-group" role="group" aria-label="Action Group" style="display: block;">
                <a class="btn btn-default" href="<%=IssuesController.issueURL(c, IssuesController.UpdateAction.class).addParameter("issueId", issueId)%>">Update</a>
                <% if (issue.getStatus().equals(Issue.statusOPEN))
                {%>
                <a class="btn btn-default" href="<%=IssuesController.issueURL(c, IssuesController.ResolveAction.class).addParameter("issueId", issueId)%>">Resolve</a>
                <%}
                else if (issue.getStatus().equals(Issue.statusRESOLVED))
                {%>
                <a class="btn btn-default" href="<%=IssuesController.issueURL(c, IssuesController.CloseAction.class).addParameter("issueId", issueId)%>">Close</a>
                <a class="btn btn-default" href="<%=IssuesController.issueURL(c, IssuesController.ReopenAction.class).addParameter("issueId", issueId)%>">Reopen</a>
                <%}
                else if (issue.getStatus().equals(Issue.statusCLOSED) && bean.getHasUpdatePermissions())
                {%>
                <a class="btn btn-default" href="<%=IssuesController.issueURL(c, IssuesController.ReopenAction.class).addParameter("issueId", issueId)%>">Reopen</a>
                <%}%>

            </div>
            &nbsp;
            &nbsp;
            <span id="moreMenuToggle" class="lk-menu-drop dropdown">
                <a data-toggle="dropdown" class="btn btn-default">More</a>
                <ul class="dropdown-menu dropdown-menu-left">
                    <%if (!getUser().isGuest())
                    {%>
                        <li><a href="<%=IssuesController.issueURL(c, EmailPrefsAction.class).addParameter("issueId", issueId)%>">Email Preferences</a></li>
                    <%}
                        if (bean.getHasAdminPermissions() && bean.hasMoveDestinations())
                        {%>
                        <li><a>Move</a></li>
                    <%}%>
                    <li><a href="<%=context.cloneActionURL().replaceParameter("_print", "1")%>">Print</a></li>
                    <%
                        for (NavTree headerLink : additionalHeaderLinks)
                        {
                            String isDisabled = headerLink.isDisabled() ? "disabled" : ""; %>
                        <li class="<%=text(isDisabled)%>"><a href="<%=headerLink.getHref()%>"></a></li>
                    <%}%>
                </ul>
            </span>
        </div>
        <div class="col-sm-4" style="margin-bottom: 5px">
            <labkey:form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" layout="inline">
                <labkey:input name="issueId" placeholder="ID # or Search Term"/>
                <%= button("Search").iconCls("search").submit(true) %>
            </labkey:form>
        </div>
        <div class="col-sm-4" style="margin-bottom: 5px">
            <%if (bean.getHasUpdatePermissions())
            {%>
            <div class="btn-group" role="group" aria-label="Create New Issue group" style="display: block;">
                <a class="btn btn-primary" style="margin-bottom: 8px;" href="<%=PageFlowUtil.getLastFilter(context, IssuesController.issueURL(c, IssuesController.InsertAction.class).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDef.getName()))%>">
                    <%="new " + names.singularName.toLowerCase()%>
                </a>
                <span class="lk-menu-drop dropdown">
                    <a class="btn btn-primary" data-toggle="dropdown">
                        <i class="fa fa-caret-down"></i>
                    </a>
                    <ul class="dropdown-menu dropdown-menu-right">
                        <li><a onclick="<%=relatedIssues.toString()%>">Create Related Issue</a></li>
                    </ul>
                </span>
            </div>
            <%}%>
        </div>
    </div>
    <%}
    else
    {%>
    <div class="row">
        <div class="col-sm-3" style="margin-bottom: 5px">
            <a class="btn btn-default" style="margin-bottom: 8px;" href="<%=context.cloneActionURL().replaceParameter("_print", "1")%>">Print</a>
        </div>
        <div class="col-sm-4" style="margin-bottom: 5px">
            <labkey:form name="jumpToIssue" action="<%= new ActionURL(IssuesController.JumpToIssueAction.class, c) %>" layout="inline">
                <labkey:input name="issueId" placeholder="ID # or Search Term"/>
                <%= button("Search").iconCls("search").submit(true) %>
            </labkey:form>
        </div>
    </div>
    <%} %>
<div class="row">
    <div class="col-sm-1">
        <label><%=text(bean.getLabel("Status", true))%></label>
        <div class="form-group"><%=h(issue.getStatus())%></div>
    </div>
    <div class="col-sm-2">
        <label>Assigned To</label>
        <div class="form-group"><%=issue.getAssignedToName(user)%></div>
    </div>
    <div class="col-sm-4">
        <label>Recent Activity</label>
        <%
            Map<String, String> m = issue.getRecentTimestampMap(user);
            String lastUpdatedStr = "";
            if (null != m)
            {
                lastUpdatedStr = m.get("event") + ": " + m.get("date") + " by " + m.get("user");
            }
        %>
        <div class="form-group">
            <div id="recentTimeStamp"><%=lastUpdatedStr%>
                <a id="timestampsToggle" onclick="showMoreTimestamps()">
                    <i id="stampExpandIcon" title="See all" class="fa fa-caret-down" style="cursor: pointer;"></i>
                </a>
            </div>

            <div id="allTimeStamps" style="display: none;">
                <%
                    List<Map<String, String>> mapList = issue.getOrderedTimestampMapArray(user);

                    for (int j = 1; j < mapList.size(); j++)
                    {
                        Map<String, String> s = mapList.get(j);
                        String stampString = s.get("event") + ": " + s.get("date") + " by " + s.get("user");
                %>
                <div><%=stampString%></div>
                <%
                    }
                %>
            </div>
        </div>
    </div>
    <% if (!bean.getNotifyList().trim().equals(""))
    {%>
    <div class="col-sm-4">
        <label>Notify List</label>
        <div><%=bean.getNotifyList()%></div>
    </div>
    <%}%>
</div>
<%
    if (!issue.getRelatedIssues().isEmpty())
    {%>
<div class="row">
    <div class="col-sm-9">
       <% RelatedIssuesView view = new RelatedIssuesView(context, issue.getRelatedIssues());
        include(view, out);
        %>
        <a class="btn btn-default btn-xs" id="relatedCommentsToggle" onclick="toggleComments()">Show Related Comments</a>
    </div>
</div>
<%}%>

<div class="row">
    <div class="col-sm-3 col-sm-push-9">
        <br>
        <%
            ArrayList<DomainProperty> propertyArr = new ArrayList<>(extraColumns);
            propertyArr.addAll(bean.getCustomColumnConfiguration().getCustomProperties());
            for(DomainProperty prop : propertyArr)
            { %>
        <%=bean.renderColumn(prop, getViewContext(), true, true)%>
        <%}%>
    </div>
    <div class="col-sm-9 col-sm-pull-3">
        <%
        for (Issue.Comment comment : commentLinkedList)
        {
            String styleStr = !issue.getComments().contains(comment) ? "display: none" : "display: inline";
            String classStr = !issue.getComments().contains(comment) ? "relatedIssue" : "currentIssue";
            %>
        <div class="<%=classStr%>" style="<%=styleStr%>">
            <hr>
                <strong>
                    <%=h(comment.getCreatedByName(user))%>
                </strong>
                <br>
                <strong>
                    <%=h(bean.writeDate(comment.getCreated()))%>
                </strong>
                <%
                if (!issue.getComments().contains(comment))
                {%>
                    <div style="color:blue;font-weight:bold;">Related # <%=comment.getIssue().getIssueId()%> </div><%
                }%>
                <%=comment.getComment()%>
                <%=bean.renderAttachments(context, comment)%>
            </div>
            <%
        }%>
    </div>
</div>

<%}%>


<%
    if (bean.getCallbackURL() != null)
    {%>
<input type="hidden" name="callbackURL" value="<%=bean.getCallbackURL()%>"/>
  <%}%>


