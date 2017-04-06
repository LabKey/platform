package org.labkey.api.security.permissions;

/**
 * Describes the ability to manage operational site administration settings.
 */
public class AdminOperationsPermission extends AdminPermission
{
    public AdminOperationsPermission()
    {
        super("Operational Settings Administration", "Users may manage operational administrative information");
    }
}
