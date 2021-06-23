package org.labkey.api.announcements.api;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

public interface DiscussionSrcTypeProvider
{
    ActionURL getThreadURL(Container container, User user, int announcementRowId, String discussionSrcIdentifier);
}
