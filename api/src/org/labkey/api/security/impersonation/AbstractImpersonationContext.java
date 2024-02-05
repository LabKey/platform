/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.CanImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.Set;

public abstract class AbstractImpersonationContext implements ImpersonationContext
{
    private final User _adminUser;
    private final @Nullable Container _project;
    @JsonIgnore // Can't be handled by remote pipelines
    private final ActionURL _returnURL;
    private final ImpersonationContextFactory _factory;

    protected AbstractImpersonationContext(User adminUser, @Nullable Container project, ActionURL returnURL, ImpersonationContextFactory factory)
    {
        _adminUser = adminUser;
        _project = project;
        _returnURL = returnURL;
        _factory = factory;
    }

    @Override
    public final boolean isImpersonating()
    {
        return true;
    }

    @Override
    public @Nullable Container getImpersonationProject()
    {
        return _project;
    }

    @Override
    public final User getAdminUser()
    {
        return _adminUser;
    }

    @Override
    public final ActionURL getReturnURL()
    {
        return _returnURL;
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        ActionURL url = PageFlowUtil.urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getImpersonationContext().getReturnURL());
        NavTree stop = new NavTree("Stop Impersonating", url).usePost();
        menu.addChild(stop);
    }

    @Override
    public ImpersonationContextFactory getFactory()
    {
        return _factory;
    }

    /**
     * @return A set of roles with the privileged roles filtered out if the impersonating admin user isn't allowed them
     */
    protected Set<Role> getFilteredRoles(Set<Role> roles)
    {
        if (getAdminUser() != null && !getAdminUser().hasRootPermission(CanImpersonatePrivilegedSiteRolesPermission.class))
            roles.removeIf(Role::isPrivileged);

        return roles;
    }
}
