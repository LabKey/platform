/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.labkey.api.security.permissions.ImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.permissions.ImpersonatePermission;
import org.labkey.api.security.permissions.ExemptFromAccountDisablingPermission;

import java.util.Set;

public class ImpersonatingTroubleshooterRole extends AbstractRootContainerRole
{
    protected ImpersonatingTroubleshooterRole()
    {
        super("Impersonating Troubleshooter", "Can impersonate users, groups, and roles, including Site Administrator, in addition to having other standard Troubleshooter abilities.",
            TroubleshooterRole.PERMISSIONS,
            Set.of(
                ExemptFromAccountDisablingPermission.class,
                ImpersonatePrivilegedSiteRolesPermission.class,
                ImpersonatePermission.class
            )
        );
        excludeUsers();
    }

    @Override
    public boolean isPrivileged()
    {
        return true;
    }

    @Override
    public boolean isApplicableOutsideRoot()
    {
        return false;
    }
}
