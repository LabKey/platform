/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.SimpleFolderTab;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.menu.FolderAdminMenu;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
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
    private  String _startUrl = null;
    protected FolderTab _defaultTab = null;

    public MultiPortalFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        super(name, description, requiredParts, preferredParts, activeModules, defaultModule);
    }

    public MultiPortalFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule, String startUrl)
    {
        super(name, description, requiredParts, preferredParts, activeModules, defaultModule);
        _startUrl = startUrl;
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return true;
    }

    private static List<FolderTab> getFolderTabs(Container container, FolderType folderType, boolean showHidden)
    {
        CaseInsensitiveHashMap<Portal.PortalPage> portalPages = new CaseInsensitiveHashMap<>(Portal.getPages(container, showHidden));
        List<FolderTab> folderTabs = new ArrayList<>();
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

        // OK if there are no pages at this point
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
        // Show hidden tabs if the user is an admin.
        boolean showHiddenTabs = ctx.hasPermission(ctx.getUser(), AdminPermission.class);

        // Get folder tabs first because it will bring back a portal page if ALL have been hidden
        CaseInsensitiveHashMap<FolderTab> folderTabMap = new CaseInsensitiveHashMap<>();
        for (FolderTab folderTab : getFolderTabs(container, this, showHiddenTabs))
        {
            folderTabMap.put(folderTab.getName(), folderTab);
        }

        // Now get portal pages that are not stealth pages.
        List<Portal.PortalPage> sortedPages = Portal.getTabPages(container, showHiddenTabs);

        // No page index should be 0
        assert !(sortedPages.size() > 0 && sortedPages.get(0).getIndex() <= 0);

        _activePortalPage = null;
        Map<String, NavTree> navMap = new LinkedHashMap<>();
        List<NavTree> buttons = new ArrayList<>();
        List<NavTree> subContainerTabs = new ArrayList<>();

        for (Portal.PortalPage portalPage : sortedPages)
        {
            FolderTab folderTab = folderTabMap.get(portalPage.getPageId());

            // Make sure tab isVisible and if its a container tab, make sure user has permission to see container
            if (folderTab != null && folderTab.isVisible(container, ctx.getUser()) && hasPermission(folderTab, container, ctx.getUser()))
            {
                if (!folderTab.isContainerTab() || null != folderTab.getContainerTab(container, ctx.getUser()))
                {
                    // Not a container tab or it is and the container exists -- go make a tab!
                    String label = portalPage.getCaption() != null ?
                            portalPage.getCaption() :
                            folderTab.isDefaultTab() ?
                                    getStartPageLabel(ctx) :
                                    folderTab.getCaption(ctx);

                    NavTree nav = new NavTree(label, folderTab.getURL(container, ctx.getUser()));
                    nav.setId("portal:" + portalPage.getPageId());
                    nav.addChild(getTabMenu(ctx, folderTab, portalPage, label));

                    // Set disabled if hidden so we can hide if not in tab edit mode.
                    if (portalPage.isHidden())
                        nav.setDisabled(true);

                    buttons.add(nav);
                    navMap.put(portalPage.getPageId(), nav);

                    // Stop looking for a tab to select if we've already found one
                    if (_activePortalPage == null && !portalPage.isHidden() &&
                            (null == childContainer && (folderTab.isSelectedPage(ctx)) ||
                                    (null != childContainer && childContainer.getName().equalsIgnoreCase(folderTab.getName()))))
                    {
                        nav.setSelected(true);
                        _activePortalPage = folderTab.getName();

                        // If container tab, add tabs for its folderType as children
                        if (folderTab.isContainerTab())
                        {
                            Container folderContainer = folderTab.getContainerTab(container, ctx.getUser());
                            assert (null != folderContainer);        // we checked above here
                            FolderType folderType = folderContainer.getFolderType();        // get type from container because it may be different from original
                            folderType.clearActivePortalPage();         // There may have been a previous page set the last time the container tab was visited
                            boolean foundSelected = false;
                            List<FolderTab> subTabs = getFolderTabs(folderContainer, folderType, false);
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

                            if (!foundSelected && nav.getChildCount() > 0 && !subContainerTabs.isEmpty())
                                subContainerTabs.get(0).setSelected(true);
                        }
                    }
                }
            }
        }

        // If we didn't find a match, and there is a tab that should be the default, and we're on the generic portal page
        if (_activePortalPage == null && !navMap.isEmpty() &&
            ctx.getActionURL().clone().deleteParameters().equals(PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ctx.getContainer())) &&
            null == ctx.getActionURL().getParameter("pageId"))
        {
            for (Map.Entry<String, NavTree> entry : navMap.entrySet())
            {
                NavTree nav = entry.getValue();
                if (!nav.isDisabled())
                {
                    // Mark the first visible tab as selected
                    _activePortalPage = entry.getKey();
                    nav.setSelected(true);
                    break;
                }
            }
        }

        return new AppBar(getFolderTitle(ctx), ctx.getContainer().getStartURL(ctx.getUser()), buttons, subContainerTabs);
    }


    @Override
    public ActionURL getStartURL(Container c, User user)
    {
        List<Portal.PortalPage> pages = Portal.getTabPages(c);


        // if startURL for this folderType specified in the folderType.xml use that
        if (_startUrl != null && !_startUrl.equals(""))
        {
            String encodedContainerPath = PageFlowUtil.encode(c.getPath());
            return new ActionURL(encodedContainerPath + "/" + _startUrl);
        }
        // otherwise get the startURL from the tab configs
        else
        {
            for (Portal.PortalPage page : pages)
            {
                FolderTab folderTab = findTab(page.getPageId());
                if (!page.isHidden())
                {
                    if (null != folderTab && folderTab.isVisible(c, user) && hasPermission(folderTab, c, user))
                    {
                        return folderTab.getURL(c, user);
                    }
                    else
                    {   // Issue 29604 -- let custom tab be the start tab
                        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c, page.getPageId());
                    }
                }
            }
            return super.getStartURL(c, user);
        }
    }


    protected String getFolderTitle(ViewContext context)
    {   // Default; often overridden
        return context.getContainer().getTitle();
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
            List<Portal.PortalPage> activeTabs = Portal.getTabPages(ctx.getContainer());

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

    private boolean hasPermission(FolderTab folderTab, Container container, User user)
    {
        if (folderTab.isContainerTab())
        {
            Container folderContainer = folderTab.getContainerTab(container, user);
            if (null != folderContainer && !folderContainer.hasPermission(user, ReadPermission.class))
            {
                return false;
            }
        }
        return true;
    }

    @Override @Nullable
    public FolderTab getDefaultTab()
    {
        return _defaultTab == null ? getDefaultTabs().get(0) : _defaultTab;
    }

    private NavTree getTabMenu(ViewContext ctx, FolderTab folderTab, Portal.PortalPage portalPage, String folderLabel)
    {
        String portalPageId = portalPage.getPageId();
        Container container = ctx.getContainer();
        User user = ctx.getUser();
        ProjectUrls projectURLProvider = PageFlowUtil.urlProvider(ProjectUrls.class);

        NavTree menu = new NavTree("Tab Administration");

        String portalTabParams = PageFlowUtil.jsString(portalPageId) + ", "
                + PageFlowUtil.jsString(getTabIdFromLabel(folderLabel)) + ", "
                + PageFlowUtil.jsString(folderLabel);

        if (portalPage.isHidden())
        {
            menu.addChild(new NavTree("Show", "javascript:LABKEY.Portal.showTab(" + portalTabParams + ")"));
        }
        else
        {
            ActionURL url = projectURLProvider.getHidePortalPageURL(container, portalPageId, ctx.getActionURL());
            NavTree hide = new NavTree("Hide", url).usePost();
            menu.addChild(hide);
        }

        if (portalPage.isCustomTab())
        {
            ActionURL url = projectURLProvider.getDeletePortalPageURL(container, portalPageId, ctx.getActionURL());
            NavTree delete = new NavTree("Delete", url).usePost();
            menu.addChild(delete);
        }

        NavTree moveMenu = new NavTree("Move");
        moveMenu.addChild(new NavTree("Left", "javascript:LABKEY.Portal.moveTabLeft(" + portalTabParams + ");"));
        moveMenu.addChild(new NavTree("Right", "javascript:LABKEY.Portal.moveTabRight(" + portalTabParams + ");"));
        menu.addChild(moveMenu);

        menu.addChild(new NavTree("Rename", "javascript:LABKEY.Portal.renameTab(" + portalTabParams + ");"));

        if (folderTab.getTabType() == FolderTab.TAB_TYPE.Container)
        {
            Container tabContainer = ContainerManager.getChild(container, folderTab.getName());

            if (null != tabContainer)
            {
                // Add Folder Mgmt
                menu.addSeparator();
                if (ctx.hasPermission("MultiPortalFolderType", AdminPermission.class))
                {
                    NavTree folderAdmin = new NavTree("Folder");
                    folderAdmin.addChildren(FolderAdminMenu.getFolderElements(ctx, tabContainer));
                    menu.addChild(folderAdmin);
                }
                if (ctx.getUser().isBrowserDev())
                {
                    menu.addChild(new NavTree("Schema Browser", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(tabContainer)));
                }

                menu.addSeparator();
                // might need to pass tabContainer into this.
                tabContainer.getFolderType().addManageLinks(menu, tabContainer, user);
                menu.addSeparator();

                NavTree moduleMenu = new NavTree("Go To Module");

                for (Module module : tabContainer.getActiveModules(user))
                {
                    if (null == module || module.equals(tabContainer.getDefaultModule(user)))
                        continue;

                    ActionURL tabUrl = module.getTabURL(tabContainer, user);

                    if (null != tabUrl)
                        moduleMenu.addChild(new NavTree(module.getTabName(ctx), tabUrl));

                }

                if (moduleMenu.getChildCount() > 0)
                {
                    menu.addChild(moduleMenu);
                }
            }
        }

        return menu;
    }

    private String getTabIdFromLabel(String folderLabel)
    {
        return folderLabel.replace(" ", "") + "Tab";
    }

    @Override
    public void clearActivePortalPage()
    {
        _activePortalPage = null;
    }
}
