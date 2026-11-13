package org.labkey.announcements;

import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.announcements.model.MessageBoardContributorRole;
import org.labkey.announcements.model.Settings;
import org.labkey.api.announcements.api.Announcement;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.view.ActionURL;

import java.util.HashSet;
import java.util.Set;

// GH Issue 1871: a write that doesn't supply a member list must keep the existing one
public class MemberListRetentionTestCase extends AbstractContainerScopingTest
{
    private Container createBoard(String name)
    {
        Container c = createContainer(name);
        setMemberListEnabled(c, true);
        return c;
    }

    private void setMemberListEnabled(Container c, boolean enabled)
    {
        Settings settings = AnnouncementManager.getMessageBoardSettings(c);
        settings.setSecure(Settings.SECURE_OFF);
        settings.setMemberList(enabled);
        AnnouncementManager.saveMessageBoardSettings(c, settings);
    }

    private AnnouncementModel insertThread(Container c, User... members) throws Exception
    {
        AnnouncementModel insert = new AnnouncementModel();
        insert.setTitle("Member list retention thread");
        insert.setBody("body");
        insert.setMemberListInput(String.join("\n", memberIds(members).stream().map(String::valueOf).toList()));
        return AnnouncementManager.insertAnnouncement(c, getAdmin(), insert, null, false);
    }

    private static @NotNull Set<Integer> memberIds(User... members)
    {
        Set<Integer> ids = new HashSet<>();
        for (User member : members)
            ids.add(member.getUserId());
        return ids;
    }

    @Test
    public void testServiceInsertResponseRetainsMemberList() throws Exception
    {
        Container board = createBoard("ServiceResponse");
        User member = createUserInRole(board, MessageBoardContributorRole.class);
        User other = createUserInRole(board, MessageBoardContributorRole.class);
        AnnouncementModel thread = insertThread(board, member, other);
        Set<Integer> expected = memberIds(member, other);

        Announcement response = AnnouncementService.get().insertAnnouncement(board, getAdmin(), "Response", "response body", false, thread.getRowId());
        assertEquals("Response inserted without a member list should carry the thread's list",
            expected, new HashSet<>(AnnouncementManager.getPostMemberList(response.getRowId())));
        AnnouncementModel refetched = AnnouncementManager.getAnnouncement(board, thread.getEntityId());
        assertNotNull(refetched);
        assertEquals("Thread's effective member list should be unchanged after a response without a member list",
            expected, new HashSet<>(refetched.getMemberListIds()));

        Announcement responseWithNullList = AnnouncementService.get().insertAnnouncement(board, getAdmin(), "Response", "response body", false, thread.getRowId(), null, null);
        assertEquals("Response inserted with a null member list should carry the thread's list",
            expected, new HashSet<>(AnnouncementManager.getPostMemberList(responseWithNullList.getRowId())));
    }

    @Test
    public void testUpdateFormWithMemberListOffRetainsMemberList() throws Exception
    {
        Container board = createBoard("UpdateFormMemberListOff");
        User member = createUserInRole(board, MessageBoardContributorRole.class);
        User other = createUserInRole(board, MessageBoardContributorRole.class);
        AnnouncementModel thread = insertThread(board, member, other);
        Set<Integer> expected = memberIds(member, other);
        assertEquals(expected, new HashSet<>(AnnouncementManager.getPostMemberList(thread.getRowId())));

        // With the setting off, update.jsp renders no memberListInput field, so the post omits it
        setMemberListEnabled(board, false);
        ActionURL update = new ActionURL(AnnouncementsController.UpdateAction.class, board)
            .addParameter("rowId", thread.getRowId())
            .addParameter("entityId", thread.getEntityId())
            .addParameter("title", "Updated title")
            .addParameter("rendererType", thread.getRendererType())
            .addParameter("body", "updated body");

        assertStatus(HttpServletResponse.SC_FOUND, post(update, getAdmin()));
        AnnouncementModel updated = AnnouncementManager.getAnnouncement(board, thread.getRowId());
        assertNotNull(updated);
        assertEquals("Update action should have saved the new title", "Updated title", updated.getTitle());
        assertEquals("Update form without a member list field should keep the post's member list",
            expected, new HashSet<>(AnnouncementManager.getPostMemberList(thread.getRowId())));
    }
}
