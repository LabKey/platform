package org.labkey.api.security.permissions;

public class DeleteUserPermission extends AdminPermission
{
    public DeleteUserPermission()
    {
        super("Delete a User", "Allows a role to delete users from the server");
    }
}