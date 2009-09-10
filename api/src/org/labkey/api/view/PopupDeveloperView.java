package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.QueryUrls;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Sep 10, 2009
 * Time: 3:13:32 PM
 */
public class PopupDeveloperView  extends PopupMenuView
{
    public PopupDeveloperView(ViewContext context)
    {
        NavTree navTree = new NavTree("Developer");

        if (context.getUser().isDeveloper())
            navTree.addChildren(getNavTree(context));

        navTree.setId("devMenu");
        setNavTree(navTree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.BOLDTEXT);
    }

    public static List<NavTree> getNavTree(ViewContext context)
    {
        ArrayList<NavTree> items = new ArrayList<NavTree>();
        items.add(new NavTree("Schema Browser", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(context.getContainer())));
        items.add(new NavTree("JavaScript API Reference", "https://www.labkey.org/download/clientapi_docs/javascript-api/"));
        return items;
    }
}
