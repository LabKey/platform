package org.labkey.api.security.permissions;

public class TrustedPermission extends AbstractPermission
{
    public TrustedPermission()
    {
        super("Trusted", "Code written by these users may be shared (executed by others) without prompting");
    }
}
