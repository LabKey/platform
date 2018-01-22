<%
    /*
     * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.data.DataRegionSelection"%>
<%@ page import="org.labkey.api.exp.property.DomainProperty"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.security.permissions.ReadPermission"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueListDef" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssuePage" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.apache.commons.lang3.math.NumberUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.issue.view.RelatedIssuesView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.issues.IssuesListDefService" %>
<%@ page import="org.labkey.api.issues.IssueDetailHeaderLinkProvider" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="java.util.function.Function" %>
<%@ page import="java.util.stream.Stream" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    ViewContext context = getViewContext();
    IssuePage bean = me.getModelBean();

    Set<String> issueIds = bean.getIssueIds();
    final Container c = getContainer();
    final User user = getUser();
    IssueListDef issueDef = bean.getIssueListDef();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, issueDef.getName());

    ActionURL printLink = context.cloneActionURL().replaceParameter("_print", "1");
    if (bean.getDataRegionSelectionKey() != null)
        printLink.replaceParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY, bean.getDataRegionSelectionKey());

    if (!bean.isPrint())
    {
%>
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
    <div class="col-sm-5" style="margin-bottom: 5px">
        <div class="btn-group input-group-pull-right" role="group" aria-label="Create New Issue group" style="display: block;">
            <a class="btn btn-primary" style="margin-bottom: 8px;" href="<%=PageFlowUtil.getLastFilter(context, IssuesController.issueURL(c, IssuesController.InsertAction.class).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDef.getName()))%>">
                <%=h("new " + names.singularName.toLowerCase())%>
            </a>
        </div>
    </div>
</div>
<script type="text/javascript">
    var toggleRelatedFns = {};
    var toggleTimestampFns = {};
</script>
<%
    }

    for (String issueId : issueIds )
    {
        Issue issue = null;
        if (NumberUtils.isNumber(issueId))
            issue = IssueManager.getIssue(getContainer(), getUser(), Integer.parseInt(issueId));

        if (issue == null)
            continue;

        boolean hasReadPermission = ContainerManager.getForId(issue.getContainerId()).hasPermission(getUser(), ReadPermission.class);

        if (!hasReadPermission)
            continue;

        bean.setIssue(issue);
        // create collections for additional custom columns and distribute them evenly in the form
        List<DomainProperty> column1Props = new ArrayList<>();
        List<DomainProperty> column2Props = new ArrayList<>();
        int i=0;

        for (DomainProperty prop : bean.getCustomColumnConfiguration().getCustomProperties())
        {
            if ((i++ % 2) == 0)
                column1Props.add(prop);
            else
                column2Props.add(prop);
        }

        List<DomainProperty> propertiesList = new ArrayList<>(bean.getCustomColumnConfiguration().getCustomProperties());
        Map<String, DomainProperty> propertyMap = bean.getCustomColumnConfiguration().getPropertyMap();

        propertiesList.addAll(Stream.of("type", "area", "priority", "milestone")
                .filter(propertyMap::containsKey)
                .map((Function<String, DomainProperty>) propertyMap::get)
                .collect(Collectors.toList()));


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

        String recentTimeStampId = "recentTimeStamp" + issueId;
        String timestampsToggleId = "timestampsToggle" + issueId;
        String stampExpandIconId = "stampExpandIcon" + issueId;
        String allTimeStampsId = "allTimeStamps" + issueId;
        String relatedCommentsToggleId = "relatedCommentsToggle" + issueId;
        String relatedCommentsDivClassName = "relatedIssue" + issueId;
%>
<script type="text/javascript">
    var hidden = true;
    var showLess = true;

    /**
     * Toggle the hidden flag, set the hide button text to reflect state, and show or hide all related comments.
     */
    toggleRelatedFns[<%=h(issueId)%>] = function() {
        // change the button text
        var toggle = document.getElementById("<%=h(relatedCommentsToggleId)%>");
        if (!hidden)
            toggle.innerText = 'Show Related Comments';
        else
            toggle.innerText = 'Hide Related Comments';

        // show/hide comment elements
        var commentDivs = document.getElementsByClassName("<%=h(relatedCommentsDivClassName)%>");
        for (var i = 0; i < commentDivs.length; i++) {
            if (hidden)
                commentDivs[i].style.display = 'inline';
            else
                commentDivs[i].style.display = 'none';
        }
        hidden = !hidden;
    };

    toggleTimestampFns[<%=h(issueId)%>] = function () {
        var toggle = document.getElementById("<%=h(timestampsToggleId)%>");
        var allStampsDiv = document.getElementById("<%=h(allTimeStampsId)%>");
        var stampExpandIcon = document.getElementById("<%=h(stampExpandIconId)%>");

        if (showLess) {
            stampExpandIcon.className = 'fa fa-caret-up';
            allStampsDiv.style.display = "block";
        } else {
            stampExpandIcon.className = 'fa fa-caret-down';
            allStampsDiv.style.display = "none";
        }

        showLess = !showLess;
    };
