package org.labkey.api.announcements.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Dec 27, 2010
 * Time: 2:32:32 PM
 */

/**
 * A Permission that allows users to update messages. This should be used
 * in cases where it is desired that a user explicitly be allowed to update
 * a message.
 */
public class UpdateMessagePermission extends AbstractPermission
{
    public UpdateMessagePermission()
    {
        super("Update Message", "Users may update messages.");
    }
}
