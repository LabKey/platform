/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.SimpleFolderTab;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.data.xml.folderType.FolderTabDocument;
import org.labkey.data.xml.folderType.FolderType;
import org.labkey.data.xml.folderType.FolderTypeDocument;
import org.labkey.data.xml.folderType.ModulesDocument;
import org.labkey.data.xml.folderType.Property;
import org.labkey.data.xml.folderType.WebPartDocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Apr 19, 2010
 * Time: 4:20:27 PM
 */
public class SimpleFolderType extends MultiPortalFolderType
{
    private static final Logger LOGGER = Logger.getLogger(SimpleFolderType.class);

    private final String _name;
    private final String _description;
    private final Set<Module> _activeModules;
    private Module _defaultModule;
    protected boolean _hasContainerTabs = false;

    private SimpleFolderType(Resource folderTypeFile, FolderType folderType)
    {
        super(folderType.getName(), folderType.getDescription(), null, null, null, null, folderType.getStartURL());

        FolderType type = parseFile(folderTypeFile);
        _name = type.getName();
        _description = type.getDescription();

        if (type.isSetMenubarEnabled())
            menubarEnabled = type.getMenubarEnabled();

        if (type.getPreferredWebParts() != null)
            preferredParts = createWebParts(type.getPreferredWebParts().getWebPartArray());
        if (type.getRequiredWebParts() != null)
            requiredParts = createWebParts(type.getRequiredWebParts().getWebPartArray());

        if (type.getFolderTabs() != null)
        {
            //if folderTabs are provided, only allow other webparts if they are in the menu
            if (preferredParts != null || requiredParts != null)
            {
                boolean hasError = false;
                if (preferredParts != null)
                {
                    for (Portal.WebPart wp : preferredParts)
                    {
                        if (!wp.getLocation().equals(WebPartFactory.LOCATION_MENUBAR))
                            hasError = true;
                    }
                }
                if (requiredParts != null)
                {
                    for (Portal.WebPart wp : requiredParts)
                    {
                        if (!wp.getLocation().equals(WebPartFactory.LOCATION_MENUBAR))
                            hasError = true;
                    }
                }
                if (hasError)
                    LOGGER.error("Error in " + folderTypeFile.getName() + ".  A folderType that contains folderTabs cannot also provide preferredWebparts or requiredWebparts with locations outside the menubar.");
            }
            _folderTabs = createFolderTabs(type.getFolderTabs().getFolderTabArray());
        }
        else
        {
            _folderTabs = createDefaultTab(type);
        }

        if (_folderTabs.size() > 0)
        {
            _defaultTab = _folderTabs.get(0);
            _folderTabs.get(0).setIsDefaultTab(true);
        }

        setWorkbookType(type.isSetWorkbookType() && type.getWorkbookType());
        setForceAssayUploadIntoWorkbooks(type.getForceAssayUploadIntoWorkbooks());
        String _iconPath = type.getFolderIconPath();
        if(_iconPath != null)
            setFolderIconPath(_iconPath);

        Set<Module> activeModules = new HashSet<>();
        ModulesDocument.Modules modules = type.getModules();
        String[] moduleNames = modules != null ? modules.getModuleNameArray() : null;
        if (moduleNames != null)
        {
            for (String moduleName : moduleNames)
            {
                if (ModuleLoader.getInstance().hasModule(moduleName))
                {
                    Module module = getModule(moduleName);
                    activeModules.add(module);
                }
                else
                {
                    LOGGER.warn("Module '" + moduleName + "' not available for folder type '" + _name + "'");
                }
            }
        }
        _activeModules = activeModules;
        if (type.getDefaultModule() != null)
            _defaultModule = getModule(type.getDefaultModule());
    }

    public static SimpleFolderType create(Resource folderTypeFile)
    {
        FolderType type = parseFile(folderTypeFile);
        return new SimpleFolderType(folderTypeFile, type);
    }

    private static FolderType parseFile(Resource folderTypeFile)
    {
        Logger log = Logger.getLogger(SimpleFolderType.class);
        XmlOptions xmlOptions = new XmlOptions();

        Map<String,String> namespaceMap = new HashMap<>();
        namespaceMap.put("", "http://labkey.org/data/xml/folderType");
        xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

        FolderTypeDocument doc;
        try
        {
            doc = FolderTypeDocument.Factory.parse(folderTypeFile.getInputStream(), xmlOptions);
        }
        catch (XmlException | IOException e)
        {
            log.error(e);
            throw new RuntimeException("Unable to load custom folder type from file " +
                    folderTypeFile.getPath() + ".", e);
        }
        if(null == doc || null == doc.getFolderType())
        {
            IllegalStateException error = new IllegalStateException("Folder type definition file " +
                    folderTypeFile.getPath() + " does not contain a root 'folderType' element!");
            log.error(error);
            throw error;
        }
        return doc.getFolderType();
    }

