package org.labkey.api.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AnalystPermission;

public class AnalystRole extends AbstractRootContainerRole
{
    public AnalystRole()
    {
        super("Analyst",
                "Can write code that runs on the server. Code may be shared with other users, but users will be prompted to ‘trust’ the author before code is allowed to run.",
                AnalystPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}