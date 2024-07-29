/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.AddUserPermission;
import org.labkey.api.security.permissions.Permission;

import java.util.Collections;

public class ProjectAdminRole extends AbstractRole implements AdminRoleListener
{
    public ProjectAdminRole()
    {
        super("Project Administrator",
            "Project Administrators have full control over the project, but not the entire system.",
            FolderAdminRole.PERMISSIONS,
            Collections.singletonList(AddUserPermission.class)
        );

        excludeGuests();
        excludeUsers();
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && ((Container)resource).isProject();
    }

    @Override
    public void permissionRegistered(Class<? extends Permission> perm)
    {
        addPermission(perm);
    }
}