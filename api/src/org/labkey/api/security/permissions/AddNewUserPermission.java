package org.labkey.api.security.permissions;

public class AddNewUserPermission extends AdminPermission
{
    public AddNewUserPermission()
    {
        super("Add New User", "Allows a role to create new users for the server");
    }
}
