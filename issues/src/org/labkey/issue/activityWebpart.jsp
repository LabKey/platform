<%
/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.issue.IssuesActivityWebPartFactory" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.w3c.tidy.Tidy" %>
<%@ page import="java.io.StringReader" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    IssuesActivityWebPartFactory.IssuesActivityBean bean = (IssuesActivityWebPartFactory.IssuesActivityBean) getModelBean();
    User user = getUser();
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());

    int COMMENT_MAX_LEN = 1000;
    Tidy tidy = new Tidy();
    tidy.setMakeBare(true);
    tidy.setPrintBodyOnly(true);

    if (bean.hasPermission)
    {
%>
<style type="text/css">
    .recent-comments .comment {
        padding-top: 1em;
        padding-bottom: 1em;
    }
    .recent-comments .comment-text {
        border: 1px solid #d3d3d3;
        border-radius: 2px;
        background: #E7EFF4;
    }
    .recent-comments .comment-created {
        font-style: italic;
        font-size: smaller;
        float: right;
    }
    .recent-comments hr {
        height: 1px;
        background: #add8e6;
    }
</style>
<div class="recent-comments">
<%
    for (Issue.Comment comment : bean.comments)
    {
        Issue issue = comment.getIssue();
        Container issueContainer = ContainerManager.getForId(issue.getContainerId());
        ActionURL detailsURL = IssuesController.getDetailsURL(issueContainer, issue.getIssueId(), false);
%>
    <div class="comment">
        <span class="comment-created-by"><%=h(comment.getCreatedByName(user))%></span>
        made changes to <a href="<%=detailsURL%>"><%=h(names.singularName)%> <%=issue.getIssueId()%></a>
        <a href="<%=detailsURL%>"><%=h(issue.getTitle())%></a>
        <div class="comment-text">
            <%
                String s = comment.getComment();
                if (s != null && s.length() > COMMENT_MAX_LEN)
                {
                    s = s.substring(0, COMMENT_MAX_LEN) + " <i>... <a href='" + detailsURL + "'>more</a></i>";
                    tidy.parse(new StringReader(s), out);
                }
                else
                {
                    out.write(s);
                }
            %>
        </div>
        <span class="comment-created"><%=formatDateTime(comment.getCreated())%></span>
        <span style="float:none;">&nbsp;</span>
    </div>
    <%--<hr>--%>
<%
    }
%>
</div>
<%=textLink("open " + names.pluralName.getSource(), bean.listURL + "Issues.Status~eq=open")%>
<%=textLink("submit new " + names.singularName.getSource(), bean.insertURL)%>
<%
    }
    else
    {
%>
<span>
  <% if (user.isGuest()) { %>
     Please log in to see this data.
  <% } else { %>
     You do not have permission to see this data.
  <% } %>
</span>
<% } %>
