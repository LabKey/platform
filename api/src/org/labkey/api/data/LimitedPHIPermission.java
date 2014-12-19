package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/17/2014.
 */
public class LimitedPHIPermission extends AbstractPermission
{
    public LimitedPHIPermission()
    {
        super("Limited PHI Permission", "Users may view limited PHI.");
    }
}
