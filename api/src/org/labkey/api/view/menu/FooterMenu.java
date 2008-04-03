package org.labkey.api.view.menu;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.ACL;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.User;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:51:39 AM
 */
public class FooterMenu extends NavTreeMenu
{

    public FooterMenu(ViewContext context, PageConfig page)
    {
        super(context, "leftNavFooter", getNavTree(context, page));
        setHighlightSelection(false);
    }

    private static NavTree[] getNavTree(ViewContext context, PageConfig page)
    {
        List<NavTree> menu = new ArrayList<NavTree>();
        User user = context.getUser();

        if (context.hasPermission(ACL.PERM_ADMIN) && !"post".equalsIgnoreCase(context.getRequest().getMethod()))
            menu.add(new NavTree((context.isAdminMode() ? "Hide" : "Show") + " Admin", MenuService.get().getSwitchAdminModeURL(context)));

        //
        // LOGIN
        //
        if (user.isGuest())
            menu.add(new NavTree("Sign in", AuthenticationManager.getLoginURL(context.getActionURL())));

        ActionURL homeLink = new ActionURL("Project", "start", ContainerManager.getHomeContainer());
        menu.add(new NavTree("Home", homeLink));

        if (null != context.getContainer())
        {
            ActionURL permaLink = PageFlowUtil.expandLastFilter(context);
            permaLink.setExtraPath(context.getContainer().getId());
            menu.add(new NavTree("Permanent Link", permaLink));
        }
        
        AppProps appProps = AppProps.getInstance();
        String reportAProblemPath = appProps.getReportAProblemPath();
        if (reportAProblemPath != null && reportAProblemPath.trim().length() > 0 && !user.isGuest())
            menu.add(new NavTree("Support", appProps.getReportAProblemPath()));

        //
        // HELP
        //
        HelpTopic topic = null;
        if (null != page)
            topic = page.getHelpTopic();

        menu.add(new NavTree("Help", null == topic ? HelpTopic.getDefaultHelpURL() : topic.getHelpTopicLink()));
        return menu.toArray(new NavTree[menu.size()]);
    }
}
