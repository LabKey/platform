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

package org.labkey.api.view.menu;

import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
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
        if (!user.isAdministrator())
            return null;

        AdminUrls adminUrls = PageFlowUtil.urlProvider(AdminUrls.class);
        NavTree[] admin = new NavTree[6];
        admin[0] = new NavTree("Admin Console", adminUrls.getAdminConsoleURL());
        admin[1] = new NavTree("Site Admins", PageFlowUtil.urlProvider(SecurityUrls.class).getManageGroupURL(ContainerManager.getRoot(), "Administrators"));
        admin[2] = new NavTree("Site Developers", PageFlowUtil.urlProvider(SecurityUrls.class).getManageGroupURL(ContainerManager.getRoot(), "Developers"));
        admin[3] = new NavTree("Site Users", PageFlowUtil.urlProvider(UserUrls.class).getSiteUsersURL());
        admin[4] = new NavTree("Site Groups", PageFlowUtil.urlProvider(SecurityUrls.class).getProjectURL(ContainerManager.getRoot()));
        admin[5] = new NavTree("Create Project", adminUrls.getCreateProjectURL());
        return admin;
    }


    @Override
    public boolean isVisible()
    {
        return getViewContext().getUser().isAdministrator();
    }
}
