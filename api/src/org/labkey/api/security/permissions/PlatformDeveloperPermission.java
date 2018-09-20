package org.labkey.api.security.permissions;

public class PlatformDeveloperPermission extends AbstractSitePermission
{
    public PlatformDeveloperPermission()
    {
        super("Platform Developer", "Can write and deploy code that runs outside of the labkey security framework.");
    }
}
