package org.labkey.api.security.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class ResearchInformedConsentPermission extends AbstractPermission
{
    public ResearchInformedConsentPermission()
    {
        super("Research with Informed Consent", "May perform research with informed consent operations.");
    }
}

