package org.labkey.api.reports.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Mar 19, 2012
 */
public class ShareReportPermission extends AbstractPermission
{
    public ShareReportPermission()
    {
        super("Share Report", "Allows sharing a report for others to view/edit.");
    }
}
