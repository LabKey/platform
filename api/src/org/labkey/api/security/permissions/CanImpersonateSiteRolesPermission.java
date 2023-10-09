package org.labkey.api.security.permissions;

public class CanImpersonateSiteRolesPermission extends AbstractPermission
{
    public CanImpersonateSiteRolesPermission()
    {
        super("Can Impersonate Site Roles", "Allows users to impersonate site roles including Site Admin");
    }
}
