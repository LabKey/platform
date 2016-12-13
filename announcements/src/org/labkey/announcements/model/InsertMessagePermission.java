package org.labkey.announcements.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.AnnouncementModule;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.AbstractPermission;

/**
 * A Permission that allows users to insert messages. This should be used
 * in cases where it is desired that a user explicitly be allowed to delete
 * a message.
 */public class InsertMessagePermission extends AbstractPermission
{

    public InsertMessagePermission()
    {
        super("Insert Message", "Users may insert Plain-text messages.");
    }
}
