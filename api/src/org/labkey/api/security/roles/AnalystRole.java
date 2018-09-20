package org.labkey.api.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AnalystPermission;
import org.labkey.api.security.permissions.BrowserDeveloperPermission;

public class AnalystRole extends AbstractRootContainerRole
{
    public AnalystRole()
    {
        super("Analyst",
                "Can write code that runs on the server, but may not share code (e.g. may use rstudio if configured)",
                AnalystPermission.class,
                BrowserDeveloperPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}