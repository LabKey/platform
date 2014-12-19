package org.labkey.api.data;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/17/2014.
 */
public class FullPHIPermission extends AbstractPermission
{
    public FullPHIPermission()
    {
        super("Full PHI Permission", "Users may view full PHI.");
    }
}
