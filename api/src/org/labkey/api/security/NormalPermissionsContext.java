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
package org.labkey.api.security;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.impersonation.GroupImpersonationContextFactory;
import org.labkey.api.security.impersonation.ImpersonationContextFactory;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.impersonation.UserImpersonationContextFactory;
import org.labkey.api.security.permissions.ImpersonatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

/**
 * Used when a user is logged in normally and operating as themselves, not impersonating another user, group, or role.
 */
public class NormalPermissionsContext implements PermissionsContext
{
    private static final NormalPermissionsContext INSTANCE = new NormalPermissionsContext();

    public static NormalPermissionsContext get()
    {
        return INSTANCE;
    }

    protected NormalPermissionsContext()
    {
    }

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
    public User getAdminUser()
    {
        return null;
    }

    @Override
    public String getCacheKey()
    {
        return "";
    }

    @Override
    public ActionURL getReturnUrl()
    {
        return null;
    }

    @Override
    public ImpersonationContextFactory getFactory()
    {
        return null;
    }

    @Override
    public PrincipalArray getGroups(User user)
    {
        return GroupManager.getAllGroupsForPrincipal(user);
    }

    @Override
    public void addMenu(NavTree menu, Container c, User user, ActionURL currentURL)
    {
        if (ModuleLoader.getInstance().isStartupComplete())
        {
            @Nullable Container project = c.getProject();

            // Site admin, app admin, and impersonating troubleshooter can impersonate anywhere; project admin can
            // impersonate in that project. Folder admins can't impersonate.
            if (user.hasRootPermission(ImpersonatePermission.class) || (project != null && project.hasPermission(user, ImpersonatePermission.class)))
            {
                NavTree impersonateMenu = new NavTree("Impersonate");
                UserImpersonationContextFactory.addMenu(impersonateMenu);
                GroupImpersonationContextFactory.addMenu(impersonateMenu);
                RoleImpersonationContextFactory.addMenu(impersonateMenu);
                menu.addChild(impersonateMenu);
            }
        }

        NavTree signOut = new NavTree("Sign Out", PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(c));
        signOut.usePost();
        menu.addChild(signOut);
    }
}
