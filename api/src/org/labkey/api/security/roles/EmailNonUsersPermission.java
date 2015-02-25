package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * User: jeckels
 * Date: 2/24/2015
 */
public class EmailNonUsersPermission extends AbstractPermission
{
    public EmailNonUsersPermission()
    {
        super("Email Non-Users", "Allows users to send emails to addresses that are not associated with LabKey Server accounts.");
    }
}
