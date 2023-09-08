/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.security.impersonation;

/*
  A "not impersonating" context that disallows global roles (i.e., Site Admin and Developer)
 */

import org.labkey.api.security.Group;
import org.labkey.api.security.PrincipalArray;
import org.labkey.api.security.User;

import java.util.Set;

public class DisallowGlobalRolesContext extends NotImpersonatingContext
{
    private static final DisallowGlobalRolesContext INSTANCE = new DisallowGlobalRolesContext();

    public static DisallowGlobalRolesContext get()
    {
        return INSTANCE;
    }

    private DisallowGlobalRolesContext()
    {
    }

    @Override
    public boolean isAllowedGlobalRoles()
    {
        return false;
    }

    @Override
    public String getCacheKey()
    {
        return "DisallowGlobalRoles";
    }

    private static final Set<Integer> FORBIDDEN_ROLES = Set.of(Group.groupAdministrators, Group.groupDevelopers);

    @Override
    public PrincipalArray getGroups(User user)
    {
        return new PrincipalArray(super.getGroups(user).stream()
            .filter(id -> !FORBIDDEN_ROLES.contains(id))
            .toList());
    }
}