    private List<FolderTab> createDefaultTab(FolderType type)
    {
        final String caption = type.getName() + " Dashboard";
        FolderTab tab = new FolderTab(Portal.DEFAULT_PORTAL_PAGE_ID, caption)
        {
            @Override
            public ActionURL getURL(Container container, User user)
            {
                return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
            }

            @Override
            public Set<String> getLegacyNames()
            {
                return Collections.singleton(caption);
            }
        };
        return Collections.singletonList(tab);
    }

    private List<FolderTab> createFolderTabs(FolderTabDocument.FolderTab[] references)
    {
        ArrayList<FolderTab> tabs = new ArrayList<>();
        Set<String> tabNames = new CaseInsensitiveHashSet();

        int idx = 1;        // Must start with 1, not 0
        for (FolderTabDocument.FolderTab tab : references)
        {
            if (tabNames.add(tab.getName()))
            {
                FolderTab newTab = new SimpleFolderTab(tab, idx);
                idx++;

                if (newTab.getTabType() == FolderTab.TAB_TYPE.Container)
                    _hasContainerTabs = true;

                StringBuilder stringBuilder = new StringBuilder();
                if (newTab.getTabType() != FolderTab.TAB_TYPE.Container || Container.isLegalName(newTab.getName(), false, stringBuilder))
                {
                    tabs.add(newTab);
                }
                else
                {
                    LOGGER.error(stringBuilder);
                }
            }
            else
            {
                LOGGER.error("Folder type '" + _name + "' defines multiple tabs with the name '" + tab.getName() + "', only the first will be used.");
            }
        }

        return tabs;
    }

    public static List<Portal.WebPart> createWebParts(WebPartDocument.WebPart[] references)
    {
        List<Portal.WebPart> parts = new ArrayList<>();
        HashMap<String, Permission> permissionsMap = new HashMap<>();

        // permissionsMap maps the permissions name (not necessarily unique) to the permission class. We use this so
        // users can specify the name of a permission instead of the fully qualified class name (the unique name).
        // When setting the permission we check this HashMap first, otherwise we check permission unique names.
        for (Role role : RoleManager.getAllRoles())
        {
            for(Class<? extends Permission> permClass : role.getPermissions())
            {
                Permission perm = RoleManager.getPermission(permClass);

                //this is to debug intermittent team city failures and probably should not be merged
                if (perm == null)
                {
                    LOGGER.error("unknown permission class: " + permClass + " from the role: " + role.getName(), new Exception());
                    continue;
                }

                if(!permissionsMap.containsKey(perm.getName()))
                    permissionsMap.put(perm.getName(), perm);
            }
        }

        for (WebPartDocument.WebPart reference : references)
        {
            WebPartFactory factory = Portal.getPortalPart(reference.getName());
            if (factory != null)
            {
                String location = null;
                if (reference.getLocation() != null)
                    location = SimpleWebPartFactory.getInternalLocationName(reference.getLocation().toString());
                Portal.WebPart webPart = factory.createWebPart(location);

                if (reference.getPermission() != null)
                {
                    if (permissionsMap.containsKey(reference.getPermission()))
                        webPart.setPermission(permissionsMap.get(reference.getPermission()).getUniqueName());
                    else if (RoleManager.getPermission(reference.getPermission()) != null)
                        webPart.setPermission(reference.getPermission());
                }
                
                for (Property prop : reference.getPropertyArray())
                    webPart.setProperty(prop.getName(), prop.getValue());
                parts.add(webPart);
            }
            else
                LOGGER.error("Unable to register folder type web parts: web part " + reference.getName() + " does not exist.");
        }
        return parts;
    }

    @Override
    public Module getDefaultModule()
    {
        return _defaultModule;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public Set<Module> getActiveModules()
    {
        return _activeModules;
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        return _folderTabs;
    }


    @Override
    protected String getFolderTitle(ViewContext context)
    {
        return context.getContainer().getTitle();
    }

    @Override
    public String toString()
    {
        return "Folder type: " + _name;
    }

    @Override
    public boolean hasContainerTabs()
    {
        return _hasContainerTabs;
    }
}
