/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.data.Container;
import org.labkey.api.security.permissions.AllowedForReadOnlyUser;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.stream.Stream;

public class ReadOnlyPermissionsContext extends NormalPermissionsContext
{
    @Override
    public Stream<Class<? extends Permission>> filterPermissions(Stream<Class<? extends Permission>> perms)
    {
        return perms
            .filter(p -> null != p.getAnnotation(AllowedForReadOnlyUser.class));
    }

    @Override
    public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
    {
        return super.getAssignedRoles(user, resource).filter(role -> !role.isPrivileged());
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
    }
}
