package org.labkey.api.security.roles;

import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AccountManagementPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.EnableRestrictedModules;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.SeeUserEmailAddressesPermission;
import org.labkey.api.security.permissions.UpdatePermission;

public class ApplicationAdminRole extends AbstractRootContainerRole
{
    public ApplicationAdminRole()
    {
        super("Application Admin", "Application Administrators have control over non-operational administration settings.",
                //" By default, Application Administrators have admin permissions to all projects/folders as well, see User.getStandardContextualRoles().",
                ReadPermission.class,
                ReadSomePermission.class,
                UpdatePermission.class,
                InsertPermission.class,
                DeletePermission.class,
                AccountManagementPermission.class,
                AdminReadPermission.class,
                AdminPermission.class,
                EditSharedViewPermission.class,
                SeeUserEmailAddressesPermission.class,
                EmailNonUsersPermission.class,
                EnableRestrictedModules.class,
                CanSeeAuditLogPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupUsers));
    }
}
