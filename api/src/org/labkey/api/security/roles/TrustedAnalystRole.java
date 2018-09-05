package org.labkey.api.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AnalystPermission;
import org.labkey.api.security.permissions.TrustedPermission;

public class TrustedAnalystRole extends AbstractRootContainerRole
{
    public TrustedAnalystRole()
    {
        super("Trusted Analyst", "Can write code that runs on the server in a sandbox, code may be shared with other users and is presumed to be trusted.",
                AnalystPermission.class,
                TrustedPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}