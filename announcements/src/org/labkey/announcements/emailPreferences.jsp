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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager" %>
<%@ page extends="org.labkey.announcements.EmailPreferencesPage" %>
<b><%=message == null ? "" : message%></b>
<form action="emailPreferences.post" method="post">
    <br>Send email notifications for these <%=conversationName%>s<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_PREFERENCE_NONE%>" name="emailPreference" <%=emailPreference == AnnouncementManager.EMAIL_PREFERENCE_NONE ? " checked" : ""%>>
    <b>None</b> - Don't send me any email for this message board<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_PREFERENCE_MINE%>" name="emailPreference" <%=emailPreference == AnnouncementManager.EMAIL_PREFERENCE_MINE ? " checked" : ""%>>
    <b>Mine</b> - Send me email for posts to my <%=conversationName%>s (I've posted to the <%=conversationName%><% if (hasMemberList) { %> or I'm on its member list<% } %>)<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_PREFERENCE_ALL%>" name="emailPreference" <%=emailPreference == AnnouncementManager.EMAIL_PREFERENCE_ALL ? " checked" : ""%>>
    <b>All</b> - Send me email for all posts<br>

    <br>Notification type<br>
    <input type="radio" value="0" name="notificationType" <%= notificationType == 0 ? " checked" : ""%>>
    <b>Individual</b> - send a separate email after each post<br>
    <input type="radio" value="<%=AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST%>" name="notificationType" <%=notificationType == AnnouncementManager.EMAIL_NOTIFICATION_TYPE_DIGEST ? " checked" : "" %>>
    <b>Daily Digest</b> - send one email each day that summarizes all posts<br>

    <br><input type=hidden name="srcUrl"value="<%=PageFlowUtil.filter(srcURL)%>"/>
    <%=PageFlowUtil.generateSubmitButton("Update")%>
    <%=PageFlowUtil.generateButton((message == null ? "Cancel" : "Done"), srcURL)%>
</form>