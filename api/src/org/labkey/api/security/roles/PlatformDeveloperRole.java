package org.labkey.api.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AnalystPermission;
import org.labkey.api.security.permissions.BrowserDeveloperPermission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.TrustedPermission;

/**
 * Created by davebradlee on 7/23/18.
 */
public class PlatformDeveloperRole extends AbstractRootContainerRole
{
    public PlatformDeveloperRole()
    {
        super("Platform Developer", "Allows developers to write and deploy code outside the LabKey security framework.",
                PlatformDeveloperPermission.class,
                AnalystPermission.class,
                BrowserDeveloperPermission.class,
                TrustedPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}
