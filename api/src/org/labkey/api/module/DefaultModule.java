/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.data.*;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.reports.report.ModuleQueryRReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.collections.Cache;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.*;
import org.labkey.api.search.SearchService;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 14, 2005
 * Time: 11:42:09 AM
 */
public abstract class DefaultModule implements Module
{
    public static final String CORE_MODULE_NAME = "Core";

    private static final Logger _log = Logger.getLogger(DefaultModule.class);
    private static final Set<Pair<Class,String>> INSTANTIATED_MODULES = new HashSet<Pair<Class, String>>();

    protected static final FilenameFilter rReportFilter = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(ModuleRReportDescriptor.FILE_EXTENSION);
        }
    };

    private final Map<String, Class<? extends Controller>> _pageFlowNameToClass = new LinkedHashMap<String, Class<? extends Controller>>();
    private final Map<Class<? extends Controller>, String> _pageFlowClassToName = new HashMap<Class<? extends Controller>, String>();
    private Collection<? extends WebPartFactory> _webPartFactories;

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
    private static final Cache _reportDescriptorCache = new Cache(1024, Cache.DAY, "Report descriptor cache");

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
    }

    public int compareTo(Module m)
    {
        //sort by name--core module will override to ensure first in sort
        return getName().compareTo(m.getName());
    }

    final public void initialize()
    {
        synchronized (INSTANTIATED_MODULES)
        {
            //simple modules all use the same Java class, so we need to also include
            //the module name in the instantiated modules set
            Pair<Class,String> reg = new Pair<Class,String>(getClass(), getName());
            if (INSTANTIATED_MODULES.contains(reg))
                throw new IllegalStateException("An instance of module " + getClass() +  " with name '" + getName() + "' has already been created. Modules should be singletons");
            else
                INSTANTIATED_MODULES.add(reg);
        }

        ModuleLoader.getInstance().registerResourcePrefix(getResourcePath(), this);
        if(null != getSourcePath() && null != getBuildPath())
            ModuleLoader.getInstance().registerResourcePrefix(getResourcePath(), new ResourceFinder(this));

        if (AppProps.getInstance().isDevMode() && _sourcePath != null)
        {
            File f = new File(_sourcePath);
            if (f.exists())
                _loadFromSource = true;
        }

        init();

        Collection<? extends WebPartFactory> wpFactories = getWebPartFactories();
        if(null != wpFactories)
        {
            for (WebPartFactory part : wpFactories)
                part.setModule(this);
        }
    }
    

    protected abstract void init();
    protected abstract Collection<? extends WebPartFactory> createWebPartFactories();
    public boolean isWebPartFactorySetStale() {return false;}
    public abstract boolean hasScripts();

    protected String getResourcePath()
    {
        return "/" + getClass().getPackage().getName().replaceAll("\\.", "/");
    }

    protected void addController(String primaryName, Class<? extends Controller> cl, String... aliases)
    {
        if (!Controller.class.isAssignableFrom(cl))
            throw new IllegalArgumentException(cl.toString());

        // Map controller class to canonical name
        addPageFlowClass(cl, primaryName);

        // Map all names to controller class
        addPageFlowName(primaryName, cl);
        for (String alias : aliases)
            addPageFlowName(alias, cl);
    }


    // Map controller class to canonical name
    private void addPageFlowClass(Class<? extends Controller> controllerClass, String primaryName)
    {
        assert !_pageFlowNameToClass.values().contains(controllerClass) : "Controller class '" + controllerClass + "' is already registered";

        _pageFlowClassToName.put(controllerClass, primaryName);
    }


    // Map all names to controller class
    private void addPageFlowName(String pageFlowName, Class<? extends Controller> controllerClass)
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
        if(null == _webPartFactories || isWebPartFactorySetStale())
            _webPartFactories = createWebPartFactories();

        return _webPartFactories;
    }

    public void destroy()
    {
    }


    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }


    public final Map<String, Class<? extends Controller>> getPageFlowNameToClass()
    {
        return _pageFlowNameToClass;
    }


    public final Map<Class<? extends Controller>, String> getPageFlowClassToName()
    {
        return _pageFlowClassToName;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        return new ActionURL(getPageFlowNameToClass().keySet().iterator().next(), "begin", c);
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
        Map<String,String> props = new LinkedHashMap<String,String>();

        props.put("Module Class", getClass().getName());
        props.put("Version", getFormattedVersion());
        props.put("Extracted Path", getExplodedPath().getAbsolutePath());
        props.put("Build Path", getBuildPath());
        props.put("SVN URL", getSvnUrl());
        props.put("SVN Revision", getSvnRevision());
        props.put("Build OS", getBuildOS());
        props.put("Build Time", getBuildTime());
        props.put("Build User", getBuildUser());
        props.put("Build Path", getBuildPath());
        props.put("Module Dependencies", StringUtils.trimToNull(getModuleDependencies()) == null ? "<none>" : getModuleDependencies());

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

        String sqlScriptsPath = getSqlScriptsPath(dialect);
        if (_loadFromSource)
        {
            dir = new File(_sourcePath, sqlScriptsPath);
            if (!dir.isDirectory())
                dir = new File(_sourcePath, "resources/" + sqlScriptsPath);
            if (!dir.isDirectory())
                dir = new File(_sourcePath, "src/" + sqlScriptsPath);
        }
        else
            dir = new File(_explodedPath, sqlScriptsPath);

        if (dir.isDirectory())
        {
            for (File script : dir.listFiles())
            {
                String name = script.getName().toLowerCase();
                if (name.endsWith(".sql") && (null == schemaName || name.startsWith(schemaName + "-")))
                    fileNames.add(script.getName());
            }
        }

        return fileNames;
    }

    public String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        return "schemas/dbscripts/" + dialect.getSQLScriptPath() + "/";
    }

    protected File reportKeyToLegalFile(File rootDir, String key)
    {
        if(null == key || null == rootDir)
            return null;

        //the report key is a relative path
        //need to split it and make each part a legal file name
        String[] keyParts = key.split("/");
        for(int idx = 0; idx < keyParts.length; ++idx)
            keyParts[idx] = FileUtil.makeLegalName(keyParts[idx]);

        //reassemble into final file path
        String sep = "";
        StringBuilder legalKey = new StringBuilder();
        for(String part : keyParts)
        {
            legalKey.append(sep);
            legalKey.append(part);
            sep = "/";
        }

        return new File(rootDir, legalKey.toString());
    }

    public List<ReportDescriptor> getReportDescriptors(String key)
    {
        if(null == key)
            return null;

        //currently we support only R reports under the queries directory
        //in the future, we can also support R reports that are not tied to a schema/query
        File keyDir = reportKeyToLegalFile(getQueryReportsDir(), key);
        if(keyDir.exists() && keyDir.isDirectory())
        {
            List<ReportDescriptor> reportDescriptors = new ArrayList<ReportDescriptor>();
            for(File file : keyDir.listFiles(rReportFilter))
            {
                ModuleRReportDescriptor descriptor = (ModuleRReportDescriptor)_reportDescriptorCache.get(file.getAbsolutePath());
                if(null == descriptor || descriptor.isStale())
                {
                    descriptor = createReportDescriptor(key, file);
                    _reportDescriptorCache.put(file.getAbsolutePath(), descriptor);
                }
                reportDescriptors.add(descriptor);
            }
            return reportDescriptors;
        }

        return null;
    }

    public ReportDescriptor getReportDescriptor(String path)
    {
        if(null == path)
            return null;

        //the report path is a relative path from the module's reports directory
        //so the report key will be the middle two sections of the path
        //e.g., for path 'schemas/ms2/peptides/myreport.r', key is 'ms2/peptides'
        String key = null;
        String[] pathParts = path.split("/");
        if(getQueryReportsDir().getName().equalsIgnoreCase(pathParts[0]) && pathParts.length >= 3)
            key = pathParts[1] + "/" + pathParts[2];
        else
            key = path.substring(0, path.lastIndexOf('/'));

        File reportFile = reportKeyToLegalFile(getReportsDir(), path);
        if(reportFile.exists() && reportFile.isFile())
        {
            ModuleRReportDescriptor descriptor = (ModuleRReportDescriptor)_reportDescriptorCache.get(reportFile.getAbsolutePath());
            if(null == descriptor || descriptor.isStale())
            {
                descriptor = createReportDescriptor(key, reportFile);
                _reportDescriptorCache.put(reportFile.getAbsolutePath(), descriptor);
            }
            return descriptor;
        }
        else
            return null;
    }

    protected ModuleRReportDescriptor createReportDescriptor(String key, File reportFile)
    {
        //for now, all we create are query r report descriptors
        return new ModuleQueryRReportDescriptor(this, key, reportFile, getQueryReportsDir().getName() + "/" + key + "/" + reportFile.getName());
    }

    protected File getReportsDir()
    {
        return new File(getExplodedPath(), "reports");
    }

    protected File getQueryReportsDir()
    {
        return new File(getReportsDir(), "schemas");
    }

    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        return Collections.emptySet();
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
            File file = new File(_sourcePath, path);
            if (!file.exists() && !path.startsWith("resources"))
                file = new File(_sourcePath, "resources/" + path);
            if (!file.exists() && !path.startsWith("src"))
                file = new File(_sourcePath, "src/" + path);
            long ts = file.lastModified();
            if (tsPrevious == ts)
                return null;
            return new Pair<InputStream, Long>(new FileInputStream(file), ts);
        }
        else
        {
            File file = new File(_explodedPath, path);
            if (file.exists())
                return new Pair<InputStream, Long>(new FileInputStream(file), -1L);
            else
                return new Pair<InputStream, Long>(this.getClass().getResourceAsStream(path), -1L);
        }
    }

    public InputStream getResourceStream(String path) throws FileNotFoundException
    {
        //first try to get this from the exploded/source directory,
        //and if not found, try the class loader
        File file;
        if (_loadFromSource)
        {
            //FIX: 8122 - if path does not start with src/, add that
            file = new File(_sourcePath, path);
            if (!file.exists() && !path.startsWith("resources"))
                file = new File(_sourcePath, "resources/" + path);
            if (!file.exists() && !path.startsWith("src"))
                file = new File(_sourcePath, "src/" + path);
        }
        else
            file = new File(_explodedPath, path);

        if (file.exists())
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


    public void dispatch(HttpServletRequest request, HttpServletResponse response, ActionURL url)
            throws ServletException, IOException
    {
        int stackSize = -1;
        Controller controller = getController(request, url.getPageFlow());

        if (controller == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try
        {
            stackSize = HttpView.getStackSize();

            ViewContext rootContext = new ViewContext(request, response, url);

            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Expires", "Sun, 01 Jan 2000 00:00:00 GMT");

            HttpView.initForRequest(rootContext, request, response);
            assert rootContext == HttpView.currentContext();

            // Store the original URL in case we need to redirect for authentication
            if (request.getAttribute(ViewServlet.ORIGINAL_URL) == null)
            {
                URLHelper helper = new URLHelper(request);
                request.setAttribute(ViewServlet.ORIGINAL_URL, helper.getURIString());
            }
            request.setAttribute(ViewServlet.REQUEST_URL, url);

            if (controller instanceof HasViewContext)
                ((HasViewContext)controller).setViewContext(rootContext);
            controller.handleRequest(request, response);
        }
        catch (ServletException x)
        {
            _log.error("error", x);
            throw x;
        }
        catch (IOException x)
        {
            _log.error("error", x);
            throw x;
        }
        catch (Throwable x)
        {
            _log.error("error", x);
            throw new ServletException(x);
        }
        finally
        {
            assert HttpView.getStackSize() == stackSize + 1;
            HttpView.resetStackSize(stackSize);
        }
    }

    public Controller getController(HttpServletRequest request, String name)
    {
        Class cls = _pageFlowNameToClass.get(name);
        if (null == cls)
            return null;

        return getController(request, cls);
    }


    public Controller getController(HttpServletRequest request, Class cls)
    {
        try
        {
            return (Controller)cls.newInstance();
        }
        catch (IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
        catch (InstantiationException x)
        {
            throw new RuntimeException(x);
        }
    }

    @NotNull
    public List<File> getStaticFileDirectories()
    {
        List<File> l = new ArrayList<File>(3);
        String build = getBuildPath();
        File exploded = getExplodedPath();
        String source = getSourcePath();

        if (AppProps.getInstance().isDevMode())
        {
            if (null != source)
            {
                File f = new File(new File(source), "webapp");
                if (f.isDirectory())
                    l.add(f);
            }
            if (null != build)
            {
                File f = new File(new File(build), "explodedModule/web");
                if (f.isDirectory())
                    l.add(f);
            }
        }
        if (exploded != null && exploded.isDirectory())
        {
            File f = new File(exploded, "web");
            if (f.isDirectory())
                l.add(f);
        }
        return l;
    }


    public @Nullable Collection<String> getJarFilenames()
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        File lib = new File(AppProps.getInstance().getProjectRoot(), "server/modules/" + getName() + "/lib");

        if (!lib.exists())
            return null;

        Set<String> filenames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        filenames.addAll(Arrays.asList(lib.list(getJarFilenameFilter())));

        return filenames;
    }

    protected FilenameFilter getJarFilenameFilter()
    {
        return new FilenameFilter() {
            public boolean accept(File dir, String name)
            {
                return name.endsWith(".jar");
            }
        };
    }
}
