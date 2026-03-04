package org.labkey.api.security.roles;

import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;

/**
 * See {@link CanSeeAuditLogRole} for the site role version
 */
public class CanSeeAuditLogFolderRole extends AbstractRole
{
    protected CanSeeAuditLogFolderRole()
    {
        super("See Audit Log Events", "Allows non-administrators to view audit log events. " + CanSeeAuditLogRole.FINAL_WARNING_LINE,
            CanSeeAuditLogPermission.class
        );
    }
}
