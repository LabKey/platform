package org.labkey.core.security;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.AbstractRootContainerRole;

public class ProjectCreatorRole extends AbstractRootContainerRole
{
    public ProjectCreatorRole()
    {
        super
        (
            "Project Creator",
            "Can create new projects via the CreateProject action and grant themselves the Project Administrator role after creation",
            CreateProjectPermission.class
        );

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupUsers));
    }
}
