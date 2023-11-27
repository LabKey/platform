/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.CanImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.permissions.CanImpersonateSiteRolesPermission;
import org.labkey.api.security.permissions.CanUseSendMessageApiPermission;
import org.labkey.api.security.permissions.EditModuleResourcesPermission;
import org.labkey.api.security.permissions.ExemptFromAccountDisablingPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.security.permissions.UploadFileBasedModulePermission;
import org.labkey.api.settings.AppProps;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * The Site Administrator role. Site admins are equivalent to root in *nix - they can do anything.
 */
public class SiteAdminRole extends AbstractRootContainerRole implements AdminRoleListener
{
    private static final Collection<Class<? extends Permission>> PERMISSIONS = Arrays.asList(
        AdminOperationsPermission.class,
        CanImpersonatePrivilegedSiteRolesPermission.class,
        CanImpersonateSiteRolesPermission.class,
        CanUseSendMessageApiPermission.class,
        EmailNonUsersPermission.class,
        ExemptFromAccountDisablingPermission.class,
        SiteAdminPermission.class,
        UploadFileBasedModulePermission.class
    );

    public SiteAdminRole()
    {
        super("Site Administrator", "Site Administrators have full control over the entire system.",
            FolderAdminRole.PERMISSIONS,
            ApplicationAdminRole.PERMISSIONS,
            PlatformDeveloperRole.PERMISSIONS,
            PERMISSIONS,
            AppProps.getInstance().isDevMode() ? Collections.singleton(EditModuleResourcesPermission.class) : Collections.emptyList()
        );
    }

    @Override
    public boolean isPrivileged()
    {
        return true;
    }

    @Override
    public void permissionRegistered(Class<? extends Permission> perm)
    {
        addPermission(perm);
    }
}