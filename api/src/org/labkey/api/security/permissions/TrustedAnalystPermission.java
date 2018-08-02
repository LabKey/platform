package org.labkey.api.security.permissions;

public class TrustedAnalystPermission extends AbstractPermission
{
    public TrustedAnalystPermission()
    {
        super("Trusted Analyst", "Can write code that runs on the server in a sandbox.");
    }
}
