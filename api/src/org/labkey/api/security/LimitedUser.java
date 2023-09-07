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

import org.labkey.api.security.roles.AbstractRootContainerRole;
import org.labkey.api.security.roles.Role;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A wrapper around another user that limits the permissions associated that that user, and thus
 * the operations that the user is allowed to perform.
 */
public class LimitedUser extends User
{
    private final PrincipalArray _groups;
    private final Set<Role> _roles;
    private final boolean _allowedGlobalRoles;

    // LimitedUser with no groups, only a set of explicit roles. Presence of any site role determines if global roles are allowed.
    public LimitedUser(User user, Set<Role> roles)
    {
        this(user, PrincipalArray.getEmptyPrincipalArray(), roles, roles.stream().anyMatch(r -> r instanceof AbstractRootContainerRole));
    }

    @Deprecated // Leave in place temporarily until the many uses in other repos have been converted
    public LimitedUser(User user, int[] groups, Set<Role> roles, boolean allowedGlobalRoles)
    {
        this(user, new PrincipalArray(Arrays.stream(groups).boxed().toList()), roles, allowedGlobalRoles);
    }

    public LimitedUser(User user, PrincipalArray groups, Set<Role> roles, boolean allowedGlobalRoles)
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
        _allowedGlobalRoles = allowedGlobalRoles;
    }

    @Override
    public boolean isAllowedGlobalRoles()
    {
        return _allowedGlobalRoles;
    }

    @Override
    public PrincipalArray getGroups()
    {
        return _groups;
    }

    @Override
    public Set<Role> getContextualRoles(SecurityPolicy policy)
    {
        return new HashSet<>(_roles);
    }
}
