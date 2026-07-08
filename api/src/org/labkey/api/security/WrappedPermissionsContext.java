/*
 * Copyright (c) 2021-2026 LabKey Corporation
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

import jakarta.servlet.http.HttpSession;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.impersonation.ImpersonationContextFactory;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.stream.Stream;

/**
 * See subclasses ElevatedUser and RoleRestrictedUser
 */
abstract class WrappedPermissionsContext implements PermissionsContext
{
    private final PermissionsContext _delegate;

    public WrappedPermissionsContext(PermissionsContext delegate)
    {
        _delegate = delegate;
    }

    @Override
    public boolean isImpersonating()
    {
        return _delegate.isImpersonating();
    }

    @Override
    @Nullable
    public Container getImpersonationProject()
    {
        return _delegate.getImpersonationProject();
    }

    @Override
    public User getAdminUser()
    {
        return _delegate.getAdminUser();
    }

    @Override
    public String getCacheKey()
    {
        return _delegate.getCacheKey();
    }

    @Override
    public ActionURL getReturnUrl()
    {
        return _delegate.getReturnUrl();
    }

    @Override
    public PrincipalArray getGroups(User user)
    {
        return _delegate.getGroups(user);
    }

    @Override
    public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
    {
        return _delegate.getAssignedRoles(user, resource);
    }

    @Override
    public ImpersonationContextFactory getFactory()
    {
        return _delegate.getFactory();
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        _delegate.addMenu(menu, c, user, currentURL);
    }

    @Override
    public Stream<Class<? extends Permission>> filterPermissions(Stream<Class<? extends Permission>> perms)
    {
        return _delegate.filterPermissions(perms);
    }

    @Override
    public void modifySession(HttpSession session)
    {
        _delegate.modifySession(session);
    }
}
