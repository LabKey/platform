package org.labkey.api.security.roles;

import org.labkey.api.data.FullPHIPermission;
import org.labkey.api.data.LimitedPHIPermission;

/**
 * Created by Marty on 12/17/2014.
 */
public class FullPHIRole extends AbstractArgosRole
{
    public FullPHIRole()
    {
        super("Full PHI Reader", "May chose to read columns tagged as Full PHI in the Argos application.",
                LimitedPHIPermission.class,
                FullPHIPermission.class);
    }
}
