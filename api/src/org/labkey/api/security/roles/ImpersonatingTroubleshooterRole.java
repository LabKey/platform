package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.CanImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.permissions.CanImpersonateSiteRolesPermission;
import org.labkey.api.security.permissions.ExemptFromAccountDisablingPermission;

import java.util.Set;

public class ImpersonatingTroubleshooterRole extends AbstractRootContainerRole
{
    protected ImpersonatingTroubleshooterRole()
    {
        super("Impersonating Troubleshooter", "Can impersonate site roles, including Site Administrator, in addition to having other standard Troubleshooter abilities.",
            TroubleshooterRole.PERMISSIONS,
            Set.of(
                CanImpersonatePrivilegedSiteRolesPermission.class,
                CanImpersonateSiteRolesPermission.class,
                ExemptFromAccountDisablingPermission.class
            )
        );
        excludeUsers();
    }

    @Override
    public boolean isPrivileged()
    {
        return true;
    }
}
