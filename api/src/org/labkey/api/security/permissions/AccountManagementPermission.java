package org.labkey.api.security.permissions;

/**
 * Describes the ability to manage accounts (create, delete, deactivate) for the server.
 */
public class AccountManagementPermission extends AdminPermission
{
    public AccountManagementPermission()
    {
        super("Account Management", "Users may manage accounts (create, delete, deactivate) for the server");
    }
}
