package org.labkey.api.security.roles;


import org.labkey.api.security.permissions.HealthcareOperationsPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class HealthcareRole extends AbstractArgosRole
{
    public HealthcareRole()
    {
        super("Healthcare Operations Selector", "May select the Healthcare Operations role in the Argos application.",
                HealthcareOperationsPermission.class);
    }
}