<%
/*
 * Copyright (c) 2005-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.announcements.EmailOption" %>
<%@ page extends="org.labkey.announcements.EmailPreferencesPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">
    function toggleOptions()
    {
        var checkBoxElem = document.getElementById("resetFolderDefault");

        var isReset = checkBoxElem.checked;
        if (checkBoxElem != null)
        {
            var inputs = document.getElementsByTagName('input');
            for(var i = 0; i < inputs.length; i++) {
                if(inputs[i].type.toLowerCase() === 'radio') {
                    inputs[i].disabled = isReset;
                }
            }
        }
    }
</script>
<b><%=h(message)%></b>
<labkey:form action="<%=h(buildURL(AnnouncementsController.EmailPreferencesAction.class))%>" method="post">
    <br>Send email notifications for these <%=h(conversationName)%>s<br>
    <input type="radio" value="<%=EmailOption.MESSAGES_NONE.getValue()%>" name="emailPreference"<%=checked(emailPreference == EmailOption.MESSAGES_NONE.getValue())%>>
    <b>None</b> - Don't send me any email for this message board<br>
    <input type="radio" value="<%=EmailOption.MESSAGES_MINE.getValue()%>" name="emailPreference"<%=checked(emailPreference == EmailOption.MESSAGES_MINE.getValue())%>>
    <b>Mine</b> - Send me email for posts to my <%=h(conversationName)%>s (I've posted to the <%=h(conversationName)%><% if (hasMemberList) { %> or I'm on its member list<% } %>)<br>
    <input type="radio" value="<%=EmailOption.MESSAGES_ALL.getValue()%>" name="emailPreference"<%=checked(emailPreference == EmailOption.MESSAGES_ALL.getValue())%>>
    <b>All</b> - Send me email for all posts<br>

    <br>Notification type<br>
    <input type="radio" value="<%=EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue()%>" name="notificationType"<%=checked(notificationType == EmailOption.MESSAGES_NO_DAILY_DIGEST.getValue())%>>
    <b>Individual</b> - send a separate email after each post<br>
    <input type="radio" value="<%=EmailOption.MESSAGES_DAILY_DIGEST.getValue()%>" name="notificationType"<%=checked(notificationType == EmailOption.MESSAGES_DAILY_DIGEST.getValue())%>>
    <b>Daily Digest</b> - send one email each day that summarizes all posts<br>

   <br><input type=checkbox id="resetFolderDefault" name="resetFolderDefault" onclick="toggleOptions();"><b> Reset</b> - Reset to folder default setting<br>

    <br><input type=hidden name="srcUrl" value="<%=h(srcURL)%>"/>
    <br><input type=hidden name="srcIdentifier" value="<%=h(srcIdentifier)%>"/>
    <%= button("Update").submit(true) %>
    <%= button((message == null ? "Cancel" : "Done")).href(srcURL) %>
</labkey:form>