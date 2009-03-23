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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.BreakpointThread;
import org.labkey.api.util.ContextListener;
import org.labkey.api.view.HttpView;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.servlet.mvc.Controller;

import javax.naming.*;
import javax.servlet.Filter;
import javax.servlet.*;
import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: migra
 * Date: Jul 13, 2005
 * Time: 10:27:45 AM
 */
public class ModuleLoader implements Filter
{
    private static ModuleLoader _instance = null;
    private static Logger _log = Logger.getLogger(ModuleLoader.class);
    private boolean _deferUsageReport = false;
    private static Throwable _startupFailure = null;
    private static Map<String, Throwable> _moduleFailures = new HashMap<String, Throwable>();
    private static boolean _newInstall = false;
    private static final Map<String, Module> _pageFlowToModule = new HashMap<String, Module>();
    private static final Map<Package, String> _packageToPageFlowURL = new HashMap<Package, String>();
    private static final Map<String, Module> _schemaNameToModule = new HashMap<String, Module>();
    private static final Map<String, Module> _resourcePrefixToModule = new ConcurrentHashMap<String, Module>();
    private static final Map<String, Collection<ResourceFinder>> _resourceFinders = new ConcurrentHashMap<String, Collection<ResourceFinder>>();
    private static final Map<Class, Class<? extends UrlProvider>> _urlProviderToImpl = new HashMap<Class, Class<? extends UrlProvider>>();
    private static CoreSchema _core = CoreSchema.getInstance();

    private File _webappDir;

    private enum UpgradeState {UpgradeRequired, UpgradeInProgress, UpgradeComplete}
    private static final Object UPGRADE_LOCK = new Object();
    private UpgradeState _upgradeState;
    private User upgradeUser = null;

    private static final Object STARTUP_LOCK = new Object();
    private boolean _startupComplete = false;

    private final List<ModuleResourceLoader> _resourceLoaders = new ArrayList<ModuleResourceLoader>();

    public enum ModuleState
    {
        Disabled,
        Loading,
        InstallRequired
        {
            public String describeModuleState(double installedVersion, double targetVersion)
            {
                if (installedVersion > 0.0)
                    return "Upgrade Required: " + ModuleContext.formatVersion(installedVersion) + " -> " + ModuleContext.formatVersion(targetVersion);
                else
                    return "Not Installed.";
            }
        },
        Installing,
        InstallComplete,
        ReadyToRun
        {
            public String describeModuleState(double installedVersion, double targetVersion)
            {
                return "Version " + ModuleContext.formatVersion(installedVersion) + " ready to run.";
            }
        },
        Running
        {
            public String describeModuleState(double installedVersion, double targetVersion)
            {
                return "Version " + ModuleContext.formatVersion(installedVersion) + " running.";
            }
        };

        public String describeModuleState(double installedVersion, double targetVersion)
        {
            return toString();
        }
    }


    private Map<String, ModuleContext> contextMap = new HashMap<String, ModuleContext>();
    private Map<String, Module> moduleMap = new HashMap<String, Module>();

    private List<Module> _modules;
    private final SortedMap<String, FolderType> _folderTypes = new TreeMap<String, FolderType>(new FolderTypeComparator());
    private static class FolderTypeComparator implements Comparator<String>
    {
        //Sort NONE to the bottom and Collaboration to the top
        static final String noneStr = FolderType.NONE.getName();
        static final String collabStr = "Collaboration"; //Cheating

        public int compare(String s, String s1)
        {
            if (s.equals(s1))
                return 0;

            if (noneStr.equals(s))
                return 1;
            if (noneStr.equals(s1))
                return -1;
            if (collabStr.equals(s))
                return -1;
            if (collabStr.equals(s1))
                return 1;

            return s.compareTo(s1);
        }
    }

    public ModuleLoader()
    {
        assert null == _instance : "Should be only one instance of module loader";
        if (null != _instance)
            _log.error("More than one instance of module loader...");

        _instance = this;
    }

