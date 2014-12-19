package org.labkey.api.data;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/17/2014.
 */
public class RestrictedPHIPermission extends AbstractPermission
{
    public RestrictedPHIPermission()
    {
        super("Limited PHI Permission", "Users may view limited PHI.");
    }
}
