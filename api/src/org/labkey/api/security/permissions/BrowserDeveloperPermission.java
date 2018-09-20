package org.labkey.api.security.permissions;

public class BrowserDeveloperPermission extends AbstractSitePermission
{
    public BrowserDeveloperPermission()
    {
        super("Browser Developer", "Can write and deploy javascript code that runs in the browser.");
    }
}
