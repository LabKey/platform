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

import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;

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

        Container c = context.getContainer();
        NavTree[] admin = new NavTree[4];
        admin[0] = new NavTree("Admin Console", ActionURL.toPathString("admin", "showAdmin", c));
        admin[1] = new NavTree("Site Admins", ActionURL.toPathString("Security", "group", "") + "?group=Administrators");
        admin[2] = new NavTree("Site Users", ActionURL.toPathString("User", "showUsers", c));
        admin[3] = new NavTree("Create Project", ActionURL.toPathString("admin", "createFolder", ""));
        return admin;
    }


    @Override
    public boolean isVisible()
    {
        return getViewContext().getUser().isAdministrator();
    }
}
