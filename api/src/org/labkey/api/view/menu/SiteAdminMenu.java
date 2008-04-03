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
        admin[0] = new NavTree("Admin Console", ActionURL.toPathString("admin", "begin", c));
        admin[1] = new NavTree("Site Admins", ActionURL.toPathString("Security", "group", "") + "?group=Administrators");
        admin[2] = new NavTree("Site Users", ActionURL.toPathString("User", "showUsers", c));
        admin[3] = new NavTree("Create Project", ActionURL.toPathString("admin", "modifyFolder", "") + "?action=create");
        return admin;
    }


    @Override
    public boolean isVisible()
    {
        return getViewContext().getUser().isAdministrator();
    }
}
