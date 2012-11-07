/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.SimpleFolderTab;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Nov 9, 2011
 */
public abstract class MultiPortalFolderType extends DefaultFolderType
{
    private String _activePortalPage = null;
    protected FolderTab _defaultTab = null;

    public MultiPortalFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        super(name, description, requiredParts, preferredParts, activeModules, defaultModule);
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return true;
    }


    private ArrayList<Portal.PortalPage> getSortedPortalPages(Container container)
    {

        CaseInsensitiveHashMap<Portal.PortalPage> portalPages = new CaseInsensitiveHashMap<Portal.PortalPage>(Portal.getPages(container, false));

        // Build up a list of all the currently defined tab names
        Set<String> currentTabNames = new HashSet<String>();
        for (FolderTab folderTab : getDefaultTabs())
        {
            currentTabNames.add(folderTab.getName());
        }

        // Filter out ones that we've saved that are no longer part of the folder type
        ArrayList<Portal.PortalPage> filtered = new ArrayList<Portal.PortalPage>(portalPages.size());
        for (Portal.PortalPage tab : portalPages.values())
        {
            if (currentTabNames.contains(tab.getPageId()))
            {
                filtered.add(tab);
            }
        }

        // Add in custom (portal page) tabs
        for (Portal.PortalPage portalPage : portalPages.values())
        {
            String customTab = portalPage.getProperty(Portal.PROP_CUSTOMTAB);
            if (null != customTab && customTab.equalsIgnoreCase("true"))
            {
                filtered.add(portalPage);
            }
        }

        // Need to sort by index so we choose the first tab as the default tab.
        Collections.sort(filtered, new Comparator<Portal.PortalPage>()
        {
            @Override
            public int compare(Portal.PortalPage o1, Portal.PortalPage o2)
            {
                return o1.getIndex() - o2.getIndex();
            }
        });
        return filtered;
    }

    private static List<FolderTab> getFolderTabs(Container container, FolderType folderType)
    {
        CaseInsensitiveHashMap<Portal.PortalPage> portalPages = new CaseInsensitiveHashMap<Portal.PortalPage>(Portal.getPages(container, false));
        List<FolderTab> folderTabs = new ArrayList<FolderTab>();
        for (FolderTab folderTab : folderType.getDefaultTabs())
        {
            if (portalPages.containsKey(folderTab.getName()))
                folderTabs.add(folderTab);
        }

        // Add in custom (portal page) tabs
        for (Portal.PortalPage portalPage : portalPages.values())
        {
            String customTab = portalPage.getProperty(Portal.PROP_CUSTOMTAB);
            if (null != customTab && customTab.equalsIgnoreCase("true"))
            {
                String caption = portalPage.getCaption() != null ? portalPage.getCaption() : portalPage.getPageId();
                folderTabs.add(new SimpleFolderTab(portalPage.getPageId(), caption));
            }
        }

        if (folderTabs.isEmpty())
        {
            folderType.resetDefaultTabs(container);
            folderTabs = folderType.getDefaultTabs();
        }

        return folderTabs;
    }

    @Override @NotNull
    public AppBar getAppBar(ViewContext ctx, PageConfig pageConfig)
    {
        return getAppBar(ctx, pageConfig, null);
    }

    @NotNull
    public AppBar getAppBar(ViewContext ctx, PageConfig pageConfig, Container childContainer)
    {
        Container container = ctx.getContainer();

        // Get folder tabs first because it will bring back a portal page if ALL have been hidden
        CaseInsensitiveHashMap<FolderTab> folderTabMap = new CaseInsensitiveHashMap<FolderTab>();
        for (FolderTab folderTab : getFolderTabs(container, this))
        {
            folderTabMap.put(folderTab.getName(), folderTab);
        }

        // Now get portal pages that are not hidden
        Map<String,Portal.PortalPage> portalPages = Portal.getPages(container, false);
        ArrayList<Portal.PortalPage> sortedPages = new ArrayList<Portal.PortalPage>(portalPages.values());
        Collections.sort(sortedPages, new Comparator<Portal.PortalPage>()
        {
            @Override
            public int compare(Portal.PortalPage o1, Portal.PortalPage o2)
            {
                return o1.getIndex() - o2.getIndex();
            }
        });

        _activePortalPage = null;
        Map<String, NavTree> navMap = new LinkedHashMap<String, NavTree>();
        List<NavTree> buttons = new ArrayList<NavTree>();
        List<NavTree> subContainerTabs = new ArrayList<NavTree>();

        for (int i = 0; i < sortedPages.size(); i++)
        {
            FolderTab folderTab = folderTabMap.get(sortedPages.get(i).getPageId());
            Portal.PortalPage portalPage = sortedPages.get(i);
            
            if (folderTab != null && folderTab.isVisible(container, ctx.getUser()))
            {
                String label = portalPage.getCaption() != null ?
                        portalPage.getCaption() :
                        folderTab.isDefaultTab() ?
                                getStartPageLabel(ctx) :
                                folderTab.getCaption(ctx);

                NavTree nav = new NavTree(label, folderTab.getURL(container, ctx.getUser()));
                nav.setId("portal:" + portalPage.getPageId());
                nav.addChild(getTabMenu(ctx, folderTab, portalPage));
                buttons.add(nav);
                navMap.put(portalPage.getPageId(), nav);
                // Stop looking for a tab to select if we've already found one
                if (_activePortalPage == null &&
                        (null == childContainer && (folderTab.isSelectedPage(ctx)) ||
                        (null != childContainer && childContainer.getName().equalsIgnoreCase(folderTab.getName()))))
                {
                    nav.setSelected(true);
                    _activePortalPage = folderTab.getName();

                    // If container tab, add tabs for its folderType as children
                    if (FolderTab.TAB_TYPE.Container == folderTab.getTabType())
                    {
                        // Container Tab must be SimpleFolderType
                        SimpleFolderTab simpleFolderTab = (SimpleFolderTab)folderTab;
                        Container folderContainer = simpleFolderTab.getContainer(container, ctx.getUser());
                        if (null != folderContainer)
                        {
                            FolderType folderType = simpleFolderTab.getFolderType();
                            if (null != folderType)
                            {
                                boolean foundSelected = false;
                                List<FolderTab> subTabs = getFolderTabs(folderContainer, folderType);
                                for (FolderTab subTab : subTabs)
                                {
                                    if (FolderTab.TAB_TYPE.Container == subTab.getTabType())
                                        continue;       // Don't add container tabs at the second level
                                    if (!subTab.isVisible(folderContainer, ctx.getUser()))
                                        continue;
                                    NavTree subNav = new NavTree(subTab.getCaption(ctx), subTab.getURL(folderContainer, ctx.getUser()));
                                    subContainerTabs.add(subNav);
                                    if (subTab.isSelectedPage(ctx))         // Use original context to determine whether to select
                                    {
                                        subNav.setSelected(true);
                                        foundSelected = true;
                                    }
                                }

                                if (!foundSelected && nav.getChildCount() > 0)
                                    subContainerTabs.get(0).setSelected(true);
                            }
                        }
                   }
                }
            }
        }

        // If we didn't find a match, and there is a tab that should be the default, and we're on the generic portal page
        if (_activePortalPage == null && !navMap.isEmpty() && ctx.getActionURL().equals(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ctx.getContainer())))
        {
            Map.Entry<String, NavTree> entry = navMap.entrySet().iterator().next();
            // Mark the first tab as selected
            _activePortalPage = entry.getKey();
            entry.getValue().setSelected(true);
        }

        if (null != childContainer && childContainer.getFolderType() instanceof MultiPortalFolderType)
        {
            // We have childContainer, which means childContainer is a Container Tab
            // Weird that this migration is here, but if childContainer is of MultiPortalFolderType, it needs to be done. But it only applys to MultiPortals so don't do it otherwise
            migrateLegacyPortalPage(childContainer);
        }
        else
        {
            migrateLegacyPortalPage(ctx.getContainer());
        }

        return new AppBar(getFolderTitle(ctx), ctx.getContainer().getStartURL(ctx.getUser()), buttons, subContainerTabs);
    }


    @Override
    public ActionURL getStartURL(Container c, User user)
    {
        ArrayList<Portal.PortalPage> tabs = getSortedPortalPages(c);

        for (Portal.PortalPage tab : tabs)
        {
            FolderTab folderTab = findTab(tab.getPageId());
            if (!tab.isHidden() && null != folderTab && folderTab.isVisible(c, user))
            {
                return folderTab.getURL(c, user);
            }
        }
        return super.getStartURL(c, user);
    }


    private void migrateLegacyPortalPage(Container container)
    {
        List<Portal.WebPart> legacyPortalParts = new ArrayList<Portal.WebPart>(Portal.getParts(container));
        if (!legacyPortalParts.isEmpty())
        {
            FolderType folderType = container.getFolderType();
            // Check if there's a tab that has the legacy portal page ID
            for (FolderTab folderTab : folderType.getDefaultTabs())
            {
                if (Portal.DEFAULT_PORTAL_PAGE_ID.equalsIgnoreCase(folderTab.getName()))
                {
                    // If so, we don't need to migrate anything
                    return;
                }
            }

            String defaultTabName = folderType.getDefaultTab().getName();
            List<Portal.WebPart> mergedParts = new ArrayList<Portal.WebPart>(Portal.getParts(container, defaultTabName));
            Iterator<Portal.WebPart> i = legacyPortalParts.iterator();
            boolean changed = false;
            while (i.hasNext())
            {
                Portal.WebPart defaultPortalPart = i.next();
                String legacyPageAdded = defaultPortalPart.getPropertyMap().get(Portal.WEBPART_PROP_LegacyPageAdded);
                if ((null == legacyPageAdded || !legacyPageAdded.equalsIgnoreCase("true")) &&
                        !WebPartFactory.LOCATION_MENUBAR.equals(defaultPortalPart.getLocation()))
                {
                    // Add it to the default tab if it's not already there
                    if (!mergedParts.contains(defaultPortalPart))
                    {
                        Portal.WebPart webPart = new Portal.WebPart(defaultPortalPart);
                        webPart.setPageId(defaultTabName);
                        mergedParts.add(webPart);
                    }
                    // Remember that legacy portal page has been added to default tab
                    defaultPortalPart.setProperty(Portal.WEBPART_PROP_LegacyPageAdded, "true");
                    changed = true;
                }
            }

            if (changed)
            {
                // Save the legacy page and the newly merged page
                Portal.saveParts(container, legacyPortalParts);
                Portal.saveParts(container, defaultTabName, mergedParts);
            }
        }
    }

    protected String getFolderTitle(ViewContext context)
    {   // Default; often overridden
        String title = context.getContainer().isWorkbook() ? context.getContainer().getTitle() : context.getContainer().getName();
        return title;
    }


    @Override
    public String getDefaultPageId(ViewContext ctx)
    {
        String result = null;
        if (_activePortalPage != null)
        {
            // If we have an explicit selection, use that
            result = _activePortalPage;
        }
        else
        {
            ArrayList<Portal.PortalPage> activeTabs = getSortedPortalPages(ctx.getContainer());

            // Use the left-most tab as the default
            for (Portal.PortalPage tab : activeTabs)
            {
                if (!tab.isHidden())
                {
                    result = tab.getPageId();
                    break;
                }
            }
            if (null == result)
                result = Portal.DEFAULT_PORTAL_PAGE_ID;
        }

        return result;
    }


    @Override @Nullable
    public FolderTab getDefaultTab()
    {
        return _defaultTab == null ? getDefaultTabs().get(0) : _defaultTab;
    }

    private NavTree getTabMenu(ViewContext ctx, FolderTab folderTab, Portal.PortalPage portalPage)
    {
        NavTree menu = new NavTree("Tab Administration");
        ActionURL removeURL = PageFlowUtil.urlProvider(ProjectUrls.class).getHidePortalPageURL(ctx.getContainer(), portalPage.getPageId(), ctx.getActionURL());
        NavTree moveMenu = new NavTree("Move");
        String moveConfig = "{pageId: \"" + portalPage.getPageId() + "\", folderTabCaption:\"" + folderTab.getCaption(ctx) +"\"}";

        moveMenu.addChild(new NavTree("Left", "javascript:LABKEY.Portal.moveTabLeft(" + moveConfig + ");"));
        moveMenu.addChild(new NavTree("Right", "javascript:LABKEY.Portal.moveTabRight(" + moveConfig + ");"));

        menu.addChild(new NavTree("Remove", removeURL));
        menu.addChild(moveMenu);

        // TODO: Determing permissions and settings links.
//        menu.addChild(new NavTree("Permissions"));
//        menu.addChild(new NavTree("Settings"));

        if (folderTab.getTabType() == FolderTab.TAB_TYPE.Container)
        {
            Container tabContainer = ContainerManager.getChild(ctx.getContainer(), folderTab.getName());

            if (null != tabContainer)
            {
                menu.addSeparator();
                // might need to pass tabContainer into this.
                tabContainer.getFolderType().addManageLinks(menu, tabContainer);
                menu.addSeparator();

                NavTree moduleMenu = new NavTree("Go To Module");

                for (Module module : tabContainer.getActiveModules())
                {
                    if (null == module || module.equals(tabContainer.getDefaultModule()))
                        continue;

                    ActionURL tabUrl = module.getTabURL(tabContainer, ctx.getUser());

                    if(null != tabUrl)
                        moduleMenu.addChild(new NavTree(module.getTabName(ctx), tabUrl));

                }

                if(moduleMenu.getChildCount() > 0)
                {
                    menu.addChild(moduleMenu);
                }
            }
        }

        return menu;
    }
}
