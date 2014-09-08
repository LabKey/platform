<%
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.announcements.DailyDigestPage" %>
<html>
<head>
    <base href="<%=h(ActionURL.getBaseServerURL())%>">
<%=PageFlowUtil.getStylesheetIncludes(c)
%></head>

<body>
<table width=100%>
    <tr><td><b>The following new posts were made yesterday in folder: <%=h(c.getPath())%></b></td></tr><%

    String previousThread = null;
    ActionURL threadURL = null;

    for (AnnouncementModel ann : announcementModels)
    {
        if (null == ann.getParent() || !ann.getParent().equals(previousThread))
        {
            if (null == ann.getParent())
                previousThread = ann.getEntityId();
            else
                previousThread = ann.getParent();

            if (null != threadURL)
            {
                %><tr><td><a href="<%=h(threadURL.getURIString())%>">View this <%=h(conversationName)%></a></td></tr><%
            }

            threadURL = AnnouncementsController.getThreadURL(c, previousThread, ann.getRowId());%>
            <tr><td>&nbsp;</td></tr><tr class="labkey-alternate-row"><td colspan="2" class="labkey-bordered"><%=h(ann.getTitle())%></td></tr><%
        }

        int attachmentCount = ann.getAttachments().size();

        %>
            <tr><td><%=h(ann.getCreatedByName(includeGroups, HttpView.currentContext().getUser()))%><% if (null == ann.getParent()) { %> created this <%=h(conversationName)%><% } else { %> responded <% } %> at <%=formatDateTime(ann.getCreated())%><%=h(attachmentCount > 0 ? " and attached " + attachmentCount + " document" + (attachmentCount > 1 ? "s" : "") : "")%></td></tr><%

        if (!settings.isSecure())
        {
            String body = ann.getFormattedHtml();
            %>
            <tr><td style="padding-left:35px;"><%=text(body)%></td></tr><%
        }
    }

    if (null != threadURL)
    {
        %><tr><td><a href="<%=h(threadURL.getURIString())%>">View this <%=h(conversationName)%></a></td></tr><%
    }

    %>
</table>

<br>
<br>
<hr size="1">

<table width=100%>
    <tr><td>You have received this email because you are signed up for a daily digest of new posts to <a href="<%=h(boardURL.getURIString())%>"><%= h(boardPath) %></a> at <a href="<%=h(siteUrl)%>"><%=h(siteUrl)%></a>.
  If you no longer wish to receive these notifications, please <a href="<%=h(removeURL.getURIString())%>">change your email preferences</a>.</td></tr>
</table>    
</body>
</html>
