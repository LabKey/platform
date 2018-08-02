package org.labkey.api.security.permissions;

public class PlatformDeveloperPermission extends AbstractPermission
{
    public PlatformDeveloperPermission()
    {
        super("Platform Developer", "Can write and deploy code that runs outside of the labkey security framework.");
    }
}
