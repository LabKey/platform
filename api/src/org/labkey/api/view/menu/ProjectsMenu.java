package org.labkey.api.view.menu;

import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 10:19:50 AM
 */
public class ProjectsMenu extends NavTreeMenu
{


    public ProjectsMenu(ViewContext context)
    {
        super(context, "projectsMenu", "Projects", !isHomePage(context), getNavTree(context));
    }

    private static boolean isHomePage(ViewContext context)
    {
        ActionURL url = context.getActionURL();
        Container homeContainer = ContainerManager.getHomeContainer();
        boolean isHomeContainer = context.getContainer().equals(homeContainer);
        return isHomeContainer && "project".equalsIgnoreCase(url.getPageFlow()) && "begin".equalsIgnoreCase(url.getAction());
    }
    
    private static NavTree[] getNavTree(ViewContext context)
    {
        NavTree projects = ContainerManager.getProjectList(context);
        if (null == projects)
            return new NavTree[0];

        return projects.getChildren();
    }
}
