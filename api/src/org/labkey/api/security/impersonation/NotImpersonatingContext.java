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
import org.labkey.api.security.PrincipalArray;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.CanImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

/**
 * Used when a user is not impersonating another user, group, or role. That is, they are logged in normally, and
 * operating as themselves.
 */
public class NotImpersonatingContext implements ImpersonationContext
{
    private static final NotImpersonatingContext INSTANCE = new NotImpersonatingContext();

    public static NotImpersonatingContext get()
    {
        return INSTANCE;
    }

    protected NotImpersonatingContext()
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
    public ActionURL getReturnURL()
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
        @Nullable Container project = c.getProject();

        // Must be site or project admin (folder admins can't impersonate)
        if (user.hasRootAdminPermission() || (null != project && project.hasPermission(user, AdminPermission.class)))
        {
            NavTree impersonateMenu = new NavTree("Impersonate");
            UserImpersonationContextFactory.addMenu(impersonateMenu);
            GroupImpersonationContextFactory.addMenu(impersonateMenu);
            RoleImpersonationContextFactory.addMenu(impersonateMenu);
            menu.addChild(impersonateMenu);
        }
        // Or Impersonating Troubleshooter to impersonate site roles only
        else if (null == project && user.hasRootPermission(CanImpersonatePrivilegedSiteRolesPermission.class))
        {
            NavTree impersonateMenu = new NavTree("Impersonate");
            RoleImpersonationContextFactory.addMenu(impersonateMenu);
            menu.addChild(impersonateMenu);
        }

        NavTree signOut = new NavTree("Sign Out", PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(c));
        signOut.usePost();
        menu.addChild(signOut);
    }
}
