/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.exp.list.ListService;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 3, 2006
 * Time: 8:49:19 PM
 *
 * Interface to define look & feel of a folder. Folders with a folder type other than FolderType.NONE will have a single
 * "tab" owned by the FolderType. This "dashboard" drill down to arbitrary URLs without changing it's look & feel.
 */
public interface FolderType
{
    public static final FolderType NONE = new FolderTypeNone();

    /**
     * Configure the container with whatever active modules and web parts are required for this folder type.
     * Convention is to NOT remove web parts already in the folder.
     */
    public void configureContainer(Container c);

    /**
     * This FolderType is being *removed* as the owner of the container. Clean up anything that you
     * might want. Typically this involves turning off the *permanent* bit for the web parts this
     * FolderType may have set.
     */
    public void unconfigureContainer(Container c);

    /**
     * Name of this folder type. Used internally to track the folder type. Must be consistent across versions.
     * @return name
     */
    public String getName();

    /**
     * Description of this folder type. Used to let users know what to expect.
     */
    public String getDescription();

    /**
     * Label of this folder type. This is what the user sees. Should be a short name such as "MS2" not "MS2 Folder"
     * @return User visible label
     */
    public String getLabel();

    /**
     * URL to start at when navigating to this folder. This is often the same as getTabURL for the portal module, or
     * getTabURL for the "owner" module, but could be any URL to an appropriate starting page.
     * @param c
     * @param u
     * @return URL for "dashboard" of this
     */
    public ActionURL getStartURL(Container c, User u);

    /**
     * Label of the start page. Typically getLabel() + " Dashboard"
     * @return Label of the start page
     */
    public String getStartPageLabel(ViewContext ctx);

    /**
     * Module that *owns* this folder. Used in constructing navigation paths. If current URL's module is NOT part of the owning module
     * extra links will be added to automatically generated nav path
     * @return Owning module. May be null
     */
    Module getDefaultModule();

    /**
     * Return all modules required by this foldertype, INCLUDING the default module if any.
     * @return set
     */
    public Set<Module> getActiveModules();

    /**
     * @return all web parts that must be included in the portal page.
     */
    public List<Portal.WebPart> getRequiredWebParts();

    /**
     * @return all web parts that are recommended for inclusion in the portal page.
     */
    public List<Portal.WebPart> getPreferredWebParts();

    /**
     * Add any management links to the admin popup menu
     * @param adminNavTree popup menu
     * @param container current folder
     */
    public void addManageLinks(NavTree adminNavTree, Container container);

    public AppBar getAppBar(ViewContext context);
    
    /**
     * Folder type that results in an old style "tabbed" folder.
     */
    static class FolderTypeNone implements FolderType
    {
        private FolderTypeNone(){}
        public void configureContainer(Container c) {  }
        public void unconfigureContainer(Container c) {  }
        public String getName() { return "None"; }

        public String getDescription()
        {
            return "Create a tab for each LabKey module you select. Used in older LabKey installations. Note that any LabKey module can also be used from any folder type    via Customize Folder.";
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
            if (null == c.getDefaultModule())
                return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
            return c.getDefaultModule().getTabURL(c, u);
        }

        public void addManageLinks(NavTree adminNavTree, Container container)
        {
            adminNavTree.addChild(new NavTree("Manage Assays", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(container)));
            adminNavTree.addChild(new NavTree("Manage Lists", ListService.get().getManageListsURL(container)));
        }

        public AppBar getAppBar(ViewContext context)
        {
            return null;
        }
    }

}
