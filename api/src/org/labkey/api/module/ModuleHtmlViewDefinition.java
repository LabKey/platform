/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.resource.Resource;
import org.labkey.api.resource.TestResource;
import org.labkey.api.security.ACL;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.labkey.data.xml.view.PermissionClassListType;
import org.labkey.data.xml.view.PermissionClassType;
import org.labkey.data.xml.view.PermissionType;
import org.labkey.data.xml.view.PermissionsListType;
import org.labkey.data.xml.view.ViewDocument;
import org.labkey.data.xml.view.ViewType;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Metadata for a file-based html view in a module, typically supplied by a .view.xml file in a module's
 * ./resources/views directory. This is separate from ModuleHtmlView so that it can be cached.
 */
public class ModuleHtmlViewDefinition
{
    private static final Logger _log = LogManager.getLogger(ModuleHtmlViewDefinition.class);

    public static final String HTML_VIEW_EXTENSION = ".html";
    public static final String VIEW_METADATA_EXTENSION = ".view.xml";

    private final String _name;
    private final List<Supplier<ClientDependency>> _clientDependencySuppliers = new LinkedList<>();
    private final Set<Class<? extends Permission>> _requiredPermissionClasses = new HashSet<>();

    private HtmlString _html;
    private int _requiredPerms = ACL.PERM_READ;  //8550: Default perms for simple module views should be read
    private boolean _requiresLogin = false;
    private ViewType _viewDef = null;

    public ModuleHtmlViewDefinition(Resource r)
    {
        _name = r.getName().substring(0, r.getName().length() - HTML_VIEW_EXTENSION.length());

        try (InputStream is = r.getInputStream())
        {
            if (is != null)
            {
                String html = IOUtils.toString(is, StringUtilsLabKey.DEFAULT_CHARSET);
                char ch = !html.isEmpty() ? html.charAt(0) : 0;
                if (ch == 0xfffe || ch == 0xfeff)
                    html = html.substring(1);
                _html = HtmlString.unsafe(html);
            }
        }
        catch (IOException e)
        {
            throw new MinorConfigurationException("Error trying to read HTML content from " + r, e);
        }

        Resource parent = r.parent();
        if (parent != null)
        {
            Resource metadataResource = parent.find(Path.toPathPart(_name + VIEW_METADATA_EXTENSION));
            if (metadataResource != null)
                parseMetadata(metadataResource, true);
        }
    }

    public ModuleHtmlViewDefinition(String name, HtmlString body, @Nullable Resource metadataResource, boolean logErrors)
    {
        _name = name;
        _html = body;
        if (metadataResource != null)
            parseMetadata(metadataResource, logErrors);
    }

