/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.labkey.api.audit.permissions.CanSeeAuditLogPermission;
import org.labkey.api.data.Container;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A wrapper around another user that limits the permissions associated that that user, and thus
 * the operations that the user is allowed to perform.
 */
public class LimitedUser extends User
{
    private final PrincipalArray _groups;
    private final Set<Role> _roles;

    // LimitedUser that's granted one or more roles (no groups)
    @SafeVarargs
    public LimitedUser(User user, Class<? extends Role>... roleClasses)
    {
        this(user, PrincipalArray.getEmptyPrincipalArray(), Arrays.stream(roleClasses).map(RoleManager::getRole).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    @Deprecated // Delete
    public LimitedUser(User user, PrincipalArray groups, Set<Role> roles, boolean ignored)
    {
        this(user, groups, roles);
    }

    public LimitedUser(User user, PrincipalArray groups, Set<Role> roles)
    {
        super(user.getEmail(), user.getUserId());
        setFirstName(user.getFirstName());
        setLastName(user.getLastName());
        setActive(user.isActive());
        setDisplayName(user.getFriendlyName());
        setLastLogin(user.getLastLogin());
        setPhone(user.getPhone());
        _groups = groups;
        _roles = roles;
    }

    @Override
    public PrincipalArray getGroups()
    {
        return _groups;
    }

    @Override
    public Set<Role> getAssignedRoles(SecurityPolicy policy)
    {
        return new HashSet<>(_roles);
    }

    /**
     * Conditionally add roles to the supplied user. For each permission + role pair, add the role if the user doesn't
     * have the corresponding permission. I don't love using LimitedUser, but at least we're no longer implementing
     * this (incorrectly) in a ton of modules.
     */
    @SafeVarargs
    public static User getElevatedUser(Container container, User user, Pair<Class<? extends Permission>, Class<? extends Role>>... pairs)
    {
        Set<Class<? extends Role>> rolesToAdd = Arrays.stream(pairs)
            .filter(pair -> !container.hasPermission(user, pair.first))
            .map(pair -> pair.second)
            .collect(Collectors.toSet());

        return !rolesToAdd.isEmpty() ? getElevatedUser(container, user, rolesToAdd) : user;
    }

    /** Unconditionally add roles to the supplied user */
    public static User getElevatedUser(Container container, User user, Collection<Class<? extends Role>> rolesToAdd)
    {
        Set<Role> roles = new HashSet<>(user.getAssignedRoles(container.getPolicy()));
        rolesToAdd.stream()
            .map(RoleManager::getRole)
            .filter(Objects::nonNull)
            .forEach(roles::add);

        return new LimitedUser(user, user.getGroups(), roles);
    }

    public static User getCanSeeAuditLogUser(Container container, User user)
    {
        return getElevatedUser(container, user, Pair.of(CanSeeAuditLogPermission.class, CanSeeAuditLogRole.class));
    }
}
