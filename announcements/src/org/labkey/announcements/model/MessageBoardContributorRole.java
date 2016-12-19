package org.labkey.announcements.model;

import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AbstractRole;

public class MessageBoardContributorRole extends AbstractRole
{
    public MessageBoardContributorRole()
    {
        super("Message Board Contributor", "Allows user to insert, delete, and edit their own messages and respond to other's messages.",
                InsertMessagePermission.class, ReadPermission.class);
    }
}