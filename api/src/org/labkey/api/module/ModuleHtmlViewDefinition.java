/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.ACL;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.data.xml.view.DependenciesType;
import org.labkey.data.xml.view.ModuleContextType;
import org.labkey.data.xml.view.PermissionClassListType;
import org.labkey.data.xml.view.PermissionClassType;
import org.labkey.data.xml.view.PermissionType;
import org.labkey.data.xml.view.PermissionsListType;
import org.labkey.data.xml.view.RequiredModuleType;
import org.labkey.data.xml.view.ViewDocument;
import org.labkey.data.xml.view.ViewType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Metadata for a file-based html view in a module, supplied by a .view.xml file in a module's ./resources/views directory.
 * This is separate from ModuleHtmlView so that it can be cached
 * User: Dave
 * Date: Jan 26, 2009
 */
public class ModuleHtmlViewDefinition
{
    private static final Logger _log = Logger.getLogger(ModuleHtmlViewDefinition.class);

    public static final String HTML_VIEW_EXTENSION = ".html";
    public static final String VIEW_METADATA_EXTENSION = ".view.xml";

    private final String _name;

    private String _html;
    private int _requiredPerms = ACL.PERM_READ;  //8550: Default perms for simple module views should be read
    private Set<Class<? extends Permission>> _requiredPermissionClasses = new HashSet<>();
    private boolean _requiresLogin = false;
    private Set<ClientDependency> _clientDependencies = new LinkedHashSet<>();
    private ViewType _viewDef = null;

    public ModuleHtmlViewDefinition(Resource r)
    {
        _name = r.getName().substring(0, r.getName().length() - HTML_VIEW_EXTENSION.length());

        try (InputStream is = r.getInputStream())
        {
            if (is != null)
            {
                _html = IOUtils.toString(is, StringUtilsLabKey.DEFAULT_CHARSET);
                char ch = _html.length() > 0 ? _html.charAt(0) : 0;
                if (ch == 0xfffe || ch == 0xfeff)
                    _html = _html.substring(1);
            }
        }
        catch (IOException e)
        {
            throw new MinorConfigurationException("Error trying to read HTML content from " + r.getPath(), e);
        }

        Resource parent = r.parent();
        if (parent != null)
        {
            Resource metadataResource = parent.find(_name + VIEW_METADATA_EXTENSION);
            if (metadataResource != null)
                parseMetadata(metadataResource);
        }
    }

    private void parseMetadata(Resource r)
    {
        if (r.exists())
        {
            try
            {
                XmlOptions xmlOptions = new XmlOptions();
                Map<String, String> namespaceMap = new HashMap<>();
                namespaceMap.put("", "http://labkey.org/data/xml/view");
                xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

                ViewDocument viewDoc = ViewDocument.Factory.parse(r.getInputStream(), xmlOptions);
                if (AppProps.getInstance().isDevMode())
                {
                    try
                    {
                        XmlBeansUtil.validateXmlDocument(viewDoc, r.getPath().toString());
                    }
                    catch (XmlValidationException e)
                    {
                        _log.error("View XML file failed validation: " + r.getPath() + ". " + e.getDetails());
                    }
                }
                _viewDef = viewDoc.getView();
                if (null != _viewDef)
                {
                    calculatePermissions();
                    // We will reload to pick up changes, so don't just keep adding to the same set of dependencies.
                    // Start over each time and flip the collection all at once.
                    Set<ClientDependency> newClientDependencies = new LinkedHashSet<>();
                    newClientDependencies.addAll(addResources());
                    newClientDependencies.addAll(addModuleContext());
                    _clientDependencies = newClientDependencies;
                }
            }
            catch(Exception e)
            {
                _log.error("Error trying to read and parse the metadata XML content from " + r.getPath(), e);
                _html = "<p class='labkey-error'>The following exception occurred while attempting to load view metadata from "
                         + PageFlowUtil.filter(r.getPath()) + ": "
                         + e.getMessage() + "</p>";
            }
        }
    }

