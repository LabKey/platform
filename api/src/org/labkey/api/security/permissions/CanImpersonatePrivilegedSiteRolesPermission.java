package org.labkey.api.security.permissions;

public class CanImpersonatePrivilegedSiteRolesPermission extends AbstractPermission
{
    public CanImpersonatePrivilegedSiteRolesPermission()
    {
        super("Can Impersonate Privileged Site Roles", "Allows users to impersonate privileged site roles including Site Admin");
    }
}
