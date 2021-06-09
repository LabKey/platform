package org.labkey.api.lists.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

public class ManagePicklistsPermission extends AbstractPermission
{
    public ManagePicklistsPermission()
    {
        super("Manage Picklists",
                "May create, update, and delete sample picklists.");
    }
}
