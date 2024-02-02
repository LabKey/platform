package org.labkey.api.announcements.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.HashSet;
import java.util.Set;

public interface DiscussionSrcTypeProvider
{
    /**
     * Should the discussion service send email notifications for each added/updated comment?
     * @return true if discussion service emails are desired, false if caller is responsible for notification emails
     */
    boolean shouldSendEmailNotifications();

    @Nullable
    default ActionURL getThreadURL(Container container, User user, int announcementRowId, String discussionSrcIdentifier)
    {
        return null;
    }

    @Nullable
    default String getEmailSubject(Container container, User user, int announcementRowId, String discussionSrcIdentifier, String body, String title, String parentBody)
    {
        return null;
    };

    default Set<User> getRecipients(Container container, User user, String discussionSrcIdentifier)
    {
        return new HashSet<>();
    }

    enum Change
    {
        Insert, Update, Delete
    }

    // Called any time a discussion thread is changed (insert, update, or delete)
    default void discussionChanged(Container container, User user, String discussionSrcIdentifier, Change change, Announcement ann)
    {
    }
}
