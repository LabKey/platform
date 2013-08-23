/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: Dave
* Date: Dec 2, 2008
* Time: 9:58:18 AM
*/

/**
 * A module that does nothing. Used for automated tests.
 */
public class MockModule implements Module
{
    private String _name;
    private double _version;
    private String[] _dependencies;

    public MockModule(String name, String... dependencies)
    {
        this(name, 0, dependencies);
    }

    public MockModule(String name, double version, String... dependencies)
    {
        _name = name;
        _version = version;
        _dependencies = dependencies;
    }

    public int compareTo(Module m)
    {
        return (m instanceof MockModule) ? 0 : 1;
    }

    public void initialize()
    {
    }

    public String getName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    public String getTabName(ViewContext context)
    {
        return _name;
    }

    public double getVersion()
    {
        return _version;
    }

    public String getFormattedVersion()
    {
        return ModuleContext.formatVersion(getVersion());
    }

    public void beforeUpdate(ModuleContext moduleContext)
    {
    }

    public void versionUpdate(ModuleContext moduleContext) throws Exception
    {
    }

    public void afterUpdate(ModuleContext moduleContext)
    {
    }

    public void startup(ModuleContext moduleContext)
    {
    }

    public void destroy()
    {
    }

    @NotNull
    public Collection<WebPartFactory> getWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean isWebPartFactorySetStale()
    {
        return false;
    }

    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public Map<String, Class<? extends Controller>> getControllerNameToClass()
    {
        return null;
    }

    public Map<Class<? extends Controller>, String> getControllerClassToName()
    {
        return null;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        return null;
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Collections.emptySet();
    }

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

    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    public Resolver getModuleResolver()
    {
        return null;
    }
    
    public Resource getModuleResource(Path path)
    {
        return null;
    }

    public Resource getModuleResource(String path)
    {
        return null;
    }

    public InputStream getResourceStream(String filename) throws FileNotFoundException
    {
        return null;
    }

    public String getSourcePath()
    {
        return null;
    }

    public String getBuildPath()
    {
        return null;
    }

    public String getSvnRevision()
    {
        return null;
    }

    public String getSvnUrl()
    {
        return null;
    }

    public Map<String, String> getProperties()
    {
        return null;
    }

    public Set<String> getModuleDependenciesAsSet()
    {
        return new HashSet<>(Arrays.asList(_dependencies));
    }

    public List<String> getAttributions()
    {
        return null;
    }

    public void setExplodedPath(File path)
    {
    }

    public Set<String> getSqlScripts(@Nullable DbSchema schema)
    {
        return null;
    }

    public String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        return null;
    }

    @Nullable
    public ReportDescriptor getCachedReport(Path path)
    {
        return null;
    }

    public void cacheReport(Path path, ReportDescriptor descriptor)
    {
    }

    public Set<Resource> getReportFiles()
    {
        return Collections.emptySet();
    }

    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        return Collections.emptySet();
    }

    public File getExplodedPath()
    {
        return null;
    }

    public void dispatch(HttpServletRequest request, HttpServletResponse response, ActionURL url) throws ServletException, IOException
    {
    }

    @NotNull
    public List<File> getStaticFileDirectories()
    {
        return Collections.emptyList();
    }

    public @Nullable Collection<String> getJarFilenames()
    {
        return null;
    }

    @Override
    public boolean isAutoUninstall()
    {
        return false;
    }

    @Override
    public void addDeferredUpgradeTask(Method task)
    {
    }

    @Override
    public void runDeferredUpgradeTasks(ModuleContext context)
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

    public Map<String, ModuleProperty> getModuleProperties()
    {
        return new HashMap<>();
    }

    public JSONObject getPageContextJson(User u, Container c)
    {
        return new JSONObject();
    }

    public LinkedHashSet<ClientDependency> getClientDependencies(Container c, User u)
    {
        return new LinkedHashSet<>();
    }

    @Override
    public Set<SupportedDatabase> getSupportedDatabasesSet()
    {
        return DefaultModule.ALL_DATABASES;
    }
}
