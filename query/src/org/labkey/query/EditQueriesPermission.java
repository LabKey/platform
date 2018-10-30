package org.labkey.query;

import org.labkey.api.security.permissions.AbstractPermission;

public class EditQueriesPermission extends AbstractPermission
{
    protected EditQueriesPermission()
    {
        super("Edit Queries", "My create, edit, and delete queries");
    }
}
