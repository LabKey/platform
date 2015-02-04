<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController" %><%@ page import="org.labkey.announcements.model.AnnouncementModel" %><%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.announcements.DailyDigestPage" %>The following new posts were made yesterday in folder: <%=text(c.getPath())%>

<%
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
                %>
View this <%=text(conversationName)%> here:

<%=text(threadURL.getURIString())%>


<%
            }

            threadURL = AnnouncementsController.getThreadURL(c, previousThread, ann.getRowId());
%><%=text(ann.getTitle())%>

<%
        }
%><%=text(ann.getCreatedByName(includeGroups, HttpView.currentContext().getUser(), false))%><% if (null == ann.getParent()) { %> created this <%=text(conversationName)%><% } else { %> responded<% } %> at <%=text(DateUtil.formatDateTime(c, ann.getCreated()))%><%

        int attachmentCount = ann.getAttachments().size();

        if (attachmentCount > 0)
            out.println(" and attached " + attachmentCount + " document" + (attachmentCount > 1 ? "s" : ""));
        else
            out.println();
    }

    if (null != threadURL)
    {
       %>
View this <%=text(conversationName)%> here:

<%=text(threadURL.getURIString())%>

<%  }  %>


You have received this email because you are signed up for a daily digest of new posts to <%=text(boardPath)%> at <%=text(siteUrl)%>.
You must login to respond to this message.
If you no longer wish to receive these notifications, please change your email preferences here: <%=text(removeURL.getURIString())%>