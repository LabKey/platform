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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Comparator;
import java.io.Writer;

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

    @Override
    protected void renderView(NavTree model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if ("post".equals(request.getMethod().toLowerCase()))
            return;

        if (adminMode)
            super.renderView(model, request, response);
        else
        {
            Writer out = response.getWriter();
            if (canAdmin)
                out.write("<a class=\"wpTitle\" href=\"" + PageFlowUtil.filter(adminURL.toString()) + "\">Show Admin</a>");
            else
                out.write("&nbsp;");
        }
    }

    public PopupAdminView(final ViewContext context, PageConfig page)
    {
        super("adminMenu", null);

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

        NavTree projectAdmin = new NavTree("Manage Project");
        projectAdmin.addChildren(ProjectAdminMenu.getNavTree(context));
        navTree.addChild(projectAdmin);


        if (user.isAdministrator())
        {

            NavTree siteAdmin = new NavTree("Manage Site");

            siteAdmin.addChildren(SiteAdminMenu.getNavTree(context));
            navTree.addChild(siteAdmin);
        }

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

        if (AssayService.get().hasAssayProtocols(c))
            navTree.addChild(new NavTree("Manage Assays", AssayService.get().getAssayListURL(c)));

        navTree.addChild(new NavTree("Manage Lists", ListService.get().getManageListsURL(c)));

        for (Module module : modules)
        {
            if (null == module || module.equals(defaultModule))
                continue;
            
            goToModuleMenu.addChild(module.getTabName(context), module.getTabURL(c, user));
        }
        navTree.addChild(goToModuleMenu);

        navTree.addChild(NavTree.MENU_SEPARATOR);
        setNavTree(navTree);
        setAlign(Align.RIGHT);
    }


}
