package org.labkey.announcements.query;

import org.junit.Test;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementModel;
import org.labkey.announcements.model.MessageBoardContributorRole;
import org.labkey.announcements.model.Settings;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnnouncementTableTestCase extends AbstractContainerScopingTest
{
    @Test
    public void testQueryUpdateRetainsMemberList() throws Exception
    {
        Container board = createContainer("QueryUpdate");
        Settings settings = AnnouncementManager.getMessageBoardSettings(board);
        settings.setSecure(Settings.SECURE_OFF);
        settings.setMemberList(true);
        AnnouncementManager.saveMessageBoardSettings(board, settings);

        User member = createUserInRole(board, MessageBoardContributorRole.class);
        User other = createUserInRole(board, MessageBoardContributorRole.class);

        AnnouncementModel insert = new AnnouncementModel();
        insert.setTitle("Member list query update thread");
        insert.setBody("body");
        insert.setMemberListInput(member.getUserId() + "\n" + other.getUserId());
        AnnouncementModel thread = AnnouncementManager.insertAnnouncement(board, getAdmin(), insert, null, false);

        Set<Integer> expected = Set.of(member.getUserId(), other.getUserId());
        assertEquals(expected, new HashSet<>(AnnouncementManager.getPostMemberList(thread.getRowId())));

        UserSchema schema = QueryService.get().getUserSchema(getAdmin(), board, AnnouncementSchema.SCHEMA_NAME);
        TableInfo table = schema.getTable(AnnouncementSchema.ANNOUNCEMENT_TABLE_NAME);
        QueryUpdateService qus = table.getUpdateService();
        assertNotNull(qus);
        BatchValidationException errors = new BatchValidationException();
        qus.updateRows(getAdmin(), board,
            List.of(Map.of("RowId", thread.getRowId(), "Title", "Updated title")),
            List.of(Map.of("RowId", thread.getRowId())),
            errors, null, null);
        if (errors.hasErrors())
            throw errors;

        AnnouncementModel updated = AnnouncementManager.getAnnouncement(board, thread.getRowId());
        assertNotNull(updated);
        assertEquals("Updated title", updated.getTitle());
        assertEquals(expected, new HashSet<>(AnnouncementManager.getPostMemberList(thread.getRowId())));
    }
}
