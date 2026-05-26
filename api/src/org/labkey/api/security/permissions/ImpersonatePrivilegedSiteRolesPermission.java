package org.labkey.api.security.permissions;

public class ImpersonatePrivilegedSiteRolesPermission extends AbstractPermission
{
    public ImpersonatePrivilegedSiteRolesPermission()
    {
        super("Can Impersonate Privileged Site Roles", "Allows users to impersonate privileged site roles including Site Admin");
    }
}
