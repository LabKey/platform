package org.labkey.api.security.permissions;

public class ImpersonatePermission extends AbstractPermission
{
    public ImpersonatePermission()
    {
        super("Can Impersonate", "Allows users to impersonate users, groups, and roles, except for privileged roles");
    }
}
