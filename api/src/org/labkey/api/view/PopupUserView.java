package org.labkey.api.view;

import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class PopupUserView extends PopupMenuView
{
    public PopupUserView(ViewContext context)
    {
        User user = context.getUser();
        NavTree tree = new NavTree(user.getFriendlyName());
        tree.addChild("My Account", PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(context.getContainer(), user.getUserId(), context.getActionURL()));
        if (user.isImpersonated())
        {
            tree.addChild("Stop Impersonating", PageFlowUtil.urlProvider(LoginUrls.class).getStopImpersonatingURL(context.getContainer(), context.getRequest()));
        }
        else
        {
            tree.addChild("Sign Out", PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(context.getContainer()));
        }

        tree.setId("userMenu");

        setNavTree(tree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }
}
