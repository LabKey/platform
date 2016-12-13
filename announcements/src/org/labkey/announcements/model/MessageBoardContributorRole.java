package org.labkey.announcements.model;

import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AbstractRole;

public class MessageBoardContributorRole extends AbstractRole
{
    public MessageBoardContributorRole()
    {
        super("Message Board Contributor", "Allows user to insert messages and manage them.",
                InsertMessagePermission.class, ReadPermission.class);
    }
}