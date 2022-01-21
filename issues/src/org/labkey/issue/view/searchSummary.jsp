<%
/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.security.User"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.issue.model.IssueObject"%>
<%@ page import="java.util.regex.Matcher" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssueObject> me = (JspView<IssueObject>) HttpView.currentView();
    final IssueObject issue = me.getModelBean();
    final User user = getUser();
    final boolean isClosed = StringUtils.equalsIgnoreCase(issue.getStatus(),"closed");
    final boolean isOpen = StringUtils.equalsIgnoreCase(issue.getStatus(),"open");
%>
<table style="min-width:150pt;margin-bottom:15px;">
    <tr><td style="color: #777777"><label>Status:</label></td><td style="white-space: nowrap;"><%=h(issue.getStatus())%></td></tr>
    <% if (!isClosed) { %>
    <tr><td style="color: #777777"><label>Assigned&nbsp;To:</label></td><td style="white-space: nowrap;"><%=h(issue.getAssignedToName(user))%></td></tr>
    <% } %>
    <tr><td style="color: #777777"><label>Opened:</label></td><td style="white-space: nowrap;"><%=formatDate(issue.getCreated())%></td></tr>
    <% if (!isOpen) { %>
    <tr><td style="color: #777777"><label>Resolved:</label></td><td style="white-space: nowrap;"><%=formatDate(issue.getResolved())%></td></tr>
    <% } %>
</table>

<div>
    <%
        StringBuilder html = new StringBuilder();
        boolean hasTextComment = false;
        for (IssueObject.CommentObject comment : issue.getCommentObjects())
        {
            String s = comment.getHtmlComment().toString();
            String pattern1 = "<div class=\"labkey-wiki\">";
            String pattern2 = "</div>";
            String regexString = Pattern.quote(pattern1) + "(?s)(.*?)" + Pattern.quote(pattern2);
            Pattern p = Pattern.compile(regexString);
            Matcher matcher = p.matcher(s);
            while (matcher.find())
            {
                String commentContentText = matcher.group(1);
                if (!StringUtils.isEmpty(commentContentText))
                {
                    hasTextComment = true;
                    html.append(commentContentText);
                    html.append("<br>");
                    if (html.length() > 500)
                        break;
                }
            }
        }

    if (hasTextComment) { %>
        <label style="text-decoration: underline">Comments</label>
    <% } %>
    <div style="max-height:4em; overflow-y:hidden; word-wrap:break-word; white-space: normal; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical;"><%= text(html.toString()) %></div>
</div>
