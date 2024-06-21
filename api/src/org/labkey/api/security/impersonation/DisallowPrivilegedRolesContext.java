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
  A "not impersonating" context that filters out privileged site roles (i.e., Site Admin, Platform Developer)
 */

import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;

import java.util.stream.Stream;

public class DisallowPrivilegedRolesContext extends NotImpersonatingContext
{
    private static final DisallowPrivilegedRolesContext INSTANCE = new DisallowPrivilegedRolesContext();

    public static DisallowPrivilegedRolesContext get()
    {
        return INSTANCE;
    }

    private DisallowPrivilegedRolesContext()
    {
    }

    @Override
    public String getCacheKey()
    {
        return "DisallowGlobalRoles";
    }

    @Override
    public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
    {
        return super.getAssignedRoles(user, resource).filter(role -> !role.isPrivileged());
    }
}
