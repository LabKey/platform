/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import junit.framework.TestCase;
import org.labkey.api.data.*;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.security.User;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.view.*;
import org.labkey.api.settings.AppProps;
import org.labkey.common.util.Pair;
import org.springframework.web.servlet.mvc.Controller;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletContext;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * This handles modules that are implemented using Beehive controllers
 *
 * User: migra
 * Date: Jul 14, 2005
 * Time: 11:42:09 AM
 */
public abstract class DefaultModule implements Module
{
    public static final String CORE_MODULE_NAME = "Core";
    private static final Set<Class> INSTANTIATED_MODULES = new HashSet<Class>();

    private final Map<String, Class> _pageFlowNameToClass = new LinkedHashMap<String, Class>();
    private final Map<Class, String> _pageFlowClassToName = new HashMap<Class, String>();
    private final Collection<? extends WebPartFactory> _webPartFactories;

    private boolean _loadFromSource;
    private String _name = null;
    private double _version = 0.0;
    private double _requiredServerVersion = 0.0;
    private Set<String> _moduleDependencies = new CaseInsensitiveHashSet();
    private String _moduleDependenciesString = null;
    private String _svnRevision = null;
    private String _svnUrl = null;
    private String _buildUser = null;
    private String _buildTime = null;
    private String _buildOS = null;
    private String _buildPath = null;
    private String _sourcePath = null;
    private File _explodedPath = null;


    private enum SchemaUpdateType
    {
        Before
        {
            List<SqlScript> getScripts(SqlScriptProvider provider) throws SqlScriptRunner.SqlScriptException
            {
                return provider.getDropScripts();
            }
        },

        After
        {
            List<SqlScript> getScripts(SqlScriptProvider provider) throws SqlScriptRunner.SqlScriptException
            {
                return provider.getCreateScripts();
            }
        };

        abstract List<SqlScript> getScripts(SqlScriptProvider provider) throws SqlScriptRunner.SqlScriptException;
    }

    protected DefaultModule()
    {
        _webPartFactories = createWebPartFactories();
    }

    final public void initialize()
    {
        synchronized (INSTANTIATED_MODULES)
        {
            if (INSTANTIATED_MODULES.contains(getClass()))
                throw new IllegalStateException("An instance of " + getClass() + " has already been created. Modules should be singletons");
            else
                INSTANTIATED_MODULES.add(getClass());
        }

        ModuleLoader.getInstance().registerResourcePrefix(getResourcePath(), this);
        if(null != getSourcePath() && null != getBuildPath())
            ModuleLoader.getInstance().registerResourcePrefix(getResourcePath(), new ResourceFinder(this));

        Collection<? extends WebPartFactory> wpFactories = getWebPartFactories();
        if(null != wpFactories)
        {
            for (WebPartFactory part : wpFactories)
                part.setModule(this);
        }

        if (AppProps.getInstance().isDevMode() && _sourcePath != null)
        {
            File f = new File(_sourcePath);
            if (f.exists())
                _loadFromSource = true;
        }

        init();
    }

    protected abstract void init();
    protected abstract Collection<? extends WebPartFactory> createWebPartFactories();
    public abstract boolean hasScripts();

    protected String getResourcePath()
    {
        return "/" + getClass().getPackage().getName().replaceAll("\\.", "/");
    }

    protected void addController(String primaryName, Class cl, String... aliases)
    {
        if (!ViewController.class.isAssignableFrom(cl) && !Controller.class.isAssignableFrom(cl))
            throw new IllegalArgumentException(cl.toString());

        // Map controller class to canonical name
        addPageFlowClass(cl, primaryName);

        // Map all names to controller class
        addPageFlowName(primaryName, cl);
        for (String alias : aliases)
            addPageFlowName(alias, cl);
    }


    // Map controller class to canonical name
    private void addPageFlowClass(Class controllerClass, String primaryName)
    {
        assert !_pageFlowNameToClass.values().contains(controllerClass) : "Controller class '" + controllerClass + "' is already registered";

        _pageFlowClassToName.put(controllerClass, primaryName);
    }


