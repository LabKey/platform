/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.util.Pair;
import org.labkey.api.search.SearchService;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.mvc.Controller;

import java.util.*;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

import junit.framework.TestCase;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
        return new DecimalFormat("0.00#").format(getVersion());
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

    public Collection<? extends WebPartFactory> getWebPartFactories()
    {
        return null;
    }

    public boolean isWebPartFactorySetStale()
    {
        return false;
    }

    public Collection<String> getSummary(Container c)
    {
        return null;
    }

    public Map<String, Class<? extends Controller>> getPageFlowNameToClass()
    {
        return null;
    }

    public Map<Class<? extends Controller>, String> getPageFlowClassToName()
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

    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return null;
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return null;
    }

    public Set<String> getSchemaNames()
    {
        return null;
    }

    public InputStream getResourceStreamFromWebapp(ServletContext ctx, String filename) throws FileNotFoundException
    {
        return null;
    }

    public InputStream getResourceStream(String filename) throws FileNotFoundException
    {
        return null;
    }

    public Pair<InputStream, Long> getResourceStreamIfChanged(String filename, long tsPrevious) throws FileNotFoundException
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
        return new HashSet<String>(Arrays.asList(_dependencies));
    }

    public List<String> getAttributions()
    {
        return null;
    }

    public void setExplodedPath(File path)
    {
    }

    public Set<String> getSqlScripts(@Nullable String schemaName, @NotNull SqlDialect dialect)
    {
        return null;
    }

    public String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        return null;
    }

    public List<ReportDescriptor> getReportDescriptors(String key)
    {
        return null;
    }

    public ReportDescriptor getReportDescriptor(String path)
    {
        return null;
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

    public void enumerateDocuments(@NotNull SearchService.IndexTask task, Container c, Date modifiedSince)
    {
    }

    public @Nullable Collection<String> getJarFilenames()
    {
        return null;
    }
}
