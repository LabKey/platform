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
package org.labkey.api.security;

import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ClonedUser extends User
{
    protected ClonedUser(User user, PermissionsContext ctx)
    {
        this(user.getEmail(), user.getUserId(), user.getFriendlyName(), user.getFirstName(), user.getLastName(), user.isActive(), user.getLastLogin(), user.getPhone(), user.getLastActivity(), ctx);
    }

    protected ClonedUser(String email, int userId, String displayName, String firstName, String lastName, boolean active,
                         Date lastLogin, String phone, Date lastActivity, PermissionsContext ctx)
    {
        super(email, userId);
        setDisplayName(displayName);
        setFirstName(firstName);
        setLastName(lastName);
        setActive(active);
        setLastLogin(lastLogin);
        setPhone(phone);
        setLastActivity(lastActivity);

        setImpersonationContext(ctx);
    }

    // Map a stream of role classes to a set of roles
    private static Set<Role> getRoles(Stream<Class<? extends Role>> roleClassStream)
    {
        return roleClassStream
            .filter(Objects::nonNull)
            .map(RoleManager::getRole)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    // Map a collection of role classes to a set of roles
    protected static Set<Role> getRoles(Collection<Class<? extends Role>> roleClassCollection)
    {
        return getRoles(roleClassCollection.stream());
    }

    // Map an array of role classes to a set of roles
    protected static Set<Role> getRoles(Class<? extends Role>[] roleClassArray)
    {
        return getRoles(Arrays.stream(roleClassArray));
    }
}
