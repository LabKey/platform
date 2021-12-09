package org.labkey.api.announcements.api;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import javax.annotation.Nullable;

public interface DiscussionSrcTypeProvider
{
    @Nullable
    ActionURL getThreadURL(Container container, User user, int announcementRowId, String discussionSrcIdentifier);

    @Nullable
    default String getEmailSubject(Container container, User user, int announcementRowId, String discussionSrcIdentifier, String body, String title, String parentBody)
    {
        return null;
    };


}
