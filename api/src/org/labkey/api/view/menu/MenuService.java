/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.labkey.api.util.GUID;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;

import java.util.List;
import java.util.ArrayList;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:52:00 AM
 */
public class MenuService
{
    private static Service _instance = new ServiceImpl();

    public interface Service
    {
        MenuView getMenuView(ViewContext context, PageConfig config);
        List<? extends WebPartView> getMenus(ViewContext context, PageConfig trailConfig);
        NavTree getSiteAdminTree(ViewContext context);
        NavTree getProjectAdminTree(ViewContext context);
        ActionURL getSwitchAdminModeURL(ViewContext context);
    }

    private static class ServiceImpl implements Service
    {
        public List<? extends WebPartView> getMenus(ViewContext context, PageConfig page)
        {
            List<WebPartView> menus = new ArrayList<WebPartView>();
            menus.add(new ContainerMenu(context));
            menus.add(new ProjectsMenu(context));
            menus.add(new FooterMenu(context, page));
            return menus;
        }

        public MenuView getMenuView(ViewContext context, PageConfig page)
        {
            return new MenuView(getMenus(context, page));
        }

        public NavTree getProjectAdminTree(ViewContext context)
        {
            return createNavTree(new ProjectAdminMenu(context));
        }

        public NavTree getSiteAdminTree(ViewContext context)
        {
            return createNavTree(new SiteAdminMenu(context));
        }

        private NavTree createNavTree(NavTreeMenu menu)
        {
            NavTree navTree = new NavTree(menu.getTitle());
            if (menu.getTitleHref() != null)
                navTree.setHref(menu.getTitleHref());
            navTree.addChildren(menu.getElements());
            return navTree;
        }

        public ActionURL getSwitchAdminModeURL(ViewContext context)
        {
            ActionURL url = context.cloneActionURL();
            url.deleteParameters().setAction("setAdminMode").setController("admin");
            url.replaceParameter("adminMode", String.valueOf(!context.isAdminMode()));
            ActionURL redir = context.getActionURL().clone();
            redir.replaceParameter("_dc", GUID.makeHash());
            url.replaceParameter("returnUrl", redir.toString());
            return url;
        }
    }

    public static Service get()
    {
        return _instance;
    }
}
