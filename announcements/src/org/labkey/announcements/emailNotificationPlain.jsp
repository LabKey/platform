<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page extends="org.labkey.announcements.EmailNotificationPage" %>

<%=text(announcementModel.getCreatedByName(includeGroups, recipient, false) + (announcementModel.getParent() != null ? " responded" : " created a new " + settings.getConversationName().toLowerCase())) %> at <%=text(DateUtil.formatDateTime(c, announcementModel.getCreated()))%>.<%

    StringBuilder sb = new StringBuilder();
    String separator = "";
    for (Attachment attachment : announcementModel.getAttachments())
    {
       sb.append(separator);
       separator = ", ";
       sb.append(attachment.getName());
    }
    String attachmentString = sb.toString();

    int attachmentCount = announcementModel.getAttachments().size();

    if (attachmentCount > 0)
        out.println("\n\nAttachments: " + attachmentString + ".");
    else
        out.println();

    if (null != body)
    {
        out.print(text(body));
    }
%>
View this <%=text(settings.getConversationName().toLowerCase())%> here:

<%=text(threadURL.getURIString())%>


You have received this email because you are you are <%
    switch(reason)
    {
        case signedUp:
%>signed up to receive notifications about new posts to <%=text(boardPath)%> at <%=text(siteURL)%>.
You must login to respond to this message.
If you no longer wish to receive these notifications you can change your email preferences by
navigating here: <%=text(removeURL.getURIString())%>.<%
        break;

        case memberList:
%>on the member list for this <%=text(settings.getConversationName().toLowerCase())%>.
You must login to respond to this message.
If you no longer wish to receive these notifications you can remove yourself from
the member list by navigating here: <%=text(removeURL.getURIString())%><%
        break;
    }
%>
