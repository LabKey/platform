package org.labkey.api.security.permissions;

public class ExemptFromAccountDisablingPermission extends AbstractSitePermission
{
    public ExemptFromAccountDisablingPermission()
    {
        super("Exempt From Account Disabling", "Prevents the compliance maintenance task from disabling users due to account expiration and inactivity");
    }
}
