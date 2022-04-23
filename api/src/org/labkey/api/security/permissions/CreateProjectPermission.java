package org.labkey.api.security.permissions;

public class CreateProjectPermission extends AbstractPermission
{
    public CreateProjectPermission()
    {
        super("Create projects", "Users are able to create projects and grant themselves the Project Administrator role after creation");
    }
}
