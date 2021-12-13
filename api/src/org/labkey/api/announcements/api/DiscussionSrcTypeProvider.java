package org.labkey.api.announcements.api;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public interface DiscussionSrcTypeProvider
{
    @Nullable
    ActionURL getThreadURL(Container container, User user, int announcementRowId, String discussionSrcIdentifier);

    @Nullable
    default String getEmailSubject(Container container, User user, int announcementRowId, String discussionSrcIdentifier, String body, String title, String parentBody)
    {
        return null;
    };

    default List<Integer> getMemberList(Container container, User user, int announcementRowId, String discussionSrcIdentifier, int createdBy, List<Integer> memberList, String parent, Integer threadAuthor)
    {
        return memberList;
    }

    default Set<User> getRecipients(Set<User> recipients, int createdBy)
    {
        return recipients;
    }
}
