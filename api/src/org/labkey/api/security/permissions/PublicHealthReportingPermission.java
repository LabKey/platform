package org.labkey.api.security.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class PublicHealthReportingPermission extends AbstractPermission
{
    public PublicHealthReportingPermission()
    {
        super("Public Health Reporting", "May perform public health reporting operations.");
    }
}