    private void parseMetadata(Resource r, boolean logErrors)
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
                XmlBeansUtil.validateXmlDocument(viewDoc, r.toString());
                _viewDef = viewDoc.getView();
                if (null != _viewDef)
                {
                    calculatePermissions(r.toString());
                    // We will reload to pick up changes, so don't just keep adding to the same set of dependencies.
                    // Start over each time and flip the collection all at once.
                    addResources();
                    addModuleContext();
                }
            }
            catch (Exception e)
            {
                if (logErrors)
                    _log.error("Error trying to read and parse the metadata XML content from {}", r, e);
                _html = HtmlStringBuilder.of(HtmlString.unsafe("<p class='labkey-error'>"))
                    .append("The following exception occurred while attempting to load view metadata from ")
                    .append(r.toString()).append(": ").append(e.getMessage())
                    .endTag("p")
                    .getHtmlString();
            }
        }
    }

    protected String getTitleFromName(String name)
    {
        //convert camel case to separate words
        return ColumnInfo.labelFromName(name);
    }

    private enum PermissionEnum
    {
        login(0),
        read(ACL.PERM_READ),
        insert(ACL.PERM_INSERT),
        update(ACL.PERM_UPDATE),
        delete(ACL.PERM_DELETE),
        admin(ACL.PERM_ADMIN),
        none(ACL.PERM_NONE);

        private final int _value;

        PermissionEnum(int value)
        {
            _value = value;
        }

        private int toInt()
        {
            return _value;
        }
    }

    protected void calculatePermissions(String resource) throws ViewDefinitionException
    {
        boolean allowAcls = OptionalFeatureService.get().isFeatureEnabled(ACL.RESTORE_USE_OF_ACLS);

        PermissionsListType permsList = _viewDef.getPermissions();
        if (permsList != null && permsList.getPermissionArray() != null)
        {
            if (allowAcls)
                _log.warn("The \"<permissions>\" element used in \"{}\" is deprecated and support will be removed in LabKey Server 24.12! Migrate uses to \"<requiresPermissions>\", \"<requiresNoPermission>\", or \"<requiresLogin>\".", resource);
            else
                throw new ViewDefinitionException("The \"<permissions>\" element is no longer supported. Migrate uses to \"<requiresPermissions>\", \"<requiresNoPermission>\", or \"<requiresLogin>\".");

            for (PermissionType permEntry : permsList.getPermissionArray())
            {
                PermissionEnum perm = PermissionEnum.valueOf(permEntry.getName().toString());

                if (PermissionEnum.login == perm)
                    _requiresLogin = true;
                else if (PermissionEnum.none == perm)
                    _requiredPerms = perm.toInt();
                else
                    _requiredPerms |= perm.toInt();
            }
        }

        if (_viewDef.isSetPermissionClasses() || _viewDef.isSetRequiresPermissions())
        {
            if (_viewDef.isSetRequiresNoPermission())
                throw new ViewDefinitionException("The <requiresNoPermission/> element can't be specified along with other permission elements");

            // <permissionClasses> and <requiresPermissions> are synonyms, so add all permission classes from both.
            // For now, allow but warn for empty <permissionClasses> element; throw for empty <requiresPermissions> element.
            if (_viewDef.isSetPermissionClasses())
                addPermissionClasses(_viewDef.getPermissionClasses(), resource, !allowAcls);

            if (_viewDef.isSetRequiresPermissions())
                addPermissionClasses(_viewDef.getRequiresPermissions(), resource, true);
        }
        else if (!_viewDef.isSetRequiresNoPermission())
        {
            // No permission elements are present, so assume read permissions are required
            addPermissionClass(ReadPermission.class.getName());
        }
        else
        {
            // No permissions case: for now, clear out old-style perms as well
            _requiredPerms = PermissionEnum.none.toInt();
        }

        if (_viewDef.isSetRequiresLogin())
            _requiresLogin = true;
    }

    private void addPermissionClasses(PermissionClassListType permClassList, String resource, boolean throwOnEmpty) throws ViewDefinitionException
    {
        assert permClassList != null && permClassList.getPermissionClassArray() != null;

        if (permClassList.getPermissionClassArray().length == 0)
        {
            if (throwOnEmpty)
                throw new ViewDefinitionException("Empty permissions class lists are not allowed");
            else
                _log.warn("Empty permissions class list is specified in \"{}\". Empty class lists are not allowed; this will be " +
                    "enforced in LabKey Server 24.8. Specify one or more permission classes or remove the empty element.", resource);
        }

        for (PermissionClassType className : permClassList.getPermissionClassArray())
        {
            addPermissionClass(className.getName());
        }
    }

    private void addPermissionClass(String permissionClassName)
    {
        try
        {
            Class<?> c = Class.forName(permissionClassName);
            if (Permission.class.isAssignableFrom(c))
            {
                _requiredPermissionClasses.add((Class<? extends Permission>)c);
            }
            else
            {
                _log.warn("Resolved class {} from view: {}, but it was not of the expected type, {}", permissionClassName, getName(), Permission.class);
            }
        }
        catch (ClassNotFoundException e)
        {
            _log.warn("Could not find permission class {} for view: {}", permissionClassName, getName());
        }
    }

    protected void addResources()
    {
        if (_viewDef.isSetDependencies())
            _clientDependencySuppliers.addAll(ClientDependency.getSuppliers(_viewDef.getDependencies().getDependencyArray(), getName()));
    }

    protected void addModuleContext()
    {
        if (_viewDef.isSetRequiredModuleContext())
            _clientDependencySuppliers.addAll(ClientDependency.getSuppliers(_viewDef.getRequiredModuleContext().getModuleArray(), _name, x->true));
    }

    public String getName()
    {
        return _name;
    }

    public HtmlString getHtml()
    {
        return _html;
    }

    public String getTitle()
    {
        return null != _viewDef && null != _viewDef.getTitle() ? _viewDef.getTitle() : getTitleFromName(_name);
    }

    public boolean isAppView()
    {
        return null != _viewDef && _viewDef.isSetIsAppView() && _viewDef.getIsAppView();
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
        return ClientDependency.getClientDependencySet(_clientDependencySuppliers);
    }

    public static class ViewDefinitionException extends Exception
    {
        public ViewDefinitionException(String message)
        {
            super(message);
        }
    }

    public static class TestCase extends Assert
    {
        private static final String HEADER = """
            <view xmlns="http://labkey.org/data/xml/view" title="Test view.xml file" frame="portal">
            """;
        private static final String FOOTER = "\n</view>";

        @Test
        public void testPermissions()
        {
            if (OptionalFeatureService.get().isFeatureEnabled(ACL.RESTORE_USE_OF_ACLS))
            {
                testPermissions(null, ACL.PERM_READ, Set.of(), false); // No .view.xml file -> read
                testPermissions("", ACL.PERM_READ, Set.of(ReadPermission.class), false); // No permission elements -> read
                testPermissions("<requiresLogin/>", ACL.PERM_READ, Set.of(ReadPermission.class), true); // read + login
                testPermissions("<requiresNoPermission/>", ACL.PERM_NONE, Set.of(), false); // no permissions
                testPermissions("""
                    <requiresNoPermission/>
                    <requiresLogin/>""", ACL.PERM_NONE, Set.of(), true); // just login required
                testPermissions("<permissionClasses/>", ACL.PERM_READ, Set.of(), false); // allow empty element for now
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </permissionClasses>""", ACL.PERM_READ, Set.of(ReadPermission.class), false);
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </permissionClasses>
                    <requiresLogin/>""", ACL.PERM_READ, Set.of(ReadPermission.class), true);
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                    </permissionClasses>""", ACL.PERM_READ, Set.of(InsertPermission.class), false);
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                        <permissionClass name="org.labkey.api.security.permissions.UpdatePermission"/>
                    </permissionClasses>""", ACL.PERM_READ, Set.of(InsertPermission.class, UpdatePermission.class), false);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </requiresPermissions>""", ACL.PERM_READ, Set.of(ReadPermission.class), false);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </requiresPermissions>
                    <requiresLogin/>""", ACL.PERM_READ, Set.of(ReadPermission.class), true);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                    </requiresPermissions>""", ACL.PERM_READ, Set.of(InsertPermission.class), false);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                        <permissionClass name="org.labkey.api.security.permissions.UpdatePermission"/>
                    </requiresPermissions>""", ACL.PERM_READ, Set.of(InsertPermission.class, UpdatePermission.class), false);
            }
            else
            {
                testPermissions(null, Set.of(), false); // No .view.xml file -> read
                testPermissions("", Set.of(ReadPermission.class), false); // No permission elements -> read
                testPermissions("<requiresLogin/>", Set.of(ReadPermission.class), true); // read + login
                testPermissions("<requiresNoPermission/>", Set.of(), false); // no permissions
                testPermissions("""
                    <requiresNoPermission/>
                    <requiresLogin/>""", Set.of(), true); // just login required
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </permissionClasses>""", Set.of(ReadPermission.class), false);
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </permissionClasses>
                    <requiresLogin/>""", Set.of(ReadPermission.class), true);
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                    </permissionClasses>""", Set.of(InsertPermission.class), false);
                testPermissions("""
                    <permissionClasses>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                        <permissionClass name="org.labkey.api.security.permissions.UpdatePermission"/>
                    </permissionClasses>""", Set.of(InsertPermission.class, UpdatePermission.class), false);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </requiresPermissions>""", Set.of(ReadPermission.class), false);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                    </requiresPermissions>
                    <requiresLogin/>""", Set.of(ReadPermission.class), true);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                    </requiresPermissions>""", Set.of(InsertPermission.class), false);
                testPermissions("""
                    <requiresPermissions>
                        <permissionClass name="org.labkey.api.security.permissions.InsertPermission"/>
                        <permissionClass name="org.labkey.api.security.permissions.UpdatePermission"/>
                    </requiresPermissions>""", Set.of(InsertPermission.class, UpdatePermission.class), false);

                testBadPermissions("<permissionClasses/>", "Empty permissions class lists are not allowed");
            }

            testBadPermissions("<requiresPermissions/>", "Empty permissions class lists are not allowed");
            testBadPermissions("""
                <requiresPermissions>
                    <permissionClass name="org.labkey.api.security.permissions.ReadPermission"/>
                </requiresPermissions>
                <requiresNoPermission/>""", "The <requiresNoPermission/> element can't be specified along with other permission elements");
        }

        private void testPermissions(@Nullable String permissions, int expectedPerms, Set<Class<? extends Permission>> expectedPermissionClasses, boolean expectedLogin)
        {
            ModuleHtmlViewDefinition def = new ModuleHtmlViewDefinition("test.html", HtmlString.of("Success!"), permissions != null ? getViewXmlResource(permissions) : null, true);
            assertEquals(expectedPerms, def.getRequiredPerms());
            assertEquals(expectedPermissionClasses, def.getRequiredPermissionClasses());
            assertEquals(expectedLogin, def.isRequiresLogin());
            assertEquals("Success!", def.getHtml().toText());
        }

        private void testPermissions(@Nullable String permissions, Set<Class<? extends Permission>> expectedPermissionClasses, boolean expectedLogin)
        {
            ModuleHtmlViewDefinition def = new ModuleHtmlViewDefinition("test.html", HtmlString.of("Success!"), permissions != null ? getViewXmlResource(permissions) : null, true);
            assertEquals(expectedPermissionClasses, def.getRequiredPermissionClasses());
            assertEquals(expectedLogin, def.isRequiresLogin());
            assertEquals("Success!", def.getHtml().toText());
        }

        private void testBadPermissions(@Nullable String permissions, String error)
        {
            ModuleHtmlViewDefinition def = new ModuleHtmlViewDefinition("test.html", HtmlString.of("Success!"), permissions != null ? getViewXmlResource(permissions) : null, false);
            String errorText = def.getHtml().toText();
            assertTrue("Error \"" + errorText + "\" does not contain \"" + error + "\"", errorText.contains(error));
        }

        private Resource getViewXmlResource(String permissions)
        {
            return new TestResource("test.view.xml", HEADER + permissions + FOOTER);
        }
    }
}