</script>
<h3><%=h(issueId)%> : <%=h(issue.getTitle())%></h3>
<div class="row" style="margin-bottom: 10px">
    <div class="col-md-1">
        <label class="control-label"><%=text(bean.getLabel("Status", true))%></label>
        <div class="form-group"><%=h(issue.getStatus())%></div>
    </div>
    <%if (bean.isVisible("resolution") || !"open".equals(issue.getStatus()))
    {%>
    <div class="col-md-1">
        <label class="control-label"><%=text(bean.getLabel("Resolution", true))%></label>
        <div class="form-group">
            <%=h(issue.getResolution())%>
            <%if (issue.getResolution().equalsIgnoreCase("duplicate") && issue.getDuplicate() != null)
            {%>
            of&nbsp;<%=bean.renderDuplicate(issue.getDuplicate())%>
            <%}%>
        </div>
    </div>
    <%}%>
    <div class="col-md-2">
        <label class="control-label">Assigned To</label>
        <div class="form-group"><%=h(issue.getAssignedToName(user))%></div>
    </div>
    <div class="col-md-4">
        <label class="control-label">Recent Activity</label>
        <%
            Issue.IssueEvent m = issue.getMostRecentEvent(user);
            String lastUpdatedStr = "";
            String lastUpdatedTitleStr = "";
            if (null != m)
            {
                lastUpdatedStr = m.toString();
                lastUpdatedTitleStr = m.getFullTimestamp();
            }
        %>
        <div class="form-group">

            <div id="<%=h(recentTimeStampId)%>" title="<%=h(lastUpdatedTitleStr)%>"><%=h(lastUpdatedStr)%>
                <a id="<%=h(timestampsToggleId)%>" onclick="toggleTimestampFns[<%=h(issueId)%>]()">
                    <i id="<%=h(stampExpandIconId)%>" title="See all" class="fa fa-caret-down" style="cursor: pointer;"></i>
                </a>
            </div>

            <div id="<%=h(allTimeStampsId)%>" style="display: none;">
                <%
                    ArrayList<Issue.IssueEvent> eventArray = issue.getOrderedEventArray(user);

                    for (int j = 1; j < eventArray.size(); j++)
                    {
                        Issue.IssueEvent e = eventArray.get(j);
                        String stampString = e.toString();
                %>
                <div title="<%=h(e.getFullTimestamp())%>"><%=h(stampString)%></div>
                <%
                    }
                %>
            </div>
        </div>
    </div>
    <% if (!bean.getNotifyListCollection(false).isEmpty())
    {%>
    <div class="col-sm-4">
        <label>Notify List</label>
        <%for (String name : bean.getNotifyListCollection(false))
        {%>
        <div><%=h(name)%></div>
        <%}%>
    </div>
    <%}%>
</div>

<div class="row">
    <%  String mainContentClassName;
        if (!bean.getCustomColumnConfiguration().getCustomProperties().isEmpty() ||
                (null != issue.getDuplicates() && !issue.getDuplicates().isEmpty()))
        {
            mainContentClassName = "col-sm-10 col-sm-pull-2";
    %>
    <div class="col-sm-2 col-sm-push-10"><%
        if (!issue.getRelatedIssues().isEmpty())
        //vertical alignment with related boxes
        {%>
        <br class="input-group-disappear-sm">
        <%}%>
        <div style="word-wrap: break-word">
            <%if (null != issue.getDuplicates() && !issue.getDuplicates().isEmpty())
            {%>
            <div class="form-group">
                <label class="col-3 control-label">Duplicates</label>
                <div class="col-9">
                    <%=bean.renderDuplicates(issue.getDuplicates())%>
                </div>
            </div>

            <%}%>
            <%
                ArrayList<DomainProperty> propertyArr = new ArrayList<>();
                propertyArr.addAll(bean.getCustomColumnConfiguration().getCustomProperties());
            %><table><tbody><%
                for(DomainProperty prop : propertyArr)
                {%>
            <%=text(bean.renderColumn(prop, getViewContext(), true, true))%>
            <%}%>
            </tbody></table>
        </div>
    </div>

    <%}
    else
    {
        mainContentClassName = "col-sm-12";
    }
    %>

    <div class="<%=text(mainContentClassName)%>">
        <%
            if (!issue.getRelatedIssues().isEmpty())
            {
                RelatedIssuesView view = new RelatedIssuesView(context, issue.getRelatedIssues());
                include(view, out);

        %>
        <button class="btn btn-default btn-xs" id="<%=h(relatedCommentsToggleId)%>" onclick="toggleRelatedFns[<%=h(issueId)%>]()" style="margin-bottom: 10px">Show Related Comments</button>

        <%}%>
        <labkey:panel type="portal">

            <%
                for (Issue.Comment comment : IssueManager.getCommentsForRelatedIssues(issue, user))
                {
                    String styleStr = !issue.getComments().contains(comment) ? "display: none; word-break: break-all" : "display: inline; word-break: break-all";
                    String classStr = !issue.getComments().contains(comment) ? relatedCommentsDivClassName : "currentIssue";
            %>
            <div class="<%=text(classStr)%>" style="<%=text(styleStr)%>">
                <strong class=".comment-created-by">
                    <%=h(comment.getCreatedByName(user))%>
                </strong>
                <br>
                <strong class=".comment-created" title="<%=h(comment.getCreatedFullString())%>">
                    <%=h(bean.writeDate(comment.getCreated()))%>
                </strong>
                <%
                    if (!issue.getComments().contains(comment))
                    {%>
                <div style="font-weight:bold;">Related #<%=comment.getIssue().getIssueId()%> </div><%
                }%>
                <%=comment.getComment()%>
                <%=bean.renderAttachments(context, comment)%>
                <hr>
            </div>
            <%
                }

                if (bean.getHasUpdatePermissions())
                {%>
            <%=button("Update").href(IssuesController.issueURL(c, IssuesController.UpdateAction.class).addParameter("issueId", issueId))%>
            <%}%>
        </labkey:panel>
    </div>
</div>
<%}%>