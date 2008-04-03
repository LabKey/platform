package org.labkey.api.view.menu;

import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;

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

        NavTree[] admin = new NavTree[3];
        admin[0] = new NavTree("Permissions", ActionURL.toPathString("Security", "begin", c.getPath()));
        admin[1] = new NavTree("Manage Folders", ActionURL.toPathString("admin", "manageFolders", c.getPath()));
        admin[2] = new NavTree("Customize Folder", ActionURL.toPathString("admin", "customize", c.getPath()));
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
