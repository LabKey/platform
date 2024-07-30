package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.CreateProjectPermission;

public class ProjectCreatorRole extends AbstractRootContainerRole
{
    public ProjectCreatorRole()
    {
        super
        (
            "Project Creator",
            "Allows users to create new projects and grant themselves the Project Administrator role after creation via the CreateProject API",
            CreateProjectPermission.class
        );

        excludeUsers();
    }
}
