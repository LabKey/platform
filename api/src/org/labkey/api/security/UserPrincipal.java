/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.security.roles.Role;

import java.security.Principal;
import java.io.Serializable;
import java.util.Set;

/**
 * A user-oriented principal within the system. This encompasses both {@link User}s and {@link Group}s (of groups and users).
 *
 * User: matthewb
 * Date: Sep 20, 2006
 */
public abstract class UserPrincipal implements Principal, Parameter.JdbcParameterValue, Serializable
{
    private String _name;
    private int _userId = 0;
    private PrincipalType _principalType;

    protected UserPrincipal(PrincipalType type)
    {
        _principalType = type;
    }

    protected UserPrincipal(String name, int id, PrincipalType type)
    {
        this(type);
        _name = name;
        _userId = id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public int getUserId()
    {
        return _userId;
    }

    public void setUserId(int userId)
    {
        _userId = userId;
    }

    public boolean isGuest()
    {
        return false;
    }

    public PrincipalType getPrincipalType()
    {
        return _principalType;
    }

    public void setPrincipalType(PrincipalType principalType)
    {
        _principalType = principalType;
    }

    public String getType()
    {
        return String.valueOf(_principalType.getTypeChar());
    }

    public void setType(String type)
    {
        if (null == type || type.length() != 1 || null == (_principalType = PrincipalType.forChar(type.charAt(0))))
            throw new IllegalArgumentException("Unrecognized type specified. Must be one of 'u', 'g', 'm', or 's'.");
    }

    public abstract int[] getGroups();

    /**
     * @return the roles that the user is assumed to embody, which may go beyond the basic set of roles that the
     * user might have otherwise. For example, a user in an {@link org.labkey.api.security.roles.AuthorRole} might
     * be allowed to edit an object that they created, even though they aren't in an {@link org.labkey.api.security.roles.EditorRole}
     * and therefore don't have {@link org.labkey.api.security.permissions.UpdatePermission} to make edits more generally.
     */
    public abstract Set<Role> getContextualRoles(SecurityPolicy policy);

    public abstract boolean isInGroup(int group);


    @Override
    public String toString()
    {
        return getName();
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserPrincipal principal = (UserPrincipal) o;

        if (getUserId() != principal.getUserId()) return false;

        return true;
    }


    @Override
    public int hashCode()
    {
        return getUserId();
    }

    @Nullable
    @Override
    public Object getJdbcParameterValue()
    {
        return getUserId();
    }

    @NotNull
    @Override
    public JdbcType getJdbcParameterType()
    {
        return JdbcType.INTEGER;
    }
    public abstract boolean isActive();
}