    // Map all names to controller class
    private void addPageFlowName(String pageFlowName, Class controllerClass)
    {
        assert null == _pageFlowNameToClass.get(pageFlowName) : "Page flow name '" + pageFlowName + "' is already registered";

        _pageFlowNameToClass.put(pageFlowName, controllerClass);
    }


    public String getTabName(ViewContext context)
    {
        return getName();
    }


    public String getFormattedVersion()
    {
        return ModuleContext.formatVersion(getVersion());
    }


    public void beforeUpdate(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
            runScripts(SchemaUpdateType.Before);
    }

    /**
     * Upgrade this module to the latest version.
     */
    public void versionUpdate(ModuleContext moduleContext) throws Exception
    {
        if (hasScripts())
        {
            SqlScriptProvider provider = new FileSqlScriptProvider(this);
            List<SqlScript> scripts = SqlScriptRunner.getRecommendedScripts(provider, null, moduleContext.getInstalledVersion(), getVersion());

            if (!scripts.isEmpty())
                SqlScriptRunner.runScripts(this, moduleContext.getUpgradeUser(), scripts);
        }
    }

    public void afterUpdate(ModuleContext moduleContext)
    {
        runScripts(SchemaUpdateType.After);
    }

    private void runScripts(SchemaUpdateType type)
    {
        try
        {
            if (hasScripts())
            {
                SqlScriptProvider provider = new FileSqlScriptProvider(this);
                List<SqlScript> scripts = type.getScripts(provider);

                if (!scripts.isEmpty())
                    SqlScriptRunner.runScripts(this, null, scripts);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error running scripts in module " + getName(), e);
        }
    }


    public final Collection<? extends WebPartFactory> getWebPartFactories()
    {
        return _webPartFactories;
    }

    public void destroy()
    {
    }


    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }


    public final Map<String, Class> getPageFlowNameToClass()
    {
        return _pageFlowNameToClass;
    }


    public final Map<Class, String> getPageFlowClassToName()
    {
        return _pageFlowClassToName;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        return new ActionURL(getPageFlowNameToClass().keySet().iterator().next(), "begin", c == null ? null : c.getPath());
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE;
    }

    protected void addWebPart(String name, Container c, String location) throws SQLException
    {
        addWebPart(name, c, location, null);
    }