    public static ModuleLoader getInstance()
    {
        //Will be initialized in first line of init
        return _instance;
    }

    public void init(FilterConfig filterConfig) throws ServletException
    {
        try
        {
            doInit(filterConfig.getServletContext());
        }
        catch (Throwable t)
        {
            setStartupFailure(t);
            _log.error("Failure occurred during ModuleLoader init.", t);
        }
    }

    ServletContext _servletContext = null;

    public static ServletContext getServletContext()
    {
        return getInstance()._servletContext;
    }

    private void doInit(ServletContext servletCtx) throws Exception
    {
        _servletContext = servletCtx;

        _log.debug("ModuleLoader init");

        verifyJavaVersion();

        // Register BeanUtils converters
        ConvertHelper.registerHelpers();

        _webappDir = new File(servletCtx.getRealPath(".")).getCanonicalFile();

        List<File> explodedModuleDirs;
        try
        {
            ClassLoader webappClassLoader = getClass().getClassLoader();
            Method m = webappClassLoader.getClass().getMethod("getExplodedModuleDirectories");
            explodedModuleDirs = (List<File>)m.invoke(webappClassLoader);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException("Could not find getModuleFiles() method - you probably need to copy labkeyBootstrap.jar into $CATALINA_HOME/server/lib and/or edit your labkey.xml to include <Loader loaderClass=\"org.labkey.bootstrap.LabkeyServerBootstrapClassLoader\" />", e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }

        //load module instances using Spring
        _modules = loadModules(explodedModuleDirs);
        for (Module module : _modules)
        {
            registerResourceLoaders(module.getResourceLoaders());
        }

        //sort the modules by dependencies
        ModuleDependencySorter sorter = new ModuleDependencySorter();
        _modules = sorter.sortModulesByDependencies(_modules, _resourceLoaders);

        //initialize each module in turn
        for(Module module : _modules)
        {
            try
            {
                module.initialize();
                moduleMap.put(module.getName(), module);
            }
            catch(Throwable t)
            {
                _log.error("Unable to initialize module " + module.getName(), t);
                _moduleFailures.put(module.getName(), t);
            }
        }

        // Start up a thread that lets us hit a breakpoint in the debugger, even if
        // all the real working threads are hung. This lets us invoke methods in the debugger,
        // gain easier access to statics, etc.
        new BreakpointThread().start();

        ensureDataBases();

        if (getTableInfoModules().getTableType() == TableInfo.TABLE_TYPE_NOT_IN_DB)
            _newInstall = true;

        upgradeCoreModule();

        ModuleContext[] contexts = getAllModuleContexts();

        for (ModuleContext context : contexts)
            contextMap.put(context.getName(), context);

        //Make sure we have a context for all modules, even ones we haven't seen before
        for (Module module : _modules)
        {
            ModuleContext context = contextMap.get(module.getName());
            if (null == context)
            {
                context = new ModuleContext(module);
                contextMap.put(context.getName(), context);
            }
            if (context.getInstalledVersion() != module.getVersion())
                context.setModuleState(ModuleState.InstallRequired);
            else
                context.setModuleState(ModuleState.ReadyToRun);
            /*
        else if (!context.isEnabled())
            context.setModuleState(ModuleState.Disabled);
            */
        }

        // Core module should be upgraded and ready-to-run
        ModuleContext coreCtx = contextMap.get(DefaultModule.CORE_MODULE_NAME);
        assert (ModuleState.ReadyToRun == coreCtx.getModuleState());

        boolean upgradeRequired = false;

        for (Module m : _modules)
        {
            ModuleContext ctx = getModuleContext(m);
            if (ctx.getInstalledVersion() != m.getVersion())
            {
                upgradeRequired = true;
                break;
            }
        }

        if (upgradeRequired)
        {
            setUpgradeState(UpgradeState.UpgradeRequired);
            _log.info("Module upgrade is required");
        }
        else
        {
            setUpgradeState(UpgradeState.UpgradeComplete);
        }

        _log.info("Core LabKey Server startup is complete, modules will be initialized after the first HTTP/HTTPS request");
    }

    public static List<Module> loadModules(List<File> explodedModuleDirs)
    {
        List<Module> modules = new ArrayList<Module>();
        for(File moduleDir : explodedModuleDirs)
        {
            Module module = null;
            File moduleXml = new File(moduleDir, "config/module.xml");
            try
            {
                if (moduleXml.exists())
                {
                    BeanFactory beanFactory = new XmlBeanFactory(new FileSystemResource(moduleXml));
                    module = (Module)beanFactory.getBean("moduleBean", Module.class);
                }
                else
                {
                    //assume that module name is directory name
                    SimpleModule simpleModule = new SimpleModule(moduleDir.getName());
                    simpleModule.setSourcePath(moduleDir.getAbsolutePath());

                    module = simpleModule;
                }

                if (null != module)
                {
                    module.setExplodedPath(moduleDir);
                    modules.add(module);
                }
                else
                    _log.error("No module class was found for the module '" + moduleDir.getName() + "'");
            }
            catch (Throwable t)
            {
                _log.error("Unable to instantiate module " + moduleDir, t);
                _moduleFailures.put(moduleDir.getName(), t);
            }
        }
        return modules;
    }

    public File getWebappDir()
    {
        return _webappDir;
    }

    private void verifyJavaVersion() throws ServletException
    {
        String javaVersion = AppProps.getInstance().getJavaVersion();

        if (null != javaVersion && (javaVersion.startsWith("1.5") || javaVersion.startsWith("1.6")))
            return;

        throw new ServletException("Unsupported Java runtime version: " + javaVersion + ".  LabKey requires Java 1.5 or Java 1.6.");
    }

    private void removeAPIFiles(Set<File> unclaimedFiles, File webappRoot) throws IOException
    {
        File apiContentFile = new File(webappRoot, "WEB-INF/apiFiles.list");

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(apiContentFile));
            String line;
            while ((line = reader.readLine()) != null)
            {
                unclaimedFiles.remove(new File(webappRoot, line));
            }
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException e) {} }
        }
    }

    private Set<File> listCurrentFiles(File file) throws IOException
    {
        Set<File> result = new HashSet<File>();
        result.add(file);
        if (file.isDirectory())
        {
            for (File content : file.listFiles())
            {
                result.addAll(listCurrentFiles(content));
            }
        }
        return result;
    }



    // Enumerate each jdbc DataSource in labkey.xml and attempt a (non-pooled) connection to each.  If connection fails, attempt to create
    // the database.
    //
    // We don't use DbSchema or normal pooled connections here because failed connections seem to get added into the pool.
    private void ensureDataBases() throws ServletException
    {
        _log.debug("Ensuring that all databases specified by datasources in webapp configuration xml are present");

        try
        {
            InitialContext ctx = new InitialContext();
            Context envCtx = (Context) ctx.lookup("java:comp/env");
            NamingEnumeration<Binding> iter = envCtx.listBindings("jdbc");

            while (iter.hasMore())
            {
                Binding o = iter.next();
                String dsName = o.getName();
                DataSource ds = (DataSource) o.getObject();
                ensureDataBase(ds, dsName);
            }
        }
        catch (NamingException e)
        {
            _log.error("ensureDatabases", e);
        }
    }


    // Ensure we can connect to the specified datasource.  If the connection fails with a "database doesn't exist" exception
    // then attempt to create the database.  Return true if the database existed, false if it was just created.  Throw if some
    // other exception occurs (connection fails repeatedly with something other than "database doesn't exist" or database can't
    // be created.
    private boolean ensureDataBase(DataSource ds, String dsName) throws ServletException
    {
        Connection conn = null;
        SqlDialect.DataSourceProperties props = new SqlDialect.DataSourceProperties(ds);

        // Need the dialect to:
        // 1) determine whether an exception is "no database" or something else and
        // 2) get the name of the "master" database
        //
        // Only way to get the right dialect is to look up based on the driver class name.
        SqlDialect dialect = SqlDialect.getFromDriverClassName(props.getDriverClassName());

        SQLException lastException = null;

        // Attempt a connection three times before giving up
        for (int i = 0; i < 3; i++)
        {
            if (i > 0)
            {
                _log.error("Retrying connection to \"" + dsName + "\" at " + props.getUrl() + " in 10 seconds");

                try
                {
                    Thread.sleep(10000);  // Wait 10 seconds before trying again
                }
                catch (InterruptedException e)
                {
                    _log.error("ensureDataBase", e);
                }
            }

            try
            {
                // Load the JDBC driver
                Class.forName(props.getDriverClassName());
                // Create non-pooled connection... don't want to pool a failed connection
                conn = DriverManager.getConnection(props.getUrl(), props.getUsername(), props.getPassword());
                _log.debug("Successful connection to \"" + dsName + "\" at " + props.getUrl());
                return true;        // Database already exists
            }
            catch (SQLException e)
            {
                if (dialect.isNoDatabaseException(e))
                {
                    createDataBase(props, dialect);
                    return false;   // Successfully created database
                }
                else
                {
                    _log.error("Connection to \"" + dsName + "\" at " + props.getUrl() + " failed with the following error:");
                    _log.error("Message: " + e.getMessage() + " SQLState: " + e.getSQLState() + " ErrorCode: " + e.getErrorCode(), e);
                    lastException = e;
                }
            }
            catch (Exception e)
            {
                _log.error("ensureDataBase", e);
                throw new ServletException("Internal error", e);
            }
            finally
            {
                try
                {
                    if (null != conn) conn.close();
                }
                catch (Exception x)
                {
                    _log.error("Error closing connection", x);
                }
            }
        }

        _log.error("Attempted to connect three times... giving up.", lastException);
        throw new ServletException("Can't connect to datasource \"" + dsName + "\".  Make sure that your LabKey Server configuration file includes the correct user name, password, url, port, etc. for your database and that the database server is running.", lastException);
    }


    private void createDataBase(SqlDialect.DataSourceProperties props, SqlDialect dialect) throws ServletException
    {
        Connection conn = null;
        PreparedStatement stmt = null;

        String dbName = dialect.getDatabaseName(props.getUrl());

        _log.info("Attempting to create database \"" + dbName + "\"");

        String masterUrl = StringUtils.replace(props.getUrl(), dbName, dialect.getMasterDataBaseName());

        try
        {
            conn = DriverManager.getConnection(masterUrl, props.getUsername(), props.getPassword());
            // get version specific dialect
            dialect = SqlDialect.getFromMetaData(conn.getMetaData());
            stmt = conn.prepareStatement(dialect.getCreateDatabaseSql(dbName));
            stmt.execute();
        }
        catch (SQLException e)
        {
            _log.error("createDataBase() failed", e);
            dialect.handleCreateDatabaseException(e);
        }
        finally
        {
            try
            {
                if (null != conn) conn.close();
            }
            catch (Exception x)
            {
                _log.error("", x);
            }
            try
            {
                if (null != stmt) stmt.close();
            }
            catch (Exception x)
            {
                _log.error("", x);
            }
        }

        _log.info("Database \"" + dbName + "\" created");
    }


    // Update the CoreModule "manually", outside the normal page flow-based process.  We want to be able to change the core tables
    // before we display pages, require login, check permissions, etc.
    private void upgradeCoreModule() throws ServletException
    {
        Module coreModule = ModuleLoader.getInstance().getCoreModule();
        if (coreModule == null)
        {
            throw new IllegalStateException("CoreModule does not exist");
        }
        ModuleContext coreContext;

        // If modules table doesn't exist (bootstrap case), then new up a core context
        if (getTableInfoModules().getTableType() == TableInfo.TABLE_TYPE_NOT_IN_DB)
            coreContext = new ModuleContext(coreModule);
        else
            coreContext = getModuleContext("Core");

        // Does the core module need to be upgraded?
        if (coreContext.getInstalledVersion() == coreModule.getVersion())
            return;

        if (coreContext.isNewInstall())
        {
            _log.debug("Initializing core module to " + coreModule.getFormattedVersion());
        }
        else
        {
            if (coreContext.getInstalledVersion() < 2.0)
                throw new ServletException("Can't upgrade from LabKey Server version " + coreContext.getInstalledVersion() + "; installed version must be 2.0 or later. Contact info@labkey.com for assistance.");

            _log.debug("Upgrading core module from " + ModuleContext.formatVersion(coreContext.getInstalledVersion()) + " to " + coreModule.getFormattedVersion());
        }

        contextMap.put(coreModule.getName(), coreContext);

        try
        {
            ModuleUpgrader coreUpgrader = new ModuleUpgrader(Collections.singletonList(coreModule));
            coreUpgrader.upgrade();
        }
        catch (Exception e)
        {
            throw new ServletException(e);
        }
    }


    public Throwable getStartupFailure()
    {
        return _startupFailure;
    }

    public void setStartupFailure(Throwable t)
    {
        if (null == _startupFailure)
            _startupFailure = t;
    }

    public void setModuleFailure(String moduleName, Throwable t)
    {
        _moduleFailures.put(moduleName, t);
    }

    public Map<String, Throwable> getModuleFailures()
    {
        if (_moduleFailures.size() == 0)
        {
            return Collections.emptyMap();
        }
        else
        {
            return new HashMap<String, Throwable>(_moduleFailures);
        }
    }

    private TableInfo getTableInfoModules()
    {
        return _core.getTableInfoModules();
    }

    public ModuleContext getModuleContext(Module module)
    {
        return contextMap.get(module.getName());
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        if (isUpgradeRequired())
        {
            setDeferUsageReport(true);
        }
        else
        {
            ensureStartupComplete();
        }

        filterChain.doFilter(servletRequest, servletResponse);

        ConnectionWrapper.dumpLeaksForThread(Thread.currentThread());
    }

    public boolean isDeferUsageReport()
    {
        return _deferUsageReport;
    }

    public void setDeferUsageReport(boolean defer)
    {
        _deferUsageReport = defer;
    }

    public void runBeforeUpdates()
    {
        synchronized (UPGRADE_LOCK)
        {
            List<Module> modules = getModules();
            ListIterator<Module> iter = modules.listIterator(modules.size());

            while (iter.hasPrevious())
            {
                Module module = iter.previous();
                module.beforeUpdate(getModuleContext(module));
            }
        }
    }

    public void runAfterUpdates()
    {
        synchronized (UPGRADE_LOCK)
        {
            for (Module module : getModules())
                module.afterUpdate(getModuleContext(module));
        }
    }

    // Runs the drop view and create view scripts in every module
    public void recreateViews()
    {
        synchronized (UPGRADE_LOCK)
        {
            runBeforeUpdates();
            runAfterUpdates();
        }
    }

    public boolean isStartupComplete()
    {
        synchronized (STARTUP_LOCK)
        {
            return _startupComplete;
        }
    }

    private void ensureStartupComplete()
    {
        synchronized (STARTUP_LOCK)
        {
            if (_startupComplete)
                return;

            if (isUpgradeRequired())
                throw new IllegalStateException("Can't start modules before upgrade is complete");

            for (Module m : _modules)
            {
                try
                {
                    ModuleContext ctx = getModuleContext(m);
                    m.startup(ctx);
                    ctx.setModuleState(ModuleLoader.ModuleState.Running);
                }
                catch (Throwable x)
                {
                    setStartupFailure(x);
                    _log.error("Failure starting module: " + m.getName(), x);
                }

                //call the module resource loaders
                for(ModuleResourceLoader resLoader : _resourceLoaders)
                {
                    try
                    {
                        resLoader.loadResources(m, m.getExplodedPath());
                    }
                    catch(Throwable t)
                    {
                        _log.error("Unable to load resources from module " + m.getName() + " using the resource loader " + resLoader.getClass().getName(), t);
                    }
                }
            }

            ContextListener.moduleStartupComplete(_servletContext);

            _startupComplete = true;
        }
    }


    void saveModuleContext(ModuleContext context)
    {
        try
        {
            ModuleContext stored = getModuleContext(context.getName());
            if (null == stored)
                Table.insert(null, getTableInfoModules(), context);
            else
                Table.update(null, getTableInfoModules(), context, context.getName(), null);
        }
        catch (SQLException x)
        {
            _log.error("Couldn't save module context.", x);
        }
    }


    public void startNonCoreUpgrade(User user) throws Exception
    {
        synchronized(UPGRADE_LOCK)
        {
            if (_upgradeState == UpgradeState.UpgradeRequired)
            {
                List<Module> modules = new ArrayList<Module>(getModules());
                modules.remove(ModuleLoader.getInstance().getCoreModule());
                setUpgradeState(UpgradeState.UpgradeInProgress);
                setUpgradeUser(user);

                ModuleUpgrader upgrader = new ModuleUpgrader(modules);
                upgrader.upgradeInBackground(new Runnable(){
                    public void run()
                    {
                        ModuleLoader.getInstance().setUpgradeState(UpgradeState.UpgradeComplete);
                    }
                });
            }
        }
    }


    public void setUpgradeUser(User user)
    {
        synchronized(UPGRADE_LOCK)
        {
            assert null == upgradeUser;
            upgradeUser = user;
        }
    }

    public User getUpgradeUser()
    {
        synchronized(UPGRADE_LOCK)
        {
            return upgradeUser;
        }
    }

    public void setUpgradeState(UpgradeState state)
    {
        synchronized(UPGRADE_LOCK)
        {
            _upgradeState = state;
        }
    }

    public boolean isUpgradeRequired()
    {
        synchronized(UPGRADE_LOCK)
        {
            return UpgradeState.UpgradeComplete != _upgradeState;
        }
    }

    public boolean isUpgradeInProgress()
    {
        synchronized(UPGRADE_LOCK)
        {
            return UpgradeState.UpgradeInProgress == _upgradeState;
        }
    }

    // Did this server start up with no modules installed?  If so, it's a new install.  This lets us tailor the
    // module upgrade UI to "install" or "upgrade," as appropriate.
    public boolean isNewInstall()
    {
        return _newInstall;
    }

    public void destroy()
    {
        // in the case of a startup failure, _modules may be null.
        // we want to allow a context reload to succeed in this case,
        // since the reload may contain the code change to fix the problem
        if (_modules != null)
        {
            for (Module module : _modules)
            {
                module.destroy();
            }
        }
    }

    public Module getModule(String name)
    {
        return moduleMap.get(name);
    }

    public Module getCoreModule()
    {
        return getModule(DefaultModule.CORE_MODULE_NAME);
    }

    public List<Module> getModules()
    {
        return _modules;
    }

    // TODO: Move to LoginController, only place that uses this now
    public boolean isAdminOnlyMode()
    {
        return AppProps.getInstance().isUserRequestedAdminOnlyMode() || (isUpgradeRequired() && !UserManager.hasNoUsers());
    }

    public String getAdminOnlyMessage()
    {
        if (isUpgradeRequired() && !UserManager.hasNoUsers())
        {
            return "This site is currently being upgraded to a new version of LabKey Server.";
        }
        return AppProps.getInstance().getAdminOnlyMessage();
    }

    // CONSIDER: ModuleUtil.java
    public Collection<String> getModuleSummaries(Container c)
    {
        LinkedList<String> list = new LinkedList<String>();
        for (Module m : _modules)
        {
            Collection<String> messages = m.getSummary(c);
            if (null != messages)
                list.addAll(messages);
        }
        return list;
    }

    public void initPageFlowToModule()
    {
        synchronized(_pageFlowToModule)
        {
            if (!_pageFlowToModule.isEmpty())
                return;
            List<Module> allModules = ModuleLoader.getInstance().getModules();
            for (Module module : allModules)
            {
                TreeSet<String> set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                for (Map.Entry<String, Class<? extends Controller>> entry : module.getPageFlowNameToClass().entrySet())
                {
                    String key = entry.getKey();
                    if (!set.add(key))
                        continue;   // Avoid duplicate work

                    _pageFlowToModule.put(key, module);
                    _pageFlowToModule.put(key.toLowerCase(), module);

                    Class clazz = entry.getValue();
                    _packageToPageFlowURL.put(clazz.getPackage(), key);
                    for (Class innerClass : clazz.getClasses())
                    {
                        for (Class inter : innerClass.getInterfaces())
                        {
                            Class[] supr = inter.getInterfaces();
                            if (supr != null && supr.length == 1 && UrlProvider.class.equals(supr[0]))
                                _urlProviderToImpl.put(inter, innerClass);
                        }
                    }
                }
            }
        }
    }

    public String getPageFlowForPackage(Package pkg)
    {
        String ret = _packageToPageFlowURL.get(pkg);
        if (ret != null)
            return ret;
        return StringUtils.replace(pkg.getName(), ".", "-");
    }

    public Module getModuleForPageFlow(String pageflow)
    {
        synchronized(_pageFlowToModule)
        {
            Module module = _pageFlowToModule.get(pageflow);
            if (null != module)
                return module;

            int i = pageflow.indexOf('-');
            if (-1 == i)
                return null;

            String prefix = pageflow.substring(0,i);
            module = _pageFlowToModule.get(prefix);
            if (null != module)
                _pageFlowToModule.put(pageflow, module);
            return module;
        }
    }


    public Module getModuleForSchemaName(String schemaName)
    {
        synchronized(_schemaNameToModule)
        {
            if (_schemaNameToModule.isEmpty())
            {
                for (Module module : getModules())
                {
                    for (String name : module.getSchemaNames())
                        _schemaNameToModule.put(name, module);
                }
            }
        }

        return _schemaNameToModule.get(schemaName);
    }

    public <P extends UrlProvider> P getUrlProvider(Class<P> inter)
    {
        Class<? extends UrlProvider> clazz = _urlProviderToImpl.get(inter);

        if (clazz == null)
        {
            throw new IllegalArgumentException("Interface " + inter.getName() + " not registered.");
        }

        try
        {
            P impl = (P) clazz.newInstance();
            return impl;
        }
        catch (InstantiationException e)
        {
            throw new RuntimeException("Failed to instantiate provider class " + clazz.getName() + " for " + inter.getName(), e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Illegal access of provider class " + clazz.getName() + " for " + inter.getName(), e);
        }
    }


    public void registerResourcePrefix(String prefix, Module module)
    {
        if (null == prefix)
            return;

        synchronized(_resourcePrefixToModule)
        {
            // If prefix is a substring of an existing path or vice versa, throw an exception
            for (Map.Entry<String, Module> e : _resourcePrefixToModule.entrySet())
                if (prefix.startsWith(e.getKey()) || e.getKey().startsWith(prefix))
                    throw new RuntimeException(module.getName() + " module's resource path (" + prefix + ") overlaps with " + e.getValue().getName() + " module's resource path (" + e.getKey() + ")");

            _resourcePrefixToModule.put(prefix, module);
        }
    }


    public void registerResourcePrefix(String prefix, ResourceFinder finder)
    {
        if (null == prefix)
            return;

        synchronized(_resourceFinders)
        {
            // First make sure there's no overlap with a different prefix
            for (Map.Entry<String, Collection<ResourceFinder>> e : _resourceFinders.entrySet())
            {
                // We allow more than one finder with the same key (e.g., API and Internal have the same package)
                if (!prefix.equals(e.getKey()))
                {
                    if (prefix.startsWith(e.getKey()) || e.getKey().startsWith(prefix))
                        throw new RuntimeException(finder.getName() + " prefix (" + prefix + ") overlaps with existing prefix (" + e.getKey() + ")");
                }
            }

            Collection<ResourceFinder> col = _resourceFinders.get(prefix);

            if (null == col)
            {
                col = new ArrayList<ResourceFinder>();
                _resourceFinders.put(prefix, col);
            }

            col.add(finder);
        }
    }


    public Collection<ResourceFinder> getResourceFindersForPath(String path)
    {
        for (Map.Entry<String, Collection<ResourceFinder>> e : _resourceFinders.entrySet())
            if (path.startsWith(e.getKey()))
                return e.getValue();

        return null;
    }


    public Module getModuleForResourcePath(String path)
    {
        for (Map.Entry<String, Module> e : _resourcePrefixToModule.entrySet())
            if (path.startsWith(e.getKey()))
                return e.getValue();

        return null;
    }


    public Module getCurrentModule()
    {
        return ModuleLoader.getInstance().getModuleForPageFlow(HttpView.getRootContext().getActionURL().getPageFlow());
    }

    public FolderType getFolderType(String name)
    {
        synchronized (_folderTypes)
        {
            return _folderTypes.get(name);
        }
    }

    public void registerFolderType(FolderType folderType)
    {
        synchronized (_folderTypes)
        {
            _folderTypes.put(folderType.getName(), folderType);
        }
    }

    public Collection<FolderType> getFolderTypes()
    {
        synchronized (_folderTypes)
        {
            return Collections.unmodifiableCollection(_folderTypes.values());
        }
    }

    public void registerResourceLoader(ModuleResourceLoader loader)
    {
        registerResourceLoaders(Collections.singleton(loader));
    }

    public void registerResourceLoaders(Set<ModuleResourceLoader> loaders)
    {
        synchronized (_resourceLoaders)
        {
            _resourceLoaders.addAll(loaders);
        }
    }

    public static File searchModuleSourceForFile(String rootPath, String filepath)
    {
        File rootDir = new File(rootPath + "/server/modules");
        File[] moduleDirs = rootDir.listFiles(new FileFilter() {
            public boolean accept(File f)
            {
                return f.isDirectory();
            }
        });

        for (File moduleDir : moduleDirs)
        {
            File f = new File(moduleDir.getPath() + filepath);
            if (f.exists())
                return f;
        }

        File f = new File(rootPath + "/server/internal/" + filepath);
        if (f.exists())
            return f;

        f = new File(rootPath + "/server/api/" + filepath);
        if (f.exists())
            return f;
        return null;
    }



    private ModuleContext getModuleContext(String name)
    {
        try
        {
            TableInfo modules = getTableInfoModules();
            SQLFragment sql = new SQLFragment("SELECT * FROM " + modules.getSelectName() + " WHERE Name=?", name);
            ModuleContext[] contexts = Table.executeQuery(modules.getSchema(), sql, ModuleContext.class);
            return contexts == null || contexts.length == 0 ? null : contexts[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    private ModuleContext[] getAllModuleContexts()
    {
        try
        {
            TableInfo modules = getTableInfoModules();
            SQLFragment sql = new SQLFragment("SELECT * FROM " + modules.getSelectName());
            ModuleContext[] contexts;
            contexts = Table.executeQuery(modules.getSchema(), sql, ModuleContext.class);
            return contexts;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
}
