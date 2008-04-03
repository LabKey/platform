package org.labkey.api.view.menu;

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
            //menus.add(new HomeLinkMenu());
            menus.add(new ContainerMenu(context));
            menus.add(new ProjectsMenu(context));
            if (context.isAdminMode())
            {
                menus.add(new ProjectAdminMenu(context));
                menus.add(new SiteAdminMenu(context));
            }
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
                navTree.setValue(menu.getTitleHref());
            navTree.addChildren(menu.getElements());
            return navTree;
        }

        public ActionURL getSwitchAdminModeURL(ViewContext context)
        {
            ActionURL url = context.cloneActionURL();
            url.deleteParameters().setAction("setAdminMode").setPageFlow("admin");
            url.replaceParameter("adminMode", String.valueOf(!context.isAdminMode()));
            url.replaceParameter("redir", context.getActionURL().toString());

            return url;
        }
    }

    public static Service get()
    {
        return _instance;
    }
}
