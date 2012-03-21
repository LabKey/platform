package org.labkey.api.reports.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 19, 2012
 */
public class EditSharedReportPermission extends AbstractPermission
{
    public EditSharedReportPermission()
    {
        super("Edit Shared Report", "Allows editing of a shared report.");
    }
}