    protected String getTitleFromName(String name)
    {
        //convert camel case to separate words
        return ColumnInfo.labelFromName(name);
    }

    protected void calculatePermissions()
    {
        PermissionsListType permsList = _viewDef.getPermissions();
        if (permsList != null && permsList.getPermissionArray() != null)
        {
            for (PermissionType permEntry : permsList.getPermissionArray())
            {
                SimpleAction.PermissionEnum perm = SimpleAction.PermissionEnum.valueOf(permEntry.getName().toString());

                if (SimpleAction.PermissionEnum.login == perm)
                    _requiresLogin = true;
                else if (SimpleAction.PermissionEnum.none == perm)
                    _requiredPerms = perm.toInt();
                else if (null != perm)
                    _requiredPerms |= perm.toInt();
            }
        }

        PermissionClassListType permClassList = _viewDef.getPermissionClasses();
        if (permClassList != null && permClassList.getPermissionClassArray() != null)
        {
            for (PermissionClassType className : permClassList.getPermissionClassArray())
            {
                addPermissionClass(className.getName());
            }
        }
    }

    private void addPermissionClass(String permissionClassName)
    {
        try
        {
            Class c = Class.forName(permissionClassName);
            if (Permission.class.isAssignableFrom(c))
            {
                _requiredPermissionClasses.add((Class<? extends Permission>)c);
            }
            else
            {
                _log.warn("Resolved class " + permissionClassName + " from view: " + getName() + ", but it was not of the expected type, " + Permission.class);
            }
        }
        catch (ClassNotFoundException e)
        {
            _log.warn("Could not find permission class " + permissionClassName + " for view: " + getName());
        }
    }

    protected Set<ClientDependency> addResources()
    {
        DependenciesType resourcesList = _viewDef.getDependencies();
        if (null == resourcesList)
            return Collections.emptySet();

        DependencyType[] resources = resourcesList.getDependencyArray();
        if (null == resources)
            return Collections.emptySet();

        Set<ClientDependency> result = new LinkedHashSet<>();
        for (DependencyType r : resources)
        {
            ClientDependency cr = ClientDependency.fromXML(r);
            if (cr != null)
                result.add(cr);
            else
                _log.error("Unable to process <dependency> for file: " + getName());
        }
        return result;
    }

    protected Set<ClientDependency> addModuleContext()
    {
        ModuleContextType modulesList = _viewDef.getRequiredModuleContext();
        if (null == modulesList)
            return Collections.emptySet();

        RequiredModuleType[] modules = modulesList.getRequiredModuleArray();
        if (null == modules)
            return Collections.emptySet();

        Set<ClientDependency> result = new HashSet<>();
        for (RequiredModuleType mn : modules)
        {
            ClientDependency cr = ClientDependency.fromModuleName(mn.getName());
            result.add(cr);
        }
        return result;
    }

    public String getName()
    {
        return _name;
    }

    public String getHtml()
    {
        return _html;
    }

    public String getTitle()
    {
        return null != _viewDef && null != _viewDef.getTitle() ? _viewDef.getTitle() : getTitleFromName(_name);
    }

    public int getRequiredPerms()
    {
        return _requiredPerms;
    }
    
    public Set<Class<? extends Permission>> getRequiredPermissionClasses()
    {
        return _requiredPermissionClasses;    
    }           

    public boolean isRequiresLogin()
    {
        return _requiresLogin;
    }

    public WebPartView.FrameType getFrameType()
    {
        return null != _viewDef && null != _viewDef.getFrame() ?
                WebPartView.FrameType.valueOf(_viewDef.getFrame().toString().toUpperCase()) : null;
    }

    public PageConfig.Template getPageTemplate()
    {
        return null != _viewDef && null != _viewDef.getTemplate() ?
            PageConfig.Template.valueOf(StringUtils.capitalize(_viewDef.getTemplate().toString().toLowerCase())) : null;
    }

    public Set<ClientDependency> getClientDependencies()
    {
        return _clientDependencies;
    }
}
