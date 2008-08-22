<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.CustomizeBean" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager.Settings" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager.Settings.SortOrder" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<CustomizeBean> me = (HttpView<CustomizeBean>) HttpView.currentView();
    CustomizeBean bean = me.getModelBean();
    Settings settings = bean.settings;

%><form action="customize.post" method="post">
<input type="hidden" name="<%=ReturnUrlForm.Params.returnUrl%>" value="<%=h(bean.returnURL)%>">
<table>
    <tr>
        <td class="labkey-form-label">Board name</td>
        <td><table><tr><td><input type="text" size="30" value="<%=settings.getBoardName()%>" name="boardName"></td><td>Custom term used in this folder to refer to the entire board.  Examples: "Discussions", "Announcements", "XYZ Study Consultations", "Message Board", "My Blog", etc.</td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Conversation name</td>
        <td><table><tr><td><input type="text" size="30" value="<%=settings.getConversationName()%>" name="conversationName"></td><td>Custom term used in this folder to refer to a conversation.  Examples: "Thread", "Discussion", "Announcement", "Consultation", etc.</td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Conversation sorting</td>
        <td>
            <table>
                <tr>
                    <td valign="top"><input type="radio" name="sortOrderIndex" value="<%=SortOrder.CreationDate%>" <%=settings.getSortOrderIndex() == SortOrder.CreationDate.getIndex() ? "checked" : ""%>></td>
                    <td><b>Initial Post</b> - Sort lists of conversations by date of the first posting.  This is appropriate for announcements and blogs.</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="sortOrderIndex" value="<%=SortOrder.LatestResponseDate%>" <%=settings.getSortOrderIndex() == SortOrder.LatestResponseDate.getIndex() ? "checked" : ""%>></td>
                    <td><b>Most Recent Post</b> - Sort lists of conversations by date of the most recent post.  This is often preferred for discussion boards.</td>
                </tr>
            </table>
        </td>
    </tr>
    <tr><td colspan=2>&nbsp;</td></tr><%
        if (null != bean.securityWarning)
        {
            %>
    <tr><td></td><td class="labkey-error"><%=bean.securityWarning%></td></tr><%
        }
    %>
    <tr>
        <td class="labkey-form-label" valign="middle">Security</td>
        <td>
            <table>
                <tr>
                    <td valign="top"><input type="radio" name="secure" value="0" <%=settings.isSecure() ? "" : "checked"%>></td>
                    <td><b>OFF</b> - Conversations are visible to anyone with read permissions, content can be modified after posting, content will be sent via email</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="secure" value="1" <%=settings.isSecure() ? "checked" : ""%>></td>
                    <td><b>ON</b> - Only editors and those on the member list can view conversations, content can't be modified after posting, content is never sent via email</td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Allow editing Title</td>
        <td><table><tr><td><input type="checkbox" name="titleEditable"<%=settings.isTitleEditable() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Member List</td>
        <td><table><tr><td><input type="checkbox" name="memberList"<%=settings.hasMemberList() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Status</td>
        <td><table><tr><td><input type="checkbox" name="status"<%=settings.hasStatus() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Expires</td>
        <td><table><tr><td><input type="checkbox" name="expires"<%=settings.hasExpires() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Assigned To</td>
        <td><table><tr><td><input type="checkbox" name="assignedTo"<%=settings.hasAssignedTo() ? " checked" : ""%>><td class="labkey-form-label">Default Assigned To</td><td><%=bean.assignedToSelect%></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Format Picker</td>
        <td><table><tr><td><input type="checkbox" name="formatPicker"<%=settings.hasFormatPicker() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Show Administrators Posters' Groups</td>
        <td><table><tr><td><input type="checkbox" name="includeGroups"<%=settings.includeGroups() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td colspan=2><%=PageFlowUtil.generateSubmitButton("Save")%>
        <%=PageFlowUtil.generateButton("Cancel", bean.returnURL)%></td>
    </tr>
</table>
</form>