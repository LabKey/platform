/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.admin;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.TabStripView.TabInfo2;
import org.labkey.core.admin.AdminController.ExportFolderAction;
import org.labkey.core.admin.AdminController.FileRootsAction;
import org.labkey.core.admin.AdminController.FolderInformationAction;
import org.labkey.core.admin.AdminController.FolderSettingsAction;
import org.labkey.core.admin.AdminController.FolderTypeAction;
import org.labkey.core.admin.AdminController.ImportFolderAction;
import org.labkey.core.admin.AdminController.ManageFoldersAction;
import org.labkey.core.admin.AdminController.MissingValuesAction;
import org.labkey.core.admin.AdminController.ModulePropertiesAction;
import org.labkey.core.admin.AdminController.NotificationsAction;
import org.labkey.core.admin.AdminController.SearchAction;
import org.springframework.validation.BindException;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * User: klum
 * Date: Jan 17, 2011
 * Time: 10:36:02 AM
 */
public class FolderManagement
{
    private static final List<TabInfo2> REGISTERED_TABS = new CopyOnWriteArrayList<>();

    public static void addTab(String name, String id, Predicate<Container> filter, Function<Container, ActionURL> urlGenerator)
    {
        REGISTERED_TABS.add(new TabInfo2(name, id, urlGenerator, filter));
    }

    private static final Predicate<Container> EVERY_CONTAINER = container -> true;
    private static final Predicate<Container> NOT_ROOT = container -> !container.isRoot();
    private static final Predicate<Container> FOLDERS_AND_PROJECTS = container -> !container.isRoot() && !container.isWorkbook();
    private static final Predicate<Container> FOLDERS_ONLY = container -> !container.isRoot() && !container.isProject() && !container.isWorkbook();

    static
    {
        // Original order: Folder Tree, Folder Type, Missing Values, Module Properties, Concepts, Search, Notifications, Export, Import, Files, Formats, Information

        addTab("Folder Tree", "folderTree", NOT_ROOT, c -> new ActionURL(ManageFoldersAction.class, c));
        addTab("Folder Type", "folderType", NOT_ROOT, c -> new ActionURL(FolderTypeAction.class, c));
        addTab("Missing Values", "mvIndicators", EVERY_CONTAINER, c -> new ActionURL(MissingValuesAction.class, c));
        addTab("Module Properties", "props", c -> {
            if (!c.isRoot())
            {
                // Show module properties tab only if a module w/ properties to set is present for current folder
                for (Module m : c.getActiveModules())
                    if (!m.getModuleProperties().isEmpty())
                        return true;
            }

            return false;
        }, c -> new ActionURL(ModulePropertiesAction.class, c));
        addTab("Concepts", "concepts", c -> {
            // Show Concepts tab only if the experiment module is enabled in this container
            return c.getActiveModules().contains(ModuleLoader.getInstance().getModule("Experiment"));
        }, c -> new ActionURL(AdminController.ConceptsAction.class, c));
        addTab("Search", "fullTextSearch", NOT_ROOT, c -> new ActionURL(SearchAction.class, c));
        addTab("Notifications", "messages", NOT_ROOT, c -> new ActionURL(NotificationsAction.class, c));
        addTab("Export", "export", NOT_ROOT, c -> new ActionURL(ExportFolderAction.class, c));
        addTab("Import", "import", NOT_ROOT, c -> new ActionURL(ImportFolderAction.class, c));
        addTab("Files", "files", FOLDERS_AND_PROJECTS, c -> new ActionURL(FileRootsAction.class, c));
        addTab("Formats", "settings", FOLDERS_ONLY, c -> new ActionURL(FolderSettingsAction.class, c));
        addTab("Information", "info", NOT_ROOT, c -> new ActionURL(FolderInformationAction.class, c));
    }

    abstract static class FolderManagementTabStrip extends TabStripView
    {
        private final Container _container;
        private final BindException _errors;

        protected FolderManagementTabStrip(Container c, String tabId, BindException errors)
        {
            _container = c;
            _errors = errors;

            // Stay on same tab if there are errors
            if (_errors.hasErrors() && null != StringUtils.trimToNull(tabId))
                setSelectedTabId(tabId);
        }

        public List<NavTree> getTabList()
        {
            List<NavTree> tabs = new LinkedList<>();

            FolderManagement.REGISTERED_TABS.forEach(tab->{
                if (tab.shouldRender(_container))
                {
                    ActionURL actionURL = tab.getActionURL(_container);
                    NavTree navTree = new NavTree(tab.getText(), actionURL);
                    navTree.setId(tab.getId());
                    tabs.add(navTree);
                }
            });

            return tabs;
        }

        public abstract HttpView getTabView(String tabId) throws Exception;
    }
}
