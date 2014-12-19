package org.labkey.api.security.roles;


import org.labkey.api.security.permissions.ResearchInformedConsentPermission;
import org.labkey.api.security.permissions.ResearchOperationsPermission;
import org.labkey.api.security.permissions.ResearchWaiverConsentPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class ResearchRole extends AbstractArgosRole
{
    public ResearchRole()
    {
        super("Research Selector", "May select the Research Operations, Research with Waiver of Consent and " +
                        "Research with Informed Consent role in the Argos application.",
                ResearchOperationsPermission.class,
                ResearchWaiverConsentPermission.class,
                ResearchInformedConsentPermission.class);
    }
}