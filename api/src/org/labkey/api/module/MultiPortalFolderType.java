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
import org.labkey.api.data.Container;
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
import java.util.Collection;
import java.util.HashMap;
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
    protected FolderTab _defaultTab;

    public MultiPortalFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        super(name, description, requiredParts, preferredParts, activeModules, defaultModule);
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return true;
    }


    private Collection<Portal.PortalPage> getPortalPages(Container container)
    {
        Map<String,Portal.PortalPage> tabs = Portal.getPages(container, false);

        // Build up a list of all the currently defined tab names
        Set<String> currentTabNames = new HashSet<String>();
        for (FolderTab folderTab : getDefaultTabs())
        {
            currentTabNames.add(folderTab.getName());
        }

        // Filter out ones that we've saved that are no longer part of the folder type
        List<Portal.PortalPage> filtered = new ArrayList<Portal.PortalPage>(tabs.size());
        for (Portal.PortalPage tab : tabs.values())
        {
            if (currentTabNames.contains(tab.getPageId()))
            {
                filtered.add(tab);
            }
        }

        // If we don't have any matching tabs any more, reset to the default set
        if (filtered.isEmpty())
            filtered = resetDefaultTabs(container);

        return filtered;
    }

    private static List<FolderTab> getFolderTabs(Container container, FolderType folderType)
    {
        Map<String,Portal.PortalPage> portalPages = Portal.getPages(container, false);
        List<FolderTab> folderTabs = new ArrayList<FolderTab>();
        for (FolderTab folderTab : folderType.getDefaultTabs())
        {
            if (portalPages.containsKey(folderTab.getName()))
                folderTabs.add(folderTab);
        }

        if (folderTabs.isEmpty())
        {
            folderType.resetDefaultTabs(container);
            folderTabs = folderType.getDefaultTabs();
        }

        // Add in custom (portal page) tabs
        for (Portal.PortalPage portalPage : portalPages.values())
        {
            String properties = portalPage.getProperties();
            if (null != properties && properties.contains(Portal.PROP_CUSTOMTAB))
            {
                folderTabs.add(new SimpleFolderTab(portalPage.getPageId(), portalPage.getPageId()));
            }
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
        List<FolderTab> folderTabs = getFolderTabs(container, this);
        Map<String,Portal.PortalPage> portalPages = Portal.getPages(container, false);
        List<NavTree> buttons = new ArrayList<NavTree>();

        _activePortalPage = null;
        Map<String, NavTree> navMap = new LinkedHashMap<String, NavTree>();
        for (FolderTab folderTab : folderTabs)
        {
            if (folderTab != null && folderTab.isVisible(container, ctx.getUser()))
            {
                Portal.PortalPage portalPage = portalPages.get(folderTab.getName());
                String label = folderTab.getCaption(ctx);
                NavTree nav = new NavTree(label, folderTab.getURL(container, ctx.getUser()));
                nav.setId("portal:" + portalPage.getPageId());
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
                                    nav.addChild(subNav);
                                    if (subTab.isSelectedPage(ctx))         // Use original context to determine whether to select
                                    {
                                        subNav.setSelected(true);
                                        foundSelected = true;
                                    }
                                }

                                if (!foundSelected && nav.getChildCount() > 0)
                                    nav.getChildren()[0].setSelected(true);
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

        return new AppBar(getFolderTitle(ctx), ctx.getContainer().getStartURL(ctx.getUser()), buttons);
    }


    @Override
    public ActionURL getStartURL(Container c, User user)
    {
        Collection<Portal.PortalPage> tabs = getPortalPages(c);
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
                if (!WebPartFactory.LOCATION_MENUBAR.equals(defaultPortalPart.getLocation()))
                {
                    // Add it to the default tab if it's not already there
                    if (!mergedParts.contains(defaultPortalPart))
                    {
                        defaultPortalPart.setPageId(defaultTabName);
                        mergedParts.add(defaultPortalPart);
                    }
                    // Remove it from the legacy portal page
                    i.remove();
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

    protected abstract String getFolderTitle(ViewContext context);


    @Override
    public String getDefaultPageId(ViewContext ctx)
    {
        String result;
        if (_activePortalPage != null)
        {
            // If we have an explicit selection, use that
            result = _activePortalPage;
        }
        else
        {
            Collection<Portal.PortalPage> activeTabs = getPortalPages(ctx.getContainer());
            if (activeTabs.isEmpty())
            {
                // No real tabs exist for this folder type, so just use the default portal page
                result = Portal.DEFAULT_PORTAL_PAGE_ID;
            }
            else
            {
                // Use the left-most tab as the default
                result = activeTabs.iterator().next().getPageId();
            }
        }

        return result;
    }


    @Override @Nullable
    public FolderTab getDefaultTab()
    {
        return _defaultTab == null ? getDefaultTabs().get(0) : _defaultTab;
    }
}
