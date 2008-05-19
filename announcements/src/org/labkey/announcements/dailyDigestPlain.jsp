<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController" %><%@ page import="org.labkey.announcements.model.Announcement" %><%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.announcements.DailyDigestPage" %>The following new posts were made yesterday in folder: <%=c.getPath()%>

<%
    String previousThread = null;
    String threadUrl = null;

    for (Announcement ann : announcements)
    {
        if (null == ann.getParent() || !ann.getParent().equals(previousThread))
        {
            if (null == ann.getParent())
                previousThread = ann.getEntityId();
            else
                previousThread = ann.getParent();

            if (null != threadUrl)
            {
                %>
View this <%=conversationName%> here:

<%=threadUrl%>


<%
            }

            threadUrl = AnnouncementsController.getThreadURL(c, previousThread, ann.getRowId()).getURIString();
%><%=ann.getTitle()%>

<%
        }
%><%=ann.getCreatedByName(includeGroups, HttpView.currentContext())%><% if (null == ann.getParent()) { %> created this <%=conversationName%><% } else { %> responded<% } %> at <%=DateUtil.formatDateTime(ann.getCreated())%><%

        int attachmentCount = ann.getAttachments().size();

        if (attachmentCount > 0)
            out.println(" and attached " + attachmentCount + " document" + (attachmentCount > 1 ? "s" : ""));
        else
            out.println();
    }

    if (null != threadUrl)
    {
       %>
View this <%=conversationName%> here:

<%=threadUrl%>

<%  }  %>


You have received this email because you are signed up for a daily digest of new posts to <%=boardPath%> at <%=siteUrl%>.
If you no longer wish to receive these notifications, please change your email preferences here: <%=removeUrl%>