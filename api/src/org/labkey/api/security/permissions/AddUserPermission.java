package org.labkey.api.security.permissions;

public class AddUserPermission extends AdminPermission
{
    public AddUserPermission()
    {
        super("Add New User", "Allows a role to create new users for the server");
    }
}
