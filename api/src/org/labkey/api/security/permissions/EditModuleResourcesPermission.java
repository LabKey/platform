package org.labkey.api.security.permissions;

public class EditModuleResourcesPermission extends AbstractSitePermission
{
    public EditModuleResourcesPermission()
    {
        super("Edit Module", "Allow editing module resources in server while running in 'development mode'.");
    }
}