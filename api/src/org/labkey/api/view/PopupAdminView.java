/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.menu.MenuService;
import org.labkey.api.view.menu.ProjectAdminMenu;
import org.labkey.api.view.menu.SiteAdminMenu;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * User: Mark Igra
 * Date: Jun 21, 2007
 * Time: 10:48:42 AM
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
        User user = context.getUser();
        ActionURL currentURL = context.getActionURL();

        boolean isAdminInThisFolder = context.hasPermission(AdminPermission.class);
        boolean hasAdminReadInRoot = ContainerManager.getRoot().hasPermission(user, AdminReadPermission.class);

        visible = isAdminInThisFolder || hasAdminReadInRoot;
        if (!visible)
            return;
        
        NavTree navTree = new NavTree("Admin");
        Container c = context.getContainer();

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
        //Allow Admins to turn the folder bar on & off
        if (laf.getFolderDisplayMode() != FolderDisplayMode.ALWAYS && !"POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
        {
            ActionURL adminURL = MenuService.get().getSwitchAdminModeURL(context);

            if (context.isAdminMode())
                navTree.addChild("Hide Navigation Bar", adminURL);
            else
                navTree.addChild("Show Navigation Bar", adminURL);
        }

        if (hasAdminReadInRoot)
        {
            NavTree siteAdmin = new NavTree("Manage Site");

            siteAdmin.addChildren(SiteAdminMenu.getNavTree(context));
            navTree.addChild(siteAdmin);
        }

        if (!c.isRoot())
        {
            Container project = c.getProject();
            assert project != null;

            if (isAdminInThisFolder && !c.isWorkbook())
            {
                NavTree projectAdmin = new NavTree("Manage Project");
                projectAdmin.addChildren(ProjectAdminMenu.getNavTree(context));
                navTree.addChild(projectAdmin);
            }

            c.getFolderType().addManageLinks(navTree, c);

            // Don't allow folder admins to impersonate
            if (project.hasPermission(user, AdminPermission.class) && !user.isImpersonated())
            {
                UserUrls userURLs = PageFlowUtil.urlProvider(UserUrls.class);
                AdminUrls adminURLs = PageFlowUtil.urlProvider(AdminUrls.class);
                NavTree impersonateMenu = new NavTree("Impersonate");

                ActionURL impersonateURL = user.isAdministrator() ? adminURLs.getAdminConsoleURL() : userURLs.getProjectUsersURL(project);
                NavTree userMenu = new NavTree("User", impersonateURL);
                impersonateMenu.addChild(userMenu);
                NavTree groupMenu = new NavTree("Group");

                // TODO: Cache this!
                Group[] groups = SecurityManager.getGroups(c.getProject(), true);

                boolean addSeparator = false;

                // Site groups are always first, followed by project groups
                for (Group group : groups)
                {
                    if (!SecurityManager.canImpersonateGroup(c, user, group))
                        continue;

                    if (!group.isProjectGroup())
                    {
                        // We have at least one site group... so add a separator (if we also have project groups)
                        addSeparator = true;
                    }
                    else if (addSeparator)
                    {
                        // Our first project group after site groups... add a separator
                        groupMenu.addSeparator();
                        addSeparator = false;
                    }

                    groupMenu.addChild(group.getName(), userURLs.getImpersonateGroupURL(c, group.getUserId(), currentURL));
                }

                // TODO: impersonateMenu.addChild(groupMenu);
                navTree.addChild(impersonateMenu);
            }

            Comparator<Module> moduleComparator = new Comparator<Module>()
            {
                public int compare(Module o1, Module o2)
                {
                    if (null == o1 && null == o2)
                        return 0;
                    if (null == o1 || null == o2)
                        return null == o1 ? -1 : 1;
                    return o1.getTabName(context).compareToIgnoreCase(o2.getTabName(context));
                }
            };

            SortedSet<Module> activeModules = new TreeSet<Module>(moduleComparator);
            activeModules.addAll(c.getActiveModules());
            SortedSet<Module> disabledModules = new TreeSet<Module>(moduleComparator);
            disabledModules.addAll(ModuleLoader.getInstance().getModules());
            disabledModules.removeAll(activeModules);

            NavTree goToModuleMenu = new NavTree("Go To Module");
            Module defaultModule = null;

            if (c.getFolderType() != FolderType.NONE)
            {
                defaultModule = c.getFolderType().getDefaultModule();
                goToModuleMenu.addChild(c.getName() + " Start Page", c.getFolderType().getStartURL(c, user));
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
                navTree.addChild(goToModuleMenu);
        }

        if (user.isDeveloper())
        {
            NavTree devMenu = new NavTree("Developer Links");
            devMenu.addChildren(PopupDeveloperView.getNavTree(context));
            navTree.addChild(devMenu);
        }

        navTree.setId("adminMenu");
        setNavTree(navTree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }

    private void addModulesToMenu(ViewContext context, SortedSet<Module> modules, Module defaultModule, NavTree menu)
    {
        for (Module module : modules)
        {
            if (null == module || module.equals(defaultModule))
                continue;

            ActionURL tabUrl = module.getTabURL(context.getContainer(), context.getUser());

            if (null != tabUrl)
                menu.addChild(module.getTabName(context), tabUrl);
        }
    }
}
