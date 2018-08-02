package org.labkey.api.security.permissions;

public class TrustedBrowserDeveloperPermission extends AbstractPermission
{
    public TrustedBrowserDeveloperPermission()
    {
        super("Trusted Browser Developer", "Can write code that runs in the browser.  Code may be shared with other users and is presumed to be trusted.");
    }
}
