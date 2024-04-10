package org.labkey.api.security.permissions;

public class RequireSecondaryAuthenticationPermission extends AbstractSitePermission
{
    public RequireSecondaryAuthenticationPermission()
    {
        super("Require Secondary Authentication", "Must authenticate via secondary authentication (TOTP, Duo) if a configuration is enabled.");
    }
}
