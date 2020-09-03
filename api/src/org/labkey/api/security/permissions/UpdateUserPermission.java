package org.labkey.api.security.permissions;

public class UpdateUserPermission extends AdminPermission
{
    public UpdateUserPermission()
    {
        super("Update a User", "Allows a role to update users");
    }
}