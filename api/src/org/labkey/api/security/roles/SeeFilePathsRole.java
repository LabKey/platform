package org.labkey.api.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.webdav.permissions.SeeFilePathsPermission;

/**
 * Created by davebradlee on 10/10/17.
 */
public class SeeFilePathsRole extends AbstractRootContainerRole
{
    public SeeFilePathsRole()
    {
        super("See Absolute File Paths", "Allows users to see absolute file paths.", SeeFilePathsPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}
