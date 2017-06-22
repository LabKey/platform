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
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.List;
import java.util.Set;

/**
 * Interface to define look & feel of a folder. Folders with a folder type other than FolderType.NONE will have a single
 * "tab" owned by the FolderType. This "dashboard" drill down to arbitrary URLs without changing its look & feel.
 *
 * User: Mark Igra
 * Date: Aug 3, 2006
 */
public interface FolderType
{
    /** Almost exclusively used on very old installs where folders were set up prior to the introduction of folder types */
    FolderType NONE = new CustomFolderType();

    /**
     * Configure the container with whatever active modules and web parts are required for this folder type.
     * Convention is to NOT remove web parts already in the folder.
     */
    void configureContainer(Container c, User user);

    /**
     * This FolderType is being *removed* as the owner of the container. Clean up anything that you
     * might want. Typically this involves turning off the *permanent* bit for the web parts this
     * FolderType may have set.
     */
    void unconfigureContainer(Container c, User user);

    /**
     * Name of this folder type. Used internally to track the folder type. Must be consistent across versions.
     * @return name
     */
    String getName();

    /** Old names that we should be backwards compatible with */
    @NotNull Set<String> getLegacyNames();

    /**
     * @return whether or not an admin is allowed to customize the set of tabs for this folder.
     * For folder types with only one tab, or a set backed by some other config (such as the active set of modules),
     * return false;
     */
    boolean hasConfigurableTabs();

    /** If configurable, reset to the default set, throwing away tab configuration if it's customized. */
    void resetDefaultTabs(Container c);

    /**
     * Description of this folder type. Used to let users know what to expect.
     */
    String getDescription();

    /**
     * If true, rather than importing into the current container, assay upload will ask the user to create a workbook or pick an existing one and import using this container
     */
    boolean getForceAssayUploadIntoWorkbooks();

    /**
     * Label of this folder type. This is what the user sees. Should be a short name such as "MS2" not "MS2 Folder"
     * @return User visible label
     */
    String getLabel();

    /**
     * The filepath of the icon for this folder type, relative to the root of the webapp.
     * @return File path to the icon
     */
    @NotNull
    String getFolderIconPath();

    /**
     * URL to start at when navigating to this folder. This is often the same as getTabURL for the portal module, or
     * getTabURL for the "owner" module, but could be any URL to an appropriate starting page.
     * @return URL for "dashboard" of this
     */
    ActionURL getStartURL(Container c, User u);

    /**
     * Label of the start page. Typically getLabel() + " Dashboard"
     * @return Label of the start page
     */
    String getStartPageLabel(ViewContext ctx);

    /**
     * Help topic of the start page.
     */
    HelpTopic getHelpTopic();

    /**
     * Whether the menu bar should be shown by default.
     */
    boolean isMenubarEnabled();

    /**
     * Module that *owns* this folder. Used in constructing navigation paths. If current URL's module is NOT part of the owning module
     * extra links will be added to automatically generated nav path
     * @return Owning module. May be null
     */
    Module getDefaultModule();

    /**
     * Return all modules required by this foldertype, INCLUDING the default module if any.  Note: in order to find the
     * requiredModules for a given container, you should call getRequiredModules() on that container rather than rely
     * on the folderType.
     * @return set
     */
    Set<Module> getActiveModules();

    /**
     * @return all web parts that must be included in the portal page.
     */
    List<Portal.WebPart> getRequiredWebParts();

    /**
     * @return all web parts that are recommended for inclusion in the portal page.
     */
    List<Portal.WebPart> getPreferredWebParts();

    /**
     * Add links to the admin popup menu. Implementors must ensure that user has the required permissions in the container
     * before adding links. User might not be an administrator in this container (could be a troubleshooter, for example).
     *
     * @param adminNavTree popup menu
     * @param container current folder
     * @param user current user
     */
    void addManageLinks(NavTree adminNavTree, Container container, User user);

    @NotNull
    AppBar getAppBar(ViewContext context, PageConfig pageConfig);

    List<FolderTab> getDefaultTabs();

    /** @return The default tab to select, which defaults to the first (including for non-tabbed folders) */
    FolderTab getDefaultTab();

    /** @return the folder tab in htis folder type's default tabs whose name matches the tabName; null if none found */
    @Nullable
    FolderTab findTab(String tabName);

    /** @return whether this is intended to be used exclusively for workbooks */
    boolean isWorkbookType();

    /** @return whether this is allowed only as a project */
    boolean isProjectOnlyType();

    /** @return whether this has container tabs, where subfolders are exposed as tabs in the parent container */
    boolean hasContainerTabs();

    /**
     * @return The pageId, which is primarily intended to support tabbed folders.  By default it will return
     * Portal.DEFAULT_PORTAL_PAGE_ID
     */
    String getDefaultPageId(ViewContext ctx);

    /**
     * Clear active portal page if there is one
     */
    void clearActivePortalPage();

    /**
     * @return any additional setup steps for the container creation wizard.
     */
    @NotNull
    List<NavTree> getExtraSetupSteps(Container c);
}

