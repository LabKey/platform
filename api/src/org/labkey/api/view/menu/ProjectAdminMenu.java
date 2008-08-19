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
import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:51:12 AM
 */
public class ProjectAdminMenu extends NavTreeMenu
{
    public ProjectAdminMenu(ViewContext context)
    {
        super(context, "projectAdmin", "Manage Project", true, getNavTree(context));
    }

    public static NavTree[] getNavTree(ViewContext context)
    {
        Container c = context.getContainer();

        NavTree[] admin = new NavTree[5];
        admin[0] = new NavTree("Permissions", PageFlowUtil.urlProvider(SecurityUrls.class).getBeginURL(c));
        admin[1] = new NavTree("Project Settings", PageFlowUtil.urlProvider(AdminUrls.class).getLookAndFeelSettingsURL(c));
        admin[2] = new NavTree("Project Members", PageFlowUtil.urlProvider(UserUrls.class).getProjectMembersURL(c));
        admin[3] = new NavTree("Manage Folders", PageFlowUtil.urlProvider(AdminUrls.class).getManageFoldersURL(c));
        admin[4] = new NavTree("Customize Folder", PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeFolderURL(c));
        return admin;
    }

    @Override
    public boolean isVisible()
    {
        Container c = getViewContext().getContainer();
        Container project = c.getProject();
        User user = getViewContext().getUser();
        return (null != project && project.isProject() && c.hasPermission(user, ACL.PERM_ADMIN));
    }
}
