package org.labkey.api.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.TrustedAnalystPermission;
import org.labkey.api.security.permissions.TrustedBrowserDeveloperPermission;

/**
 * Created by davebradlee on 7/23/18.
 */
public class PlatformDeveloperRole extends AbstractRootContainerRole
{
    public PlatformDeveloperRole()
    {
        super("Platform Developer", "Allows developers to write and deploy code outside the LabKey security framework.",
                PlatformDeveloperPermission.class,
                TrustedAnalystPermission.class,
                TrustedBrowserDeveloperPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}
