/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

/**
 * Record of a particular principal assigned a particular role for a particular {@link SecurableResource}
 * User: Dave
 * Date: Apr 22, 2009
 */
public class RoleAssignment implements Comparable<RoleAssignment>, Cloneable
{
    private String _resourceId;
    private int _userId;
    private Role _role;

    public RoleAssignment()
    {
    }

    public RoleAssignment(String resourceId, UserPrincipal user, Role role)
    {
        _resourceId = resourceId;
        _role = role;
        _userId = user.getUserId();
    }

    public RoleAssignment(RoleAssignment copy)
    {
        _resourceId = copy._resourceId;
        _userId = copy._userId;
        _role = copy._role;
    }

    public String getResourceId()
    {
        return _resourceId;
    }

    public void setResourceId(String resourceId)
    {
        _resourceId = resourceId;
    }

    public int getUserId()
    {
        return _userId;
    }

    public void setUserId(int userId)
    {
        _userId = userId;
    }

    public Role getRole()
    {
        return _role;
    }

    public void setRole(Role role)
    {
        _role = role;
    }

    public int compareTo(RoleAssignment other)
    {
        if(null == other)
            throw new NullPointerException();

        int ret = 0;
        //sort by resource id, then user id, then role unique name
        //FIX: 10023 -- the resource ids should never be null, but some modules
        //seem to use null resource ids on occasion (esp. reports and pipe roots)
        //let's be defensive here and handle null resource ids
        if (null == _resourceId)
            ret = (null == other._resourceId ? 0 : -1);
        else
            ret = _resourceId.compareTo(other._resourceId);

        if (0 == ret)
            ret = _userId - other._userId;
        if (0 == ret) // CONSIDER _role extends Comparable
            ret = _role.getUniqueName().compareTo(other._role.getUniqueName());
        return ret;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RoleAssignment that = (RoleAssignment) o;

        if (_userId != that._userId) return false;
        if (!_resourceId.equals(that._resourceId)) return false;
        if (!_role.equals(that._role)) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = null == _resourceId ? 0 : _resourceId.hashCode();
        result = 31 * result + _userId;
        result = 31 * result + (null == _role ? 0 : _role.hashCode());
        return result;
    }
}