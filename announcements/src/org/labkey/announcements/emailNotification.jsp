<%
/*
 * Copyright (c) 2005-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.announcements.EmailNotificationPage" %>
<%
    StringBuilder sb = new StringBuilder();
    String separator = "";

    if (!announcementModel.getAttachments().isEmpty())
    {
        sb.append("Attachments: ");
        for (Attachment attachment : announcementModel.getAttachments())
        {
           sb.append(separator);
           separator = ", ";
           sb.append(attachment.getName());
        }
    }
%>
<html>
<head>
    <base href="<%=h(ActionURL.getBaseServerURL())%>">
<%=PageFlowUtil.getStylesheetIncludes(c)
%></head>

<body>
<table width=100%>
    <tr class="labkey-alternate-row"><td colspan="2" class="labkey-bordered" style="border-right: 0 none">
    <%=h(announcementModel.getCreatedByName(includeGroups, recipient, false) + (announcementModel.getParent() != null ? " responded" : " created a new " + settings.getConversationName().toLowerCase()) + ".")%></td>
    <td align="right" class="labkey-bordered" style="border-left: 0 none"><%=formatDateTime(announcementModel.getCreated())%></td></tr><%

    if (null != body)
    { %>
    <tr><td colspan="3" id="message-body">
        <%=text(body)%>
    </td></tr><%
    }

    %>
    <tr><td colspan="3">&nbsp;</td></tr>
    <tr><td colspan="3"><a href="<%=h(threadURL.getURIString())%>">View this <%=h(settings.getConversationName().toLowerCase())%></a></td></tr>
    <tr><td colspan="3">&nbsp;</td></tr>
    <tr><td colspan="3"><%=h(sb)%></td></tr>
</table>

<br>
<br>
<hr size="1">

<table width=100%>
    <tr><td>You have received this email because you are <%
        switch(reason)
        {
            case signedUp:
    %>signed up to receive notifications about new posts to <a href="<%=h(boardURL.getURIString())%>"><%=h(boardPath)%></a> at <a href="<%=h(siteURL)%>"><%=h(siteURL)%></a>.
You must login to respond to this message.
If you no longer wish to receive these notifications you can <a href="<%=h(removeURL.getURIString())%>">change your email preferences</a>.<%
            break;

            case memberList:
    %>on the member list for this <%=h(settings.getConversationName().toLowerCase())%>.
You must login to respond to this message.
If you no longer wish to receive these notifications you can remove yourself from the member list by <a href="<%=h(removeURL.getURIString())%>">clicking here</a>.<%
            break;
        }
    %></td></tr>
</table>    
</body>
</html>
