/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.FolderType;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.view.menu.ProjectAdminMenu;
import org.labkey.api.view.menu.SiteAdminMenu;
import org.labkey.api.view.menu.MenuService;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.exp.list.ListService;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Comparator;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jun 21, 2007
 * Time: 10:48:42 AM
 */
public class PopupAdminView extends PopupMenuView
{
    private boolean adminMode;
    private ActionURL adminURL;
    private boolean canAdmin;

    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
        if ("post".equals(getViewContext().getRequest().getMethod().toLowerCase()))
            return;

        if (adminMode)
            super.renderInternal(model, out);    //To change body of overridden methods use File | Settings | File Templates.
        else
        {
            if (canAdmin)
                out.write("<a class=\"labkey-wp-title\" href=\"" + PageFlowUtil.filter(adminURL.toString()) + "\">Show Admin</a>");
            else
                out.write("&nbsp;");
        }
    }

    public PopupAdminView(final ViewContext context, PageConfig page)
    {
        adminMode = context.isAdminMode();
        adminURL = MenuService.get().getSwitchAdminModeURL(context);
        canAdmin = context.hasPermission(ACL.PERM_ADMIN);
        if (!canAdmin)
            return;
        
        if (!adminMode) //For now, don't even bother with menu until switch into admin mode
            return;

        NavTree navTree = new NavTree("Admin");
        Container c = context.getContainer();
        User user = context.getUser();

        if (context.isAdminMode())
            navTree.addChild("Hide Admin", adminURL);
        else //
            navTree.addChild("Show Admin", adminURL);

        if (user.isAdministrator())
        {
            NavTree siteAdmin = new NavTree("Manage Site");

            siteAdmin.addChildren(SiteAdminMenu.getNavTree(context));
            navTree.addChild(siteAdmin);
        }

        if (!c.isRoot())
        {
            NavTree projectAdmin = new NavTree("Manage Project");
            projectAdmin.addChildren(ProjectAdminMenu.getNavTree(context));
            navTree.addChild(projectAdmin);

            c.getFolderType().addManageLinks(navTree, c);

            SortedSet<Module> modules = new TreeSet<Module>(new Comparator<Module>()
            {
                public int compare(Module o1, Module o2)
                {
                    if(null == o1 && null == o2)
                        return 0;
                    if(null == o1 || null == o2)
                        return null == o1 ? -1 : 1;
                    return o1.getTabName(context).compareTo(o2.getTabName(context));
                }
            });
            modules.addAll(c.getActiveModules());

            NavTree goToModuleMenu = new NavTree("Go To Module");
            Module defaultModule = null;
            if (c.getFolderType() != FolderType.NONE)
            {
                defaultModule = c.getFolderType().getDefaultModule();
                goToModuleMenu.addChild(c.getName() + " Start Page", c.getFolderType().getStartURL(c, user));
            }

            for (Module module : modules)
            {
                if (null == module || module.equals(defaultModule))
                    continue;

                ActionURL tabUrl = module.getTabURL(c, user);
                if(null != tabUrl)
                    goToModuleMenu.addChild(module.getTabName(context), tabUrl);
            }

            if (goToModuleMenu.hasChildren())
                navTree.addChild(goToModuleMenu);
        }

        navTree.setId("adminMenu");
        setNavTree(navTree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.BOLDTEXT);
    }
}
