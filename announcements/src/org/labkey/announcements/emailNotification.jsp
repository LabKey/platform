<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.announcements.EmailNotificationPage" %>
<html>
<head>
<link href="<%=cssURL%>" rel="stylesheet" type="text/css">
</head>

<body>
<table width="100%" border="0" cellspacing="0" cellpadding="4">
    <tr><td class="normal" colspan="2" style="background-color: #dddddd">
    <%
        int attachmentCount = announcement.getAttachments().size();
    %>
    <%=announcement.getCreatedByName(includeGroups, HttpView.currentContext()) + (announcement.getParent() != null ? " responded" : " created a new " + settings.getConversationName().toLowerCase()) + (attachmentCount > 0 ? " and attached " + attachmentCount + " document" + (attachmentCount > 1 ? "s" : "") : "")%></td>
    <td class="normal" align="right" style="background-color: #dddddd"><%=formatDateTime(announcement.getCreated())%></td></tr><%

    if (null != body)
    { %>
    <tr><td colspan="3" class="normal"><%=body%></td></tr><%
    }

    %>
    <tr><td colspan="3" class="normal">&nbsp;</td></tr>
    <tr><td colspan="3" class="normal"><a href="<%=threadURL%>">View this <%=settings.getConversationName().toLowerCase()%></a></td></tr>
</table>

<br>
<br>
<hr size="1">

<table width="100%" border="0" cellspacing="0" cellpadding="4">
    <tr><td class="normal">You have received this email because <%
        switch(reason)
        {
            case broadcast:
    %>a site administrator sent this notification to all users of <a href="<%=siteURL%>"><%=siteURL%></a>.<%
            break;

            case signedUp:
    %>you are signed up to receive notifications about new posts to <a href="<%=boardURL%>"><%=boardPath%></a> at <a href="<%=siteURL%>"><%=siteURL%></a>.
If you no longer wish to receive these notifications you can <a href="<%=removeUrl%>">change your email preferences</a>.<%
            break;

            case memberList:
    %>you are on the member list for this <%=settings.getConversationName().toLowerCase()%>.  If you no longer wish to receive these
notifications you can remove yourself from the member list by <a href="<%=removeUrl%>">clicking here</a>.<%
            break;
        }
    %></td></tr>
</table>    
</body>
</html>
