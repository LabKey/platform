/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.*;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.DeveloperRole;
import org.labkey.wiki.model.Wiki;

import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;

/**
 * Enacapsulates permission testing for wikis, handling the UPDATEOWN and DELETEOWN cases
 *
 * Extend this class to do other kinds of permission checking.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Nov 7, 2007
 * Time: 4:01:58 PM
 */
public class BaseWikiPermissions
{
    private User _user;
    private SecurityPolicy _policy;

    public BaseWikiPermissions(User user, Container container)
    {
        assert(null != user && null != container);
        _user = user;
        _policy = org.labkey.api.security.SecurityManager.getPolicy(container);
    }

    protected Set<Role> getContextualRoles(Wiki wiki)
    {
        Set<Role> roles = new HashSet<Role>();
        if(userIsCreator(wiki))
            roles.add(RoleManager.getRole(OwnerRole.class));
        if(_user.isDeveloper())
            roles.add(RoleManager.getRole(DeveloperRole.class));
        return roles;
    }

    public boolean allowRead(Wiki wiki)
    {
        return _policy.hasPermission(_user, ReadPermission.class, getContextualRoles(wiki));
    }

    public boolean allowInsert()
    {
        return _policy.hasPermission(_user, InsertPermission.class);
    }

    public boolean allowUpdate(Wiki wiki)
    {
        return _policy.hasPermission(_user, UpdatePermission.class, getContextualRoles(wiki));
    }

    public boolean allowDelete(Wiki wiki)
    {
        return _policy.hasPermission(_user, DeletePermission.class, getContextualRoles(wiki));
    }

    public boolean allowAdmin()
    {
        return _policy.hasPermission(_user, AdminPermission.class);
    }

    public boolean userIsCreator(Wiki wiki)
    {
        return wiki.getCreatedBy() == _user.getUserId();
    }
}
