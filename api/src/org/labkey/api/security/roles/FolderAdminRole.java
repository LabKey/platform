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

import org.labkey.api.admin.FolderExportPermission;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DesignDataClassPermission;
import org.labkey.api.security.permissions.DesignSampleTypePermission;
import org.labkey.api.security.permissions.Permission;

import java.util.Arrays;
import java.util.Collection;

public class FolderAdminRole extends AbstractRole implements AdminRoleListener
{
    // Most permissions are assigned to all admin roles automatically, and shouldn't be added to this list
    static Collection<Class<? extends Permission>> PERMISSIONS = Arrays.asList(
        AdminPermission.class,
        DesignDataClassPermission.class,
        DesignSampleTypePermission.class,
        FolderExportPermission.class
    );

    public FolderAdminRole()
    {
        super("Folder Administrator",
            "Folder Administrators have full control over their particular folder, but not others.",
            PERMISSIONS
        );

        excludeGuests();
        excludeUsers();
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && !((Container)resource).isRoot();
    }

    @Override
    public void permissionRegistered(Class<? extends Permission> perm)
    {
        addPermission(perm);
    }
}