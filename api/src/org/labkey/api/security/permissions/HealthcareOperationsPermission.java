package org.labkey.api.security.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class HealthcareOperationsPermission extends AbstractPermission
{
    public HealthcareOperationsPermission()
    {
        super("Healthcare Operations", "May perform healthcare operations.");
    }
}
