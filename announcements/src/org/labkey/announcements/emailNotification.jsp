<%
/*
 * Copyright (c) 2005-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.announcements.EmailNotificationPage" %>
<%
    Container c = getViewContext().getContainer();
    User user = getViewContext().getUser();

    StringBuilder sb = new StringBuilder();
    String separator = "";

    if(!announcementModel.getAttachments().isEmpty())
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
<base href="<%=h(getViewContext().getActionURL().getBaseServerURI() + getViewContext().getContextPath())%>">
<%=PageFlowUtil.getStylesheetIncludes(c, user)%>
</head>

<body>
<table width=100%>
    <tr class="labkey-alternate-row"><td colspan="2" class="labkey-bordered" style="border-right: 0 none">
    <%=announcementModel.getCreatedByName(includeGroups, user) + (announcementModel.getParent() != null ? " responded" : " created a new " + settings.getConversationName().toLowerCase()) + "."%></td>
    <td align="right" class="labkey-bordered" style="border-left: 0 none"><%=formatDateTime(announcementModel.getCreated())%></td></tr><%

    if (null != body)
    { %>
    <tr><td colspan="3" id="message-body">
        <%=body%>
    </td></tr><%
    }

    %>
    <tr><td colspan="3">&nbsp;</td></tr>
    <tr><td colspan="3"><a href="<%=threadURL%>">View this <%=settings.getConversationName().toLowerCase()%></a></td></tr>
    <tr><td colspan="3">&nbsp;</td></tr>
    <tr><td colspan="3"><%= sb.toString() %></td></tr>
</table>

<br>
<br>
<hr size="1">

<table width=100%>
    <tr><td>You have received this email because <%
        switch(reason)
        {
            case signedUp:
    %>you are signed up to receive notifications about new posts to <a href="<%=boardURL%>"><%= PageFlowUtil.filter(boardPath) %></a> at <a href="<%=siteURL%>"><%=siteURL%></a>.
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
