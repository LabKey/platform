<%
    /*
     * Copyright (c) 2015-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.issue.model.Issue"%>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Issue> me = (JspView<Issue>) HttpView.currentView();
    final Issue issue = me.getModelBean();
    ViewContext context = getViewContext();
    final Container c = context.getContainer();
    final User user = getUser();
%>

<table><tr><td valign="top" >

    <table style="min-width:150pt;">
        <tr><td class="labkey-form-label" style="white-space: nowrap;">Status</td><td style="white-space: nowrap;"><%=h(issue.getStatus())%></td></tr>
        <% if (!StringUtils.equalsIgnoreCase(issue.getStatus(),"closed")) { %>
        <tr><td class="labkey-form-label" style="white-space: nowrap;">Assigned&nbsp;To</td><td style="white-space: nowrap;"><%=h(issue.getAssignedToName(user))%></td></tr>
        <% } %>
        <tr><td class="labkey-form-label" style="white-space: nowrap;">Opened</td><td style="white-space: nowrap;"><%=h(DateUtil.formatDate(c,issue.getCreated()))%></td></tr>
        <% if (!StringUtils.equalsIgnoreCase(issue.getStatus(),"open")) { %>
        <tr><td class="labkey-form-label" style="white-space: nowrap;">Resolved</td><td style="white-space: nowrap;"><%=h(DateUtil.formatDate(c, issue.getResolved()))%></td></tr>
        <% } %>
    </table>

</td><td valign="top">
    <%
        StringBuilder html = new StringBuilder();
        for (Issue.Comment comment : issue.getComments())
        {
            String s = comment.getComment();
            s = s.replaceFirst("(?s)<table class=issues-Changes>.*</table>","");
            html.append(s);
            if (html.length() > 1000)
                break;
        }
    %>
    <div style="max-height:50pt; overflow-y:hidden;"><%= text(html.toString()) %></div>
</td></tr></table>
