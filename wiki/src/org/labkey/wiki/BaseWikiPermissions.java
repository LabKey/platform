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

package org.labkey.wiki;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.wiki.model.Wiki;

import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates permission testing for wikis, handling the UPDATEOWN and DELETEOWN cases
 * Extend this class to do other kinds of permission checking.
 */
public class BaseWikiPermissions
{
    private final User _user;
    private final Container _container;

    public BaseWikiPermissions(User user, Container container)
    {
        assert(null != user && null != container);
        _user = user;
        _container = container;
    }

    protected Set<Role> getContextualRoles(Wiki wiki)
    {
        Set<Role> roles = new HashSet<>();
        if (userIsCreator(wiki) && allowRead() && allowInsert()) //31077
            roles.add(RoleManager.getRole(OwnerRole.class));
        return roles;
    }

    public boolean allowRead()
    {
        return _container.hasPermission("wiki", _user, ReadPermission.class);
    }

    public boolean allowRead(Wiki wiki)
    {
        return _container.hasPermission("wiki: " + wiki.getName(), _user, ReadPermission.class, getContextualRoles(wiki));
    }

    public boolean allowInsert()
    {
        return _container.hasPermission("wiki", _user, InsertPermission.class);
    }

    public boolean allowUpdate(Wiki wiki)
    {
        return _container.hasPermission("wiki: " + wiki.getName(), _user, UpdatePermission.class, getContextualRoles(wiki));
    }

    public boolean allowDelete(Wiki wiki)
    {
        return _container.hasPermission("wiki: " + wiki.getName(), _user, DeletePermission.class, getContextualRoles(wiki));
    }

    public boolean allowAdmin()
    {
        return _container.hasPermission("wiki", _user, AdminPermission.class);
    }

    public boolean userIsCreator(Wiki wiki)
    {
        // Guest is never considered a wiki "creator", #28955
        return !_user.isGuest() && wiki.getCreatedBy() == _user.getUserId();
    }
}
