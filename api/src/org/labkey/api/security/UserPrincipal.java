/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.security.roles.Role;

import java.io.Serializable;
import java.security.Principal;
import java.util.stream.Stream;

/**
 * A user-oriented principal within the system. This encompasses both {@link User}s and {@link Group}s (of groups and users).
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

    @Override
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

    public abstract PrincipalArray getGroups();

    /**
     * @return the roles assigned to this principal in the provided securable resource
     */
    public abstract Stream<Role> getAssignedRoles(SecurableResource resource);

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

        return getUserId() == principal.getUserId();
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

    public boolean hasPrivilegedRole()
    {
        // Check for any privileged role assigned to this principal at the root
        return ContainerManager.getRoot().getPolicy().getRoles(getGroups()).stream().anyMatch(Role::isPrivileged);
    }
}