/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.api.view.menu;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:51:20 AM
 */
public class SiteAdminMenu extends NavTreeMenu
{
    public SiteAdminMenu(ViewContext context)
    {
        super(context, "siteAdmin", "Manage Site", true, getNavTree(context));
    }

    public static NavTree[] getNavTree(ViewContext context)
    {
        User user = context.getUser();
        NavTree[] admin = null;
        Container root = ContainerManager.getRoot();

        if (user.isSiteAdmin())
        {
            SecurityUrls securityUrls = PageFlowUtil.urlProvider(SecurityUrls.class);

            admin = new NavTree[] {
                    getAdminConsole(context),
                    new NavTree("Site Admins", securityUrls.getManageGroupURL(root, "Administrators")),
                    new NavTree("Site Developers", securityUrls.getManageGroupURL(root, "Developers")),
                    new NavTree("Site Users", PageFlowUtil.urlProvider(UserUrls.class).getSiteUsersURL()),
                    new NavTree("Site Groups", securityUrls.getSiteGroupsURL(root, context.getActionURL())),
                    new NavTree("Site Permissions", securityUrls.getPermissionsURL(root, context.getActionURL())),
                    new NavTree("Create Project", PageFlowUtil.urlProvider(AdminUrls.class).getCreateProjectURL(context.getActionURL()))
            };
        }
        else if (root.hasPermission(user, AdminReadPermission.class))
        {
            admin = new NavTree[] { getAdminConsole(context) };
        }

        return admin;
    }


    @Override
    public boolean isVisible()
    {
        return ContainerManager.getRoot().hasPermission(getViewContext().getUser(), AdminReadPermission.class);
    }

    @Nullable
    private static NavTree getAdminConsole(ViewContext context)
    {
        AdminUrls adminUrls = PageFlowUtil.urlProvider(AdminUrls.class);

        ActionURL consoleUrl = adminUrls.getAdminConsoleURL();
        if (null != context && null != context.getActionURL())
        {
            if (null == context.getActionURL().getReturnURL())
                consoleUrl.addReturnURL(context.getActionURL());
            else
                consoleUrl.addReturnURL(context.getActionURL().getReturnURL());
        }

        return new NavTree("Admin Console", consoleUrl);
    }
}
