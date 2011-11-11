package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Nov 9, 2011
 */
public abstract class MultiPortalFolderType extends DefaultFolderType
{
    public MultiPortalFolderType(String name, String description, List<Portal.WebPart> requiredParts, List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        super(name, description, requiredParts, preferredParts, activeModules, defaultModule);
    }

    public String getPageCaption(String pageId)
    {
        for (FolderTab page : getDefaultTabs())
        {
            if (page instanceof FolderTab.PortalPage && page.getName().equals(pageId))
                return page.getCaption();
        }
        return null;
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return true;
    }

    @Override @NotNull
    public AppBar getAppBar(ViewContext ctx, PageConfig pageConfig)
    {
        Portal.WebPart[] tabs = Portal.getParts(ctx.getContainer(), FolderTab.FOLDER_TAB_PAGE_ID);
        if (tabs == null || tabs.length == 0)
        {
            tabs = resetDefaultTabs(ctx.getContainer());
        }

        List<NavTree> buttons = new ArrayList<NavTree>();
        for (Portal.WebPart tab : tabs)
        {
            FolderTab folderTab = findTab(tab.getName());
            if (folderTab != null && folderTab.isVisible(ctx))
            {
                String label = folderTab.getCaption();
                NavTree nav = new NavTree(label, folderTab.getURL(ctx));
                buttons.add(nav);
                if (folderTab.isSelectedPage(ctx))
                    nav.setSelected(true);
            }
        }
        return new AppBar(getFolderTitle(ctx), ctx.getContainer().getStartURL(ctx.getUser()), buttons);
    }

    protected abstract String getFolderTitle(ViewContext context);
}
