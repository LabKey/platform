<%@ page import="org.labkey.announcements.AnnouncementsController.CustomizeBean" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager.Settings" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager.Settings.SortOrder" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%
    HttpView<CustomizeBean> me = (HttpView<CustomizeBean>) HttpView.currentView();
    CustomizeBean bean = me.getModelBean();
    Settings settings = bean.settings;

%><form action="customize.post" method="post">
<input type="hidden" name="returnUrl" value="<%=bean.returnUrl.getEncodedLocalURIString()%>">
<table>
    <tr>
        <td class="ms-searchform">Board name</td>
        <td><table><tr><td class="normal"><input type="text" size="30" value="<%=settings.getBoardName()%>" name="boardName"></td><td>Custom term used in this folder to refer to the entire board.  Examples: "Discussions", "Announcements", "XYZ Study Consultations", "Message Board", "My Blog", etc.</td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Conversation name</td>
        <td><table><tr><td class="normal"><input type="text" size="30" value="<%=settings.getConversationName()%>" name="conversationName"></td><td>Custom term used in this folder to refer to a conversation.  Examples: "Thread", "Discussion", "Announcement", "Consultation", etc.</td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Conversation sorting</td>
        <td class="normal">
            <table>
                <tr>
                    <td valign="top"><input type="radio" name="sortOrderIndex" value="<%=SortOrder.CreationDate%>" <%=settings.getSortOrderIndex() == SortOrder.CreationDate.getIndex() ? "checked" : ""%>></td>
                    <td class="normal"><b>Initial Post</b> - Sort lists of conversations by date of the first posting.  This is appropriate for announcements and blogs.</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="sortOrderIndex" value="<%=SortOrder.LatestResponseDate%>" <%=settings.getSortOrderIndex() == SortOrder.LatestResponseDate.getIndex() ? "checked" : ""%>></td>
                    <td class="normal"><b>Most Recent Post</b> - Sort lists of conversations by date of the most recent post.  This is often preferred for discussion boards.</td>
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
        <td class="ms-searchform" valign="middle" style="padding-top:2;">Security</td>
        <td class="normal">
            <table>
                <tr>
                    <td valign="top"><input type="radio" name="secure" value="0" <%=settings.isSecure() ? "" : "checked"%>></td>
                    <td class="normal"><b>OFF</b> - Conversations are visible to anyone with read permissions, content can be modified after posting, content will be sent via email</td>
                </tr>
                <tr>
                    <td valign="top"><input type="radio" name="secure" value="1" <%=settings.isSecure() ? "checked" : ""%>></td>
                    <td class="normal"><b>ON</b> - Only editors and those on the member list can view conversations, content can't be modified after posting, content is never sent via email</td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="ms-searchform">Allow editing Title</td>
        <td><table><tr><td class="normal"><input type="checkbox" name="titleEditable"<%=settings.isTitleEditable() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Include Member List</td>
        <td><table><tr><td class="normal"><input type="checkbox" name="memberList"<%=settings.hasMemberList() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Include Status</td>
        <td><table><tr><td class="normal"><input type="checkbox" name="status"<%=settings.hasStatus() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Include Expires</td>
        <td><table><tr><td class="normal"><input type="checkbox" name="expires"<%=settings.hasExpires() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Include Assigned To</td>
        <td><table><tr><td class="normal"><input type="checkbox" name="assignedTo"<%=settings.hasAssignedTo() ? " checked" : ""%>><td class="ms-searchform">Default Assigned To</td><td><%=bean.assignedToSelect%></td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Include Format Picker</td>
        <td><table><tr><td class="normal"><input type="checkbox" name="formatPicker"<%=settings.hasFormatPicker() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td class="ms-searchform">Show Administrators Posters' Groups</td>
        <td><table><tr><td class="normal"><input type="checkbox" name="includeGroups"<%=settings.includeGroups() ? " checked" : ""%>></td></tr></table></td>
    </tr>
    <tr>
        <td colspan=2><input type="image" src="<%=PageFlowUtil.buttonSrc("Save")%>"/>
        <%=PageFlowUtil.buttonLink("Cancel", bean.returnUrl)%></td>
    </tr>
</table>
</form>