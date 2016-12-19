package org.labkey.announcements.model;

import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.AnnouncementModule;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.AbstractPermission;

/**
 * A Permission that allows users to insert, delete, and edit their own messages and respond to other's messages.
 */
public class InsertMessagePermission extends AbstractPermission
{

    public InsertMessagePermission()
    {
        super("Insert Message", "Users may insert, delete, and edit their own messages and respond to other's messages.");
    }
}
