package org.labkey.api.view.menu;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 11:25:31 AM
 */
public class HomeLinkMenu extends HtmlView
{
    public HomeLinkMenu()
    {
        super(getLinkHtml());
    }

    private static String getLinkHtml()
    {
        ActionURL homeLink = new ActionURL("Project", "start", ContainerManager.getHomeContainer());
        return "<table><tr>" +
                "<td class=\"ms-navheader\" style=\"padding-left: 3px;\">" +
                "<a href=\"" + homeLink.getLocalURIString() + "\">Home</a></td></tr></table>";
    }

    @Override
    public boolean isVisible()
    {
        Container c = getViewContext().getContainer();
        Container project = c.isRoot() ? c : c.getProject();
        Container home = ContainerManager.getHomeContainer();
        return !project.equals(home);
    }
}
