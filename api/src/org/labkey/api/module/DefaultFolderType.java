/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * User: Mark Igra
 * Date: Aug 2, 2006
 * Time: 8:44:04 PM
 */
public class DefaultFolderType implements FolderType
{
    public static final String DEFAULT_FOLDER_ICON_PATH = "_icons/icon_folder2.png";
    protected List<WebPart> requiredParts;
    protected List<WebPart> preferredParts;
    protected Set<Module> activeModules;
    protected String description;
    protected String name;
    protected Module defaultModule;
    protected boolean workbookType = false;
    protected String folderIconPath = DEFAULT_FOLDER_ICON_PATH;
    protected boolean forceAssayUploadIntoWorkbooks = false;
    protected boolean menubarEnabled = false;
    protected List<FolderTab> _folderTabs = null;

    public static String DEFAULT_DASHBOARD = "DefaultDashboard";

    public DefaultFolderType(String name, String description)
    {
        this.name = name;
        this.description = description;
    }

    public DefaultFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        this(name, description);
        this.requiredParts = requiredParts == null ? Collections.emptyList() : requiredParts;
        this.preferredParts = preferredParts == null ? Collections.emptyList() : preferredParts;
        this.activeModules = activeModules;
        this.defaultModule = defaultModule;
    }

    public DefaultFolderType(String name, String description, List<Portal.WebPart> requiredParts, List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule, boolean forceAssayUploadIntoWorkbooks, String folderIconPath)
    {
        this(name, description, requiredParts, preferredParts, activeModules, defaultModule);
        this.forceAssayUploadIntoWorkbooks = forceAssayUploadIntoWorkbooks;
        this.folderIconPath = folderIconPath;
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        if (null == _folderTabs)
        {
            FolderTab tab = new FolderTab.PortalPage(DEFAULT_DASHBOARD, name + " Dashboard")
            {
                @Override
                public ActionURL getURL(Container container, User user)
                {
                    return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
                }

                @Override
                public List<Portal.WebPart> createWebParts() { return new ArrayList<>(); }
            };
            tab.setIsDefaultTab(true);
            _folderTabs = Collections.singletonList(tab);
        }

        return _folderTabs;
    }

    @Override
    public FolderTab getDefaultTab()
    {
        return getDefaultTabs().get(0);
    }

    public void configureContainer(Container c, User user)
    {
        List<Portal.WebPart> required = getRequiredWebParts();
        List<Portal.WebPart> defaultParts = getPreferredWebParts();

        // Issue 19673: Create copies of the required and preferred webparts -- don't mutate the shared WebPart instances.
        // TODO: Ideally FolderType would use WebPartFactory instead of holding on to WebPart instances.
        required = copyWebParts(required);
        defaultParts = copyWebParts(defaultParts);

        //Just to be sure, make sure required web parts are set correctly
        if (null != required)
            for (Portal.WebPart part : required)
                part.setPermanent(true);

        ArrayList<Portal.WebPart> all = new ArrayList<>();
        List<Portal.WebPart> existingParts = Portal.getParts(c);

        if (existingParts.isEmpty())
        {
            if (null != required)
                all.addAll(required);
            if (null != defaultParts)
                all.addAll(defaultParts);
        }
        else
        {
            //Order will be required,preferred,optional
            all.addAll(existingParts);
            for (WebPart p : all)
                p.setIndex(2);

            if (null != required)
                for (Portal.WebPart part: required)
                {
                    Portal.WebPart foundPart = findPart(all, part);
                    if (null != foundPart)
                    {
                        foundPart.setPermanent(true);
                        foundPart.setIndex(0);
                    }
                    else
                    {
                        part.setIndex(0);
                        all.add(part);
                    }
                }

            if (null != defaultParts)
                for (Portal.WebPart part: defaultParts)
                {
                    Portal.WebPart foundPart = findPart(all, part);
                    if (null == foundPart)
                    {
                        part.setIndex(1); //Should put these right after required parts
                        all.add(part);
                    }
                    else
                        foundPart.setIndex(1);
                }
        }

        Set<Module> active = c.getActiveModules(user);
        Set<Module> requiredActive = c.getRequiredModules();

        if (null == active)
            active = new HashSet<>();
        else
            active = new HashSet<>(active); //Need to copy since returned set is unmodifiable.

        active.addAll(requiredActive);
        c.setActiveModules(active, user);
        Portal.saveParts(c, all);

        // A few things left to do; ordering is important

        // Force container tab containers to be created (unless they've been deleted explicitly)
        for (FolderTab folderTab : getDefaultTabs())        // Get default tabs from folder type
        {
            if (folderTab.isContainerTab() && !c.isContainerTab())
            {
                Container containerDummy = folderTab.getContainerTab(c, user);
            }
        }

        if (hasConfigurableTabs())
        {
            // Find or create pages for all default tabs (except container tabs whose container has been deleted)
            resetDefaultTabs(c);
        }

        for (FolderTab folderTab : c.getDefaultTabs())          // Get default tabs from container and create web parts
        {
            folderTab.initializeContent(c);
        }
    }

    @Override
    public void resetDefaultTabs(Container c)
    {
        Portal.resetPages(c, c.getDefaultTabs(), false);
    }


    public void unconfigureContainer(Container c, User user)
    {
        List<FolderTab> folderTabs = c.getFolderType().getDefaultTabs();
        CaseInsensitiveHashMap<Portal.PortalPage> portalPages = new CaseInsensitiveHashMap<>(Portal.getPages(c, true));

        for (FolderTab folderTab : folderTabs)
        {
            // Hide all of the portal pages for the old Folder Tabs.
            Portal.PortalPage portalPage = portalPages.get(folderTab.getName());

            if (null != portalPage)
            {
                // Mark any actual permanent parts not permanent, since we're switching to another folder type
                List<WebPart> parts = Portal.getParts(c, portalPage.getPageId());
                boolean saveRequired = false;

                for (WebPart part : parts)
                {
                    if (part.isPermanent())
                    {
                        part.setPermanent(false);
                        saveRequired = true;
                    }
                }

                if (saveRequired)
                    Portal.saveParts(c, portalPage.getPageId(), parts);

                Portal.hidePage(c, portalPage.getPageId());
            }
        }
    }

    @NotNull
    public String getFolderIconPath()
    {
        return folderIconPath;
    }

    public void setFolderIconPath(String folderIconPath)
    {
        this.folderIconPath = folderIconPath;
    }

    /**
     * Find a web part. Don't use strict equality, just name and location
     * @return matchingPart
     */
    protected Portal.WebPart findPart(List<Portal.WebPart> parts, Portal.WebPart partToFind)
    {
        String location = partToFind.getLocation();
        String name = partToFind.getName();
        for (Portal.WebPart part : parts)
            if (name.equals(part.getName()) && location.equals(part.getLocation()))
                return part;

        return null;
    }

    /**
     * Create a List of copied WebParts.
     */
    protected List<WebPart> copyWebParts(List<WebPart> parts)
    {
        if (parts == null || parts.isEmpty())
            return Collections.emptyList();

        List<WebPart> newParts = new ArrayList<>(parts.size());
        for (WebPart part : parts)
            newParts.add(new WebPart(part));

        return newParts;
    }

    public boolean getForceAssayUploadIntoWorkbooks()
    {
        return forceAssayUploadIntoWorkbooks;
    }

    public void setForceAssayUploadIntoWorkbooks(boolean forceAssayUploadIntoWorkbooks)
    {
        this.forceAssayUploadIntoWorkbooks = forceAssayUploadIntoWorkbooks;
    }

    public ActionURL getStartURL(Container c, User user)
    {
        return ModuleLoader.getInstance().getCoreModule().getTabURL(c, user);
    }

    public String getStartPageLabel(ViewContext ctx)
    {
        FolderTab folderTab = getDefaultTab();
        if (null != folderTab && folderTab.isDefaultTab())
        {
            String caption = folderTab.getCaption(ctx);
            if (null != caption)
                return caption;
        }
        return getLabel() + " Dashboard";
    }

    public HelpTopic getHelpTopic()
    {
        return null;
    }

    public Module getDefaultModule()
    {
        return defaultModule;
    }

    public List<Portal.WebPart> getRequiredWebParts()
    {
        return requiredParts;
    }

    public List<Portal.WebPart> getPreferredWebParts()
    {
        return preferredParts;
    }

    public String getName()
    {
        return name;
    }

    @NotNull
    @Override
    public Set<String> getLegacyNames()
    {
        return Collections.emptySet();
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return false;
    }

    @Override
    public boolean hasContainerTabs()
    {
        return false;
    }

    public String getDescription()
    {
        return description;
    }

    public String getLabel()
    {
        return name;
    }

    public Set<Module> getActiveModules()
    {
        return activeModules;
    }

    private static Set<Module> s_defaultModules = null;
    public static Set<Module> getDefaultModuleSet(Module...additionalModules)
    {
        //NOT thread safe, but worst thing that will happen is that it is set to the same thing twice
        if (null == s_defaultModules)
        {
            Set<Module> defaultModules = new HashSet<>();
            if (ModuleLoader.getInstance().hasModule("Announcements"))
                defaultModules.add(getModule("Announcements"));
            if (ModuleLoader.getInstance().hasModule("FileContent"))
                defaultModules.add(getModule("FileContent"));
            if (ModuleLoader.getInstance().hasModule("Wiki"))
                defaultModules.add(getModule("Wiki"));
            if (ModuleLoader.getInstance().hasModule("Query"))
                defaultModules.add(getModule("Query"));
            if (ModuleLoader.getInstance().hasModule("Issues"))
                defaultModules.add(getModule("Issues"));
            s_defaultModules = Collections.unmodifiableSet(defaultModules);
        }

        Set<Module> modules = new HashSet<>(s_defaultModules);
        Stream.of(additionalModules).filter(Objects::nonNull).forEach(modules::add);

        return modules;
    }

    protected static Module getModule(String moduleName)
    {
        Module m = ModuleLoader.getInstance().getModule(moduleName);
        assert null != m : "Failed to find module " + moduleName;
        return m;
    }

    public void addManageLinks(NavTree adminNavTree, Container container, User user)
    {
        AdminLinkManager.getInstance().addStandardAdminLinks(adminNavTree, container, user);
    }

    @Override
    public @NotNull AppBar getAppBar(ViewContext context, PageConfig pageConfig)
    {
        ActionURL startURL = getStartURL(context.getContainer(), context.getUser());
        NavTree startPage = new NavTree(getStartPageLabel(context), startURL);
        String controllerName = context.getActionURL().getController();
        Module currentModule = ModuleLoader.getInstance().getModuleForController(controllerName);
        startPage.setSelected(currentModule == getDefaultModule());
        String title = context.getContainer().isWorkbook() ? context.getContainer().getTitle() : context.getContainer().getName();
        return new AppBar(title, context.getContainer().getStartURL(context.getUser()), startPage);
    }

    @Override
    public boolean isWorkbookType()
    {
        return workbookType;
    }

    @Override
    public boolean isProjectOnlyType()
    {
        return false;
    }

    public void setWorkbookType(boolean workbookType)
    {
        this.workbookType = workbookType;
    }

    @Nullable
    @Override
    public FolderTab findTab(String caption)
    {
        for (FolderTab tab : getDefaultTabs())
            if (tab.getName().equalsIgnoreCase(caption))
                return tab;
        for (FolderTab tab : getDefaultTabs())
            if (tab.getLegacyNames().contains(caption))
                return tab;
        return null;
    }

    public boolean isMenubarEnabled()
    {
        return menubarEnabled;
    }

    public String getDefaultPageId(ViewContext ctx)
    {
        return Portal.DEFAULT_PORTAL_PAGE_ID;
    }

    @Override
    public void clearActivePortalPage()
    {

    }

    @NotNull
    @Override
    public List<NavTree> getExtraSetupSteps(Container c)
    {
        return Collections.emptyList();
    }
}
