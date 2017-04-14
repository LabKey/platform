package org.labkey.api.security.permissions;

/**
 * Describes the ability to manage users (create, delete, deactivate) for the server.
 */
public class UserManagementPermission extends AdminPermission
{
    public UserManagementPermission()
    {
        super("User Management", "Allows a role to manage users (create, delete, deactivate) for the server");
    }
}
