package org.labkey.api.security.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class ResearchWaiverConsentPermission extends AbstractPermission
{
    public ResearchWaiverConsentPermission()
    {
        super("Research with Waiver of Consent", "May perform research with waiver of consent operations.");
    }
}