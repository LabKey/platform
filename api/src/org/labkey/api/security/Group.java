/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.roles.Role;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A group may be scoped to either the entire server or a specific project. It is a collection of users and/or
 * nested groups.
 * User: arauch
 * Date: Jul 5, 2005
 */
public class Group extends UserPrincipal
{
    public static final int groupAdministrators = -1;
    public static final int groupUsers = -2;
    public static final int groupGuests = -3;
    public static final int groupDevelopers = -4;

    private String _ownerId;
    private String _container;

    public Group()
    {
        this(PrincipalType.GROUP);
    }

    public Group(PrincipalType type)
    {
        super(type);
    }

    public String getOwnerId()
    {
        return _ownerId;
    }

    public void setOwnerId(String ownerId)
    {
        _ownerId = ownerId;
    }

    public boolean isAdministrators()
    {
        return getUserId() == groupAdministrators;
    }

    public boolean isGuests()
    {
        return getUserId() == groupGuests;
    }

    public boolean isUsers()
    {
        return getUserId() == groupUsers;
    }

    public boolean isDevelopers()
    {
        return getUserId() == groupDevelopers;
    }

    public boolean isProjectGroup()
    {
        return getContainer() != null;
    }

    public boolean isSystemGroup()
    {
        int id = getUserId();
        return (id == groupAdministrators || id == groupGuests || id == groupUsers || id == groupDevelopers);
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String containerId)
    {
        _container = containerId;
    }


    private volatile String _path = null;
    
    // @deprecated
    public String getPath()
    {
        if (null == _path)
        {
            if (null == _container)
                return "/" + getName();
            Container c = ContainerManager.getForId(_container);
            if (c == null)
                return "/" + _container + "/" + getName();
            return "/" + c.getName() + "/" + getName();
        }
        return _path;
    }

    @Override
    public int[] getGroups()
    {
        return GroupManager.getAllGroupsForPrincipal(this);
    }

    @Override
    public boolean isInGroup(int group)
    {
        int i = Arrays.binarySearch(getGroups(), group);
        return i >= 0;
    }

    @Override
    public Set<Role> getContextualRoles(SecurityPolicy policy)
    {
        return new HashSet<>();
    }

    @Override
    public boolean isActive()
    {
        return true;
    }
}