    protected void addWebPart(String name, Container c, String location, Map<String, String> properties)
            throws SQLException
    {
        boolean foundPart = false;
        for (Portal.WebPart part : Portal.getParts(c.getId()))
        {
            if (name.equals(part.getName()))
            {
                foundPart = true;
                break;
            }
        }
        if (!foundPart)
        {
            WebPartFactory desc = Portal.getPortalPart(name);
            if (desc != null)
            {
                Portal.addPart(c, desc, location, properties);
            }
        }
    }

    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return Collections.emptySet();
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return Collections.emptySet();
    }

    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public double getVersion()
    {
        return _version;
    }

    public void setVersion(double version)
    {
        _version = version;
    }

    public double getRequiredServerVersion()
    {
        return _requiredServerVersion;
    }

    public void setRequiredServerVersion(double requiredServerVersion)
    {
        _requiredServerVersion = requiredServerVersion;
    }

    public Set<String> getModuleDependenciesAsSet()
    {
        return _moduleDependencies;
    }

    public void setModuleDependencies(String dependencies)
    {
        _moduleDependenciesString = dependencies;

        if(null == dependencies || dependencies.length() == 0)
            return;

        String[] depArray = dependencies.split(",");
        for (String dependency : depArray)
        {
            dependency = dependency.trim();
            if (dependency.length() > 0)
                _moduleDependencies.add(dependency.toLowerCase());
        }
    }

    public String getModuleDependencies()
    {
        return _moduleDependenciesString;
    }

    public String getSvnRevision()
    {
        return _svnRevision;
    }

    public void setSvnRevision(String svnRevision)
    {
        _svnRevision = svnRevision;
    }

    public String getSvnUrl()
    {
        return _svnUrl;
    }

    public void setSvnUrl(String svnUrl)
    {
        _svnUrl = svnUrl;
    }

    public String getBuildUser()
    {
        return _buildUser;
    }

    public void setBuildUser(String buildUser)
    {
        _buildUser = buildUser;
    }

    public String getBuildTime()
    {
        return _buildTime;
    }

    public void setBuildTime(String buildTime)
    {
        _buildTime = buildTime;
    }

    public String getBuildOS()
    {
        return _buildOS;
    }

    public void setBuildOS(String buildOS)
    {
        _buildOS = buildOS;
    }

    public String getSourcePath()
    {
        return _sourcePath;
    }

    public void setSourcePath(String sourcePath)
    {
        _sourcePath = sourcePath;
    }

    public String getBuildPath()
    {
        return _buildPath;
    }

    public void setBuildPath(String buildPath)
    {
        _buildPath = buildPath;
    }

    public Map<String, String> getProperties()
    {
        Map<String,String> props = new HashMap<String,String>();
        
        props.put("Module Class", getClass().toString());
        props.put("Build Path", getBuildPath());
        props.put("SVN URL", getSvnUrl());
        props.put("SVN Revision", getSvnRevision());
        props.put("Build OS", getBuildOS());
        props.put("Build Time", getBuildTime());
        props.put("Build User", getBuildUser());
        props.put("Build Path", getBuildPath());
        props.put("Module Dependencies", getModuleDependencies());

        return props;
    }

    public List<String> getAttributions()
    {
        return Collections.emptyList();
    }

    public File getExplodedPath()
    {
        return _explodedPath;
    }

    public void setExplodedPath(File path)
    {
        _explodedPath = path;
    }

    public Set<String> getSqlScripts(@Nullable String schemaName, @NotNull SqlDialect dialect)
    {
        Set<String> fileNames = new HashSet<String>();
        File dir;
        if(_loadFromSource)
            dir = new File(_sourcePath, getSqlScriptsPath(dialect));
        else
            dir = new File(_explodedPath, getSqlScriptsPath(dialect));
            
        if(dir.exists() && dir.isDirectory())
        {
            for(File script : dir.listFiles())
            {
                String name = script.getName().toLowerCase();
                if(name.endsWith(".sql") && (null == schemaName || name.startsWith(schemaName + "-")))
                    fileNames.add(script.getName());
            }
        }

        return fileNames;
    }

    public String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        if(_loadFromSource)
            return "/src/META-INF/" + getName().toLowerCase() + "/scripts/" + dialect.getSQLScriptPath(true) + "/";
        else
            return "schemas/dbscripts/" + dialect.getSQLScriptPath(false) + "/";
    }

    public InputStream getResourceStreamFromWebapp(ServletContext ctx, String path) throws FileNotFoundException
    {
        if (_loadFromSource)
        {
            File f = new File(_sourcePath, "/webapp" + path);
            return new FileInputStream(f);
        }
        else
            return ctx.getResourceAsStream(path);
    }


    // In dev mode, check the file timestamp against the previous timestamp.  If they match, return null, otherwise return the stream & new timestamp
    // In production mode, always load and return the stream
    public Pair<InputStream, Long> getResourceStreamIfChanged(String path, long tsPrevious) throws FileNotFoundException
    {
        if (_loadFromSource)
        {
            File f = new File(_sourcePath, "/src" + path);
            long ts = f.lastModified();
            if (tsPrevious == ts)
                return null;
            return new Pair<InputStream, Long>(new FileInputStream(f), ts);
        }
        else
            return new Pair<InputStream, Long>(this.getClass().getResourceAsStream(path), -1L);
    }

    public InputStream getResourceStream(String path) throws FileNotFoundException
    {
        //first try to get this from the exploded/source directory,
        //and if not found, try the class loader
        File file = null;
        if(_loadFromSource)
            file = new File(_sourcePath, path);
        else
            file = new File(_explodedPath, path);

        if(file.exists())
            return new FileInputStream(file);
        else
            return this.getClass().getResourceAsStream(path);
    }

    @Override
    public String toString()
    {
        return getName() + " " + getVersion() + " " + super.toString();
    }


    public UpgradeCode getUpgradeCode()
    {
        return null;
    }
}
