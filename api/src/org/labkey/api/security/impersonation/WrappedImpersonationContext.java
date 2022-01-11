/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.HashSet;
import java.util.Set;

/**
 * Used for when a user is not impersonating another user. That is, they are logged in normally, and operating
 * as themselves.  This class can be used to grant a user a contextual role for the duration of a request.
 * This should not be first tool to reach for.  It is usually better to find a way to provide the additional contextual
 * roles in a more limited scope.
 *
 * User: adam
 * Date: 11/9/11
 */
public class WrappedImpersonationContext implements ImpersonationContext
{
    final ImpersonationContext delegate;
    final Set<Role> additionalRoles;

    public WrappedImpersonationContext(ImpersonationContext delegate)
    {
        this.delegate = delegate;
        additionalRoles = Set.of();
    }

    public WrappedImpersonationContext(ImpersonationContext delegate, Role additionalRole)
    {
        this.delegate = delegate;
        additionalRoles = Set.of(additionalRole);
    }

    @Override
    public boolean isImpersonating()
    {
        return delegate.isImpersonating();
    }

    @Override
    public boolean isAllowedGlobalRoles()
    {
        return delegate.isAllowedGlobalRoles();
    }

    @Override
    @Nullable
    public Container getImpersonationProject()
    {
        return delegate.getImpersonationProject();
    }

    @Override
    public User getAdminUser()
    {
        return delegate.getAdminUser();
    }

    @Override
    public String getCacheKey()
    {
        return delegate.getCacheKey();
    }

    @Override
    public ActionURL getReturnURL()
    {
        return delegate.getReturnURL();
    }

    @Override
    public int[] getGroups(User user)
    {
        return delegate.getGroups(user);
    }

    @Override
    public Set<Role> getContextualRoles(User user, SecurityPolicy policy)
    {
        Set<Role> ret = new HashSet<>(additionalRoles);
        ret.addAll(delegate.getContextualRoles(user, policy));
        return ret;
    }

    @Override
    public ImpersonationContextFactory getFactory()
    {
        return delegate.getFactory();
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        delegate.addMenu(menu, c, user, currentURL);
    }

    @Override
    public Set<Class<? extends Permission>> filterPermissions(Set<Class<? extends Permission>> perms)
    {
        return delegate.filterPermissions(perms);
    }
}
