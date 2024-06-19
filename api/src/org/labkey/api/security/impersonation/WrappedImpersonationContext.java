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

import com.google.common.collect.Streams;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.PrincipalArray;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Do not use this class directly; use ElevatedUser instead.
 */
public class WrappedImpersonationContext implements ImpersonationContext
{
    private final ImpersonationContext _delegate;
    private final Set<Role> _additionalRoles;

    public WrappedImpersonationContext(ImpersonationContext delegate, Set<Role> additionalRoles)
    {
        _delegate = delegate;
        _additionalRoles = additionalRoles;
    }

    public WrappedImpersonationContext(ImpersonationContext delegate, Role additionalRole)
    {
        this(delegate, Set.of(additionalRole));
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
    public ActionURL getReturnURL()
    {
        return _delegate.getReturnURL();
    }

    @Override
    public PrincipalArray getGroups(User user)
    {
        return _delegate.getGroups(user);
    }

    @Override
    public Stream<Role> getAssignedRoles(User user, SecurableResource resource)
    {
        return Streams.concat(_additionalRoles.stream(), _delegate.getAssignedRoles(user, resource));
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
}
