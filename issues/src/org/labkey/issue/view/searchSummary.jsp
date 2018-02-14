<%
/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
<%@ page import="org.labkey.issue.model.Issue"%>
<%@ page import="java.util.regex.Matcher" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Issue> me = (JspView<Issue>) HttpView.currentView();
    final Issue issue = me.getModelBean();
    final User user = getUser();
%>
<table>
    <tr>
        <td valign="top">
            <table style="min-width:150pt;">
                <tr><td><label>Status:</label></td><td style="white-space: nowrap;"><%=h(issue.getStatus())%></td></tr>
                <% if (!StringUtils.equalsIgnoreCase(issue.getStatus(),"closed")) { %>
                <tr><td><label>Assigned&nbsp;To:</label></td><td style="white-space: nowrap;"><%=h(issue.getAssignedToName(user))%></td></tr>
                <% } %>
                <tr><td><label>Opened:</label></td><td style="white-space: nowrap;"><%=formatDate(issue.getCreated())%></td></tr>
                <% if (!StringUtils.equalsIgnoreCase(issue.getStatus(),"open")) { %>
                <tr><td><label>Resolved:</label></td><td style="white-space: nowrap;"><%=formatDate(issue.getResolved())%></td></tr>
                <% } %>
            </table>
        </td>
        <td>&nbsp;</td>
        <td>&nbsp;</td>
        <td valign="top">
<%
    StringBuilder html = new StringBuilder();
    boolean hasTextComment = false;
    for (Issue.Comment comment : issue.getComments())
    {
        String s = comment.getComment();
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
                if (html.length() > 1000)
                    break;
            }
        }
    }

    if (hasTextComment) { %>
            <label style="text-decoration: underline">Comments</label>
    <% } %>
            <div style="max-height:4em; overflow-y:hidden; word-break: break-all;"><%= text(html.toString()) %></div>
        </td>
    </tr>
</table>
