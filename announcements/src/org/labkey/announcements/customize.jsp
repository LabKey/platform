<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.CustomizeBean" %>
<%@ page import="org.labkey.announcements.AnnouncementsController.ModeratorReviewAction" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.announcements.DiscussionService.Settings.SortOrder" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<CustomizeBean> me = (HttpView<CustomizeBean>) HttpView.currentView();
    CustomizeBean bean = me.getModelBean();
    DiscussionService.Settings settings = bean.settings;

%><labkey:form action="<%=h(buildURL(AnnouncementsController.CustomizeAction.class))%>" method="post">
<%=generateReturnUrlFormField(bean.returnURL)%>
<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label">Board name</td>
        <td>
            Custom term used in this folder to refer to the entire board.  Examples: "Discussions", "Announcements", "XYZ Study Consultations", "Message Board", "My Blog", etc.<br/>
            <input type="text" size="30" value="<%=h(settings.getBoardName())%>" name="boardName">
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Conversation name</td>
        <td>
            Custom term used in this folder to refer to a conversation.  Examples: "Thread", "Discussion", "Announcement", "Consultation", etc.<br/>
            <input type="text" size="30" value="<%=h(settings.getConversationName())%>" name="conversationName">
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Conversation sorting</td>
        <td>
            <table>
                <tr>
                    <td valign="top"><input type="radio" name="sortOrderIndex" value="<%=SortOrder.CreationDate.getIndex()%>"<%=checked(settings.getSortOrderIndex() == SortOrder.CreationDate.getIndex())%>></td>
                    <td><b>Initial Post</b> - Sort lists of conversations by date of the first posting. This is appropriate for announcements and blogs.</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="sortOrderIndex" value="<%=SortOrder.LatestResponseDate.getIndex()%>"<%=checked(settings.getSortOrderIndex() == SortOrder.LatestResponseDate.getIndex())%>></td>
                    <td><b>Most Recent Post</b> - Sort lists of conversations by date of the most recent post. This is often preferred for discussion boards.</td>
                </tr>
            </table>
        </td>
    </tr>
    <tr><td colspan=2>&nbsp;</td></tr><%
        if (null != bean.securityWarning)
        {
            %>
    <tr><td></td><td class="labkey-error"><%=h(bean.securityWarning)%></td></tr><%
        }
    %>
    <tr>
        <td class="labkey-form-label" valign="middle">Security</td>
        <td>
            <table>
                <tr>
                    <td valign="top"><input type="radio" name="secure" value="0"<%=checked(!settings.isSecure())%>></td>
                    <td><b>OFF</b> - Conversations are visible to anyone with read permissions, content can be modified after posting, content will be sent via email</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="secure" value="1"<%=checked(settings.isSecure())%>></td>
                    <td><b>ON</b> - Only editors and those on the notify list can view conversations, content can't be modified after posting, content is never sent via email</td>
                </tr>
            </table>
        </td>
    </tr>
    <%
        boolean moderatorReview = "InitialPost".equals(settings.getModeratorReview()) || "All".equals(settings.getModeratorReview());
    %>
    <tr>
        <td class="labkey-form-label" valign="middle">Moderator review</td>
        <td>
            <table>
                <tr>
                    <td valign="top"><input type="radio" name="moderatorReview" value="None"<%=checked(!moderatorReview)%>></td>
                    <td><b>None</b> - New messages never require moderator review.</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="moderatorReview" value="InitialPost"<%=checked("InitialPost".equals(settings.getModeratorReview()))%>></td>
                    <td><b>Initial Post</b> - Authors must have their initial message(s) approved by a moderator. Subsequent messages are approved automatically.</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="moderatorReview" value="All"<%=checked("All".equals(settings.getModeratorReview()))%>></td>
                    <td><b>All</b> - Every new message by an Author must be approved by a moderator.</td>
                </tr>
                <%
                    if (moderatorReview)
                    {
                %>
                <tr>
                    <td colspan=2 valign="top"><%=link("Moderator Review page", ModeratorReviewAction.class)%></td>
                </tr>
                <%
                    }
                %>
            </table>
        </td>
    </tr>
    <tr>
        <td class="labkey-form-label">Allow editing Title</td>
        <td><table><tr><td><input type="checkbox" name="titleEditable"<%=checked(settings.isTitleEditable())%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Notify List</td>
        <td><table><tr><td><input type="checkbox" name="memberList"<%=checked(settings.hasMemberList())%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Status</td>
        <td><table><tr><td><input type="checkbox" name="status"<%=checked(settings.hasStatus())%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Expires</td>
        <td><table><tr><td><input type="checkbox" name="expires"<%=checked(settings.hasExpires())%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Assigned To</td>
        <td><table><tr><td><input type="checkbox" name="assignedTo"<%=checked(settings.hasAssignedTo())%>><td class="labkey-form-label">Default Assigned To</td><td><%=text(bean.assignedToSelect)%></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Include Format Picker</td>
        <td><table><tr><td><input type="checkbox" name="formatPicker"<%=checked(settings.hasFormatPicker())%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Show poster's groups, link user name to full details (admins only)</td>
        <td><table><tr><td><input type="checkbox" name="includeGroups"<%=checked(settings.includeGroups())%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Email templates</td>
        <td>
            <% if (me.getViewContext().getUser().hasRootAdminPermission()) { %><%= link("Customize site-wide template", urlProvider(AdminUrls.class).getCustomizeEmailURL(ContainerManager.getRoot(), AnnouncementManager.NotificationEmailTemplate.class, getViewContext().getActionURL()))%><br /><% } %>
            <%= link("Customize template for this " + getContainer().getContainerNoun(), urlProvider(AdminUrls.class).getCustomizeEmailURL(getContainer(), AnnouncementManager.NotificationEmailTemplate.class, getViewContext().getActionURL()))%>
        </td>
    </tr>
    <tr>
        <td colspan=2>
            <br/>
            <%= button("Save").submit(true) %>
            <% if (null != bean.returnURL) { %>
                <%= button("Cancel").href(bean.returnURL) %>
            <% } %>
        </td>
    </tr>
</table>
</labkey:form>