/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.menu.FolderAdminMenu;
import org.labkey.api.view.menu.ProjectAdminMenu;
import org.labkey.api.view.menu.SiteAdminMenu;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The Admin menu that appears in the header of pages when users have admin permissions.
 * User: Mark Igra
 * Date: Jun 21, 2007
 */
public class PopupAdminView extends PopupMenuView
{
    private boolean visible;

    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
        if (visible)
            super.renderInternal(model, out);
        else
            out.write("&nbsp;");
    }

    public PopupAdminView(final ViewContext context)
    {
        Container c = context.getContainer();
        // If current context is a container tab, use the parent container to build this menu
        if (c.isContainerTab())
        {
            c = c.getParent();
            context.setContainer(c);
        }

        NavTree tree = createNavTree(context);

        visible = tree != null;
        if (!visible)
            return;

        setNavTree(tree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);

        getModelBean().setIsSingletonMenu(true);
    }

    private static void addModulesToMenu(ViewContext context, SortedSet<Module> modules, Module defaultModule, NavTree menu)
    {
        List<NavTree> moduleItems = new ArrayList<>();

        for (Module module : modules)
        {
            if (null == module || module.equals(defaultModule))
                continue;

            ActionURL tabUrl = module.getTabURL(context.getContainer(), context.getUser());
            if (null != tabUrl)
            {
                NavTree item = new NavTree(module.getTabName(context), tabUrl);
                moduleItems.add(item);
                menu.addChild(item);
            }
        }

        // enable menu filtering for the module list if > 10 items
        if (moduleItems.size() > 10)
        {
            String menuFilterItemCls = PopupMenuView.getMenuFilterItemCls(menu);
            for (NavTree item : moduleItems)
                item.setMenuFilterItemCls(menuFilterItemCls);
        }
    }

    @Nullable
    public static NavTree createNavTree(final ViewContext context)
    {
        Container c = context.getContainer();
        // If current context is a container tab, use the parent container to build this menu
        if (c.isContainerTab())
        {
            c = c.getParent();
            context.setContainer(c);
        }

        if (!hasPermission(context))
            return null;

        User user = context.getUser();
        NavTree navTree = new NavTree("Admin");

        if (user.hasRootPermission(AdminReadPermission.class))
        {
            NavTree siteAdmin = new NavTree("Site");
            siteAdmin.addChildren(SiteAdminMenu.getNavTree(context));
            navTree.addChild(siteAdmin);
        }

        if (!c.isRoot())
        {
            Container project = c.getProject();
            assert project != null;

            if (isFolderAdmin(context) && !c.isWorkbook())
            {
                NavTree folderAdmin = new NavTree("Folder");
                folderAdmin.addChildren(FolderAdminMenu.getFolderElements(context, c));
                folderAdmin.addSeparator();
                folderAdmin.addChildren(ProjectAdminMenu.getNavTree(context));
                navTree.addChild(folderAdmin);
            }
        }

        if (user.isDeveloper())
        {
            NavTree devMenu = new NavTree("Developer Links");
            devMenu.addChildren(PopupDeveloperView.getNavTree(context));
            navTree.addChild(devMenu);
        }

        if (!c.isRoot())
        {
            if (isFolderAdmin(context))
            {
                String pageAdminTxt = PageFlowUtil.isPageAdminMode(context) ? "Exit Page Admin Mode" : "Page Admin Mode";
                ActionURL pageAdminUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getTogglePageAdminModeURL(c, context.getActionURL());
                NavTree pageAdmin = new NavTree(pageAdminTxt, pageAdminUrl);
                navTree.addChild(pageAdmin);
            }

            navTree.addSeparator();
            c.getFolderType().addManageLinks(navTree, c, user);

            Comparator<Module> moduleComparator = (o1, o2) ->
            {
                if (null == o1 && null == o2)
                    return 0;
                if (null == o1 || null == o2)
                    return null == o1 ? -1 : 1;
                return o1.getTabName(context).compareToIgnoreCase(o2.getTabName(context));
            };

            SortedSet<Module> activeModules = new TreeSet<>(moduleComparator);
            activeModules.addAll(c.getActiveModules());
            SortedSet<Module> disabledModules = new TreeSet<>(moduleComparator);
            disabledModules.addAll(ModuleLoader.getInstance().getModules());
            disabledModules.removeAll(activeModules);

            NavTree goToModuleMenu = new NavTree("Go To Module");
            Module defaultModule = null;

            if (c.getFolderType() != FolderType.NONE)
            {
                defaultModule = c.getFolderType().getDefaultModule();
                goToModuleMenu.addChild(c.getName() + " Start Page", c.getFolderType().getStartURL(c, user));
                goToModuleMenu.addSeparator();
            }

            addModulesToMenu(context, activeModules, defaultModule, goToModuleMenu);

            if (!disabledModules.isEmpty())
            {
                NavTree disabledModuleMenu = new NavTree("More Modules");
                addModulesToMenu(context, disabledModules, defaultModule, disabledModuleMenu);
                if (disabledModuleMenu.hasChildren())
                {
                    goToModuleMenu.addSeparator();
                    goToModuleMenu.addChild(disabledModuleMenu);
                }
            }

            if (goToModuleMenu.hasChildren())
            {
                navTree.addSeparator();
                navTree.addChild(goToModuleMenu);
            }
        }

        navTree.setId("adminMenu");
        return navTree;
    }

    public static boolean hasPermission(ViewContext context)
    {
        return isFolderAdmin(context) || context.getUser().hasRootPermission(AdminReadPermission.class);
    }

    private static boolean isFolderAdmin(ViewContext context)
    {
        return context.hasPermission("PopupAdminView", AdminPermission.class);
    }
}
