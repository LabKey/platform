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
package org.labkey.api.security.impersonation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.Set;

/**
 * Used for when a user is not impersonating another user. That is, they are logged in normally, and operating
 * as themselves.
 *
 * User: adam
 * Date: 11/9/11
 * Time: 5:18 AM
 */
public class NotImpersonatingContext implements ImpersonationContext
{
    @Override
    public boolean isImpersonating()
    {
        return false;
    }

    @Override
    public @Nullable Container getImpersonationProject()
    {
        return null;
    }

    @Override
    public boolean isAllowedGlobalRoles()
    {
        return true;
    }

    @Override
    public User getAdminUser()
    {
        return null;
    }

    @Override
    public String getNavTreeCacheKey()
    {
        return "";
    }

    @Override
    public URLHelper getReturnURL()
    {
        return null;
    }

    @Override
    public ImpersonationContextFactory getFactory()
    {
        return null;
    }

    @Override
    public int[] getGroups(User user)
    {
        return GroupManager.getAllGroupsForPrincipal(user);
    }

    @Override
    public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
    {
        return user.getStandardContextualRoles();
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        ImpersonateUserContextFactory.addMenu(menu);
        ImpersonateGroupContextFactory.addMenu(menu);
        ImpersonateRoleContextFactory.addMenu(menu);
    }
}
