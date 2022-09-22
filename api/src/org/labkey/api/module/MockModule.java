/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.OlapSchemaInfo;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.ContainerUser;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A module that does nothing. Used for unit and integration tests.
 * User: Dave
 * Date: Dec 2, 2008
 */
public class MockModule implements Module
{
    private final String _name;
    private final Double _schemaVersion;
    private final String[] _dependencies;

    public MockModule(String name, String... dependencies)
    {
        this(name, 0.0, dependencies);
    }

    public MockModule(String name, Double schemaVersion, String... dependencies)
    {
        _name = name;
        _schemaVersion = schemaVersion;
        _dependencies = dependencies;
    }

    @Override
    public void initialize()
    {
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return _schemaVersion;
    }

    @Override
    public String getReleaseVersion()
    {
        return null;
    }

    @Override
    public String getLabel()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Nullable
    @Override
    public String getUrl()
    {
        return null;
    }

    @Nullable
    @Override
    public String getAuthor()
    {
        return null;
    }

    @Nullable
    @Override
    public String getMaintainer()
    {
        return null;
    }

    @Nullable
    @Override
    public String getOrganization()
    {
        return null;
    }

    @Nullable
    @Override
    public String getOrganizationUrl()
    {
        return null;
    }

    @Nullable
    @Override
    public String getLicense()
    {
        return null;
    }

    @Nullable
    @Override
    public String getLicenseUrl()
    {
        return null;
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return _name;
    }

    @Override
    public void beforeUpdate(ModuleContext moduleContext)
    {
    }

    @Override
    public void versionUpdate(ModuleContext moduleContext)
    {
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
    }

    @Override
    public void startup(ModuleContext moduleContext)
    {
    }

    @Override
    public void destroy()
    {
    }

    @Override
    @NotNull
    public Collection<WebPartFactory> getWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Class<? extends Controller>> getControllerNameToClass()
    {
        return null;
    }

    @Override
    public Map<Class<? extends Controller>, String> getControllerClassToName()
    {
        return null;
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        return null;
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return Collections.emptySet();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<String> getProvisionedSchemaNames()
    {
        return Collections.emptySet();
    }

    @Override
    public ModuleResourceResolver getModuleResolver()
    {
        return null;
    }
    
    @Override
    public Resource getModuleResource(Path path)
    {
        return null;
    }

    @Override
    public Resource getModuleResource(String path)
    {
        return null;
    }

    @Override
    public InputStream getResourceStream(String filename)
    {
        return null;
    }

    @Nullable
    @Override
    public String getBuildType()
    {
        return ModuleLoader.PRODUCTION_BUILD_TYPE;
    }

    @Override
    public String getSourcePath()
    {
        return null;
    }

    @Override
    public String getBuildPath()
    {
        return null;
    }

    @Override
    public String getVcsRevision()
    {
        return null;
    }

    @Override
    public String getBuildNumber()
    {
        return null;
    }

    @Override
    public String getVcsUrl()
    {
        return null;
    }

    @Override
    public String getVcsBranch()
    {
        return null;
    }

    @Override
    public String getVcsTag()
    {
        return null;
    }

    @Override
    public boolean shouldManageVersion()
    {
        return false;
    }

    @Override
    public Map<String, String> getProperties()
    {
        return null;
    }

    @Override
    public Set<String> getModuleDependenciesAsSet()
    {
        return Set.of(_dependencies);
    }

    @Override
    public void setExplodedPath(File path)
    {
    }

    @Override
    public Set<String> getSqlScripts(@Nullable DbSchema schema)
    {
        return null;
    }

    @Override
    public String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        return null;
    }

    @Override
    public File getExplodedPath()
    {
        return null;
    }

    @Override
    public @Nullable File getZippedPath()
    {
        return null;
    }

    @Override
    public void setZippedPath(File zipped)
    {

    }

    @Override
    public void dispatch(HttpServletRequest request, HttpServletResponse response, ActionURL url)
    {
    }

    @Override
    @NotNull
    public List<File> getStaticFileDirectories()
    {
        return Collections.emptyList();
    }

    @Override
    public @NotNull Collection<String> getJarFilenames()
    {
        return Collections.emptySet();
    }

    @Override
    public boolean isAutoUninstall()
    {
        return false;
    }

    @Override
    public void addDeferredUpgradeRunnable(String description, Runnable runnable)
    {
    }

    @Override
    public void runDeferredUpgradeRunnables()
    {
    }

    @Override
    public Set<Module> getResolvedModuleDependencies()
    {
        Set<Module> modules = new HashSet<>();
        Module module;
        for(String m : _dependencies)
        {
            module = ModuleLoader.getInstance().getModule(m);
            if(module != null)
            {
                modules.add(module);
                modules.addAll(module.getResolvedModuleDependencies());
            }
        }
        return modules;
    }

    @Override
    public Controller getController(HttpServletRequest request, String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, ModuleProperty> getModuleProperties()
    {
        return new HashMap<>();
    }

    @Override
    public JSONObject getPageContextJson(ContainerUser context)
    {
        return new JSONObject();
    }

    @Override
    public @NotNull List<Supplier<ClientDependency>> getClientDependencies(Container c)
    {
        return new LinkedList<>();
    }

    @NotNull
    @Override
    public Set<SupportedDatabase> getSupportedDatabasesSet()
    {
        return DefaultModule.ALL_DATABASES;
    }

    @Nullable
    @Override
    public UpgradeCode getUpgradeCode()
    {
        return null;
    }

    @Override
    public String getResourcePath()
    {
        return null;
    }

    @Override
    public boolean getRequireSitePermission()
    {
        return false;
    }

    @Override
    public OlapSchemaInfo getOlapSchemaInfo()
    {
        return null;
    }

    @Override
    public DbSchema createModuleDbSchema(DbScope scope, String metaDataName, Map<String, SchemaTableInfoFactory> tableInfoFactoryMap)
    {
        return null;
    }

    @Override
    public String getDatabaseSchemaName(String fullyQualifiedSchemaName)
    {
        return null;
    }

    @Override
    public void lock()
    {
    }
}
