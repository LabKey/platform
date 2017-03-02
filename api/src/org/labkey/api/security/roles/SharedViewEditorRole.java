package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * Created by Josh on 3/1/2017.
 */
public class SharedViewEditorRole extends AbstractRole
{
    protected SharedViewEditorRole()
    {
        super("Shared view editor", "Shared view editors may create and update shared custom views",
                ReadPermission.class,
                EditSharedViewPermission.class);
    }
}
