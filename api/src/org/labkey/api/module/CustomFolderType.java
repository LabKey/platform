/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Folder type that results in an old style "tabbed" folder. Each enabled module gets its own tab.
 */
public class CustomFolderType implements FolderType
{
    public CustomFolderType(){}
    public void configureContainer(Container c, User user) {  }
    public void unconfigureContainer(Container c, User user) {  }
    public String getName() { return "None"; }
    protected boolean forceAssayUploadIntoWorkbooks = false;

    @NotNull
    @Override
    public Set<String> getLegacyNames()
    {
        return Collections.emptySet();
    }

    public String getDescription()
    {
        return "Create a tab for each LabKey module you select.";
    }

    public List<Portal.WebPart> getRequiredWebParts()
    {
        return Collections.emptyList();
    }

    public List<Portal.WebPart> getPreferredWebParts()
    {
        return Collections.emptyList();
    }

    public String getLabel() { return "Custom"; }
    public Module getDefaultModule() { return null; }
    public Set<Module> getActiveModules() { return Collections.emptySet(); }
    public String getStartPageLabel(ViewContext ctx) { return null; }
    public ActionURL getStartURL(Container c, User u)
    {
        if (null == c)
            return AppProps.getInstance().getHomePageActionURL();
        if (null == c.getDefaultModule(u))
            return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        return c.getDefaultModule(u).getTabURL(c, u);
    }
    public HelpTopic getHelpTopic() { return null; }

    public void addManageLinks(NavTree adminNavTree, Container container, User user)
    {
        AdminLinkManager.getInstance().addStandardAdminLinks(adminNavTree, container, user);
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        return Collections.emptyList();
    }

    @Override
    public FolderTab getDefaultTab()
    {
        return null;
    }

    @Override
    public FolderTab findTab(String tabName)
    {
        return null;
    }

    @NotNull
    public AppBar getAppBar(ViewContext context, PageConfig pageConfig)
    {
        List<NavTree> tabs = new ArrayList<>();

        User user = context.getUser();
        Container container = context.getContainer();
        String name = container.getTitle().isEmpty() ? container.getName() : container.getTitle();
        ActionURL url = container.getStartURL(context.getUser());
        if (!container.isRoot())
        {
            Set<Module> containerModules = container.getActiveModules();
            Module activeModule = pageConfig.getModuleOwner();
            String currentController = context.getActionURL().getController();
            if (activeModule == null)
            {
                activeModule = ModuleLoader.getInstance().getModuleForController(currentController);
            }

            assert activeModule != null : "Controller '" + currentController + "' is not claimed by any module.  " +
                    "This controller name must be added to the list of names returned by 'getController()' " +
                    "from at least one module.";
            List<Module> moduleList = getSortedModuleList();
            for (Module module : moduleList)
            {
                boolean selected = (module == activeModule);
                if (selected || (containerModules.contains(module)
                        && null != module.getTabURL(container, context.getUser())))
                {
                    NavTree navTree = new NavTree(module.getTabName(context), module.getTabURL(context.getContainer(), context.getUser()));
                    navTree.setSelected(selected);
                    tabs.add(navTree);
                }
            }
        }
        else
        {
            if (container.hasPermission(user, ReadPermission.class))
                tabs.add(new NavTree("Projects", PageFlowUtil.urlProvider(CoreUrls.class).getProjectsURL(context.getContainer())));

            if (container.hasPermission(user, AdminReadPermission.class))
            {
                url = PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
                tabs.add(new NavTree("Admin Console", url));
                name = "Site Administration";
            }
        }

        return new AppBar(name, url, tabs);
    }

    @NotNull
    public String getFolderIconPath()
    {
        return DefaultFolderType.DEFAULT_FOLDER_ICON_PATH;
    }

    public boolean getForceAssayUploadIntoWorkbooks()
    {
        return forceAssayUploadIntoWorkbooks;
    }

    public void setForceAssayUploadIntoWorkbooks(boolean forceAssayUploadIntoWorkbooks)
    {
        this.forceAssayUploadIntoWorkbooks = forceAssayUploadIntoWorkbooks;
    }

    private List<Module> getSortedModuleList()
    {
        List<Module> sortedModuleList = new ArrayList<>();
        // special-case the portal module: we want it to always be at the far left.
        Module portal = null;
        for (Module module : ModuleLoader.getInstance().getModules())
        {
            if ("Portal".equals(module.getName()))
                portal = module;
            else
                sortedModuleList.add(module);
        }
        sortedModuleList.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        if (portal != null)
            sortedModuleList.add(0, portal);

        return sortedModuleList;
    }


    @Override
    public boolean isWorkbookType()
    {
        return false;
    }

    @Override
    public boolean isProjectOnlyType()
    {
        return false;
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

    @Override
    public void resetDefaultTabs(Container c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMenubarEnabled()
    {
        return false;
    }

    @Override
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
