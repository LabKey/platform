package org.labkey.api.security.roles;


import org.labkey.api.security.permissions.PublicHealthReportingPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class PublicHealthReportingRole extends AbstractArgosRole
{
    public PublicHealthReportingRole()
    {
        super("Public Health Reporting Selector", "May select the Public Health Reporting role in the Argos application.",
                PublicHealthReportingPermission.class);
    }
}