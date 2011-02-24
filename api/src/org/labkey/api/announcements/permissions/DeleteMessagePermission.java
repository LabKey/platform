package org.labkey.api.announcements.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Dec 27, 2010
 * Time: 2:33:09 PM
 */

/**
 * A Permission that allows users to delete messages. This should be used
 * in cases where it is desired that a user explicitly be allowed to delete
 * a message.
 */
public class DeleteMessagePermission extends AbstractPermission
{
    public DeleteMessagePermission()
    {
        super("Delete Message", "Users may delete messages.");
    }
}
