/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security.roles;

import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.UserManagementPermission;
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

/**
 * A step down from site admins, app admins have broad access but don't get to control native resources on the server.
 */
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
                UserManagementPermission.class,
                AdminReadPermission.class,
                AdminPermission.class,
                EditSharedViewPermission.class,
                SeeUserEmailAddressesPermission.class,
                EnableRestrictedModules.class,
                CanSeeAuditLogPermission.class,
                FolderExportPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
        addExcludedPrincipal(SecurityManager.getGroup(Group.groupUsers));
    }
}
