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

import org.labkey.api.security.roles.Role;

import java.util.HashSet;
import java.util.Set;

/**
 * A wrapper around another user that limits the permissions associated that that user, and thus
 * the operations that the user is allowed to perform.
 * User: adam
 * Date: Sep 10, 2011
 */
public class LimitedUser extends User
{
    private final int[] _groups;
    private final Set<Role> _roles;
    private final boolean _allowedGlobalRoles;

    public LimitedUser(User user, int[] groups, Set<Role> roles, boolean allowedGlobalRoles)
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
    public int[] getGroups()
    {
        return _groups;
    }

    @Override
    public Set<Role> getContextualRoles(SecurityPolicy policy)
    {
        return new HashSet<>(_roles);
    }
}
