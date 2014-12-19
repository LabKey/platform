package org.labkey.api.security.roles;

import org.labkey.api.data.LimitedPHIPermission;

/**
 * Created by Marty on 12/17/2014.
 */
public class LimitedPHIRole extends AbstractArgosRole
{
    public LimitedPHIRole()
    {
        super("Limited PHI Reader", "May chose to read columns tagged as Limited PHI in the Argos application.",
               LimitedPHIPermission.class);
    }
}
