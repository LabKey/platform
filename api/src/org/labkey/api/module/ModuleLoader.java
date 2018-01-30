/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.annotations.JavaRuntimeVersion;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.module.ModuleUpgrader.Execution;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.util.BreakpointThread;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.servlet.mvc.Controller;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Drives the process of initializing all of the modules at startup time and otherwise managing their life cycle.
 * User: migra
 * Date: Jul 13, 2005
 */
public class ModuleLoader implements Filter
{
    private static final double EARLIEST_UPGRADE_VERSION = 15.3;
    private static final Logger _log = Logger.getLogger(ModuleLoader.class);
    private static final Map<String, Throwable> _moduleFailures = new HashMap<>();
    private static final Map<String, Module> _controllerNameToModule = new HashMap<>();
    private static final Map<String, SchemaDetails> _schemaNameToSchemaDetails = new CaseInsensitiveHashMap<>();
    private static final Map<String, Collection<ResourceFinder>> _resourceFinders = new HashMap<>();
    private static final Map<Class, Class<? extends UrlProvider>> _urlProviderToImpl = new HashMap<>();
    private static final CoreSchema _core = CoreSchema.getInstance();
    private static final Object UPGRADE_LOCK = new Object();
    private static final Object STARTUP_LOCK = new Object();
    private static final String APACHE_TOMCAT_SERVER_NAME_PREFIX = "Apache Tomcat/";

    public static final String MODULE_NAME_REGEX = "\\w+";
    public static final String PRODUCTION_BUILD_TYPE = "Production";
    public static final String LABKEY_DATA_SOURCE = "labkeyDataSource";
    public static final String CPAS_DATA_SOURCE = "cpasDataSource";
    public static final Object SCRIPT_RUNNING_LOCK = new Object();

    private static ModuleLoader _instance = null;
    private static Throwable _startupFailure = null;
    private static boolean _newInstall = false;
    private static TomcatVersion _tomcatVersion = TomcatVersion.UNKNOWN;

    private static final String BANNER = "\n" +
            "   __                                   \n" +
            "   ||  |  _ |_ |/ _     (\u00af _  _   _  _\n" +
            "  (__) |_(_||_)|\\(/_\\/  _)(/_| \\/(/_|  \n" +
            "                    /                  ";

    private boolean _deferUsageReport = false;
    private File _webappDir;
    private UpgradeState _upgradeState;
    private User _upgradeUser = null;

    // NOTE: the following startup fields are synchronized under STARTUP_LOCK
    private StartupState _startupState = StartupState.StartupIncomplete;
    private String _startingUpMessage = null;

    private enum UpgradeState {UpgradeRequired, UpgradeInProgress, UpgradeComplete}

    private enum StartupState {StartupIncomplete, StartupInProgress, StartupComplete}

    public enum ModuleState
    {
        Disabled,
        Loading,
        InstallRequired
        {
            public String describeModuleState(ModuleContext context, double installedVersion, double targetVersion)
            {
                if (installedVersion > 0.0)
                    return "Upgrade Required: " + ModuleContext.formatVersion(installedVersion) + " -> " + ModuleContext.formatVersion(targetVersion);
                else
                    return "Not Installed.";
            }
        },
        Installing,
        InstallComplete,
        ReadyToStart
        {
            public String describeModuleState(ModuleContext context, double installedVersion, double targetVersion)
            {
                return "Version " + ModuleContext.formatVersion(installedVersion) + " ready to start.";
            }
        },
        Starting
        {
            public String describeModuleState(ModuleContext context, double installedVersion, double targetVersion)
            {
                return "Version " + ModuleContext.formatVersion(installedVersion) + " starting up.";
            }
        },
        Started
        {
            public String describeModuleState(ModuleContext context, double installedVersion, double targetVersion)
            {
                return "Version " + ModuleContext.formatVersion(installedVersion) + " started.";
            }
        };

        public String describeModuleState(ModuleContext context, double installedVersion, double targetVersion)
        {
            return toString();
        }
    }


    private Map<String, ModuleContext> _contextMap = new HashMap<>();
    private Map<String, Module> _moduleMap = new CaseInsensitiveHashMap<>();
    private Map<Class<? extends Module>, Module> _moduleClassMap = new HashMap<>();

    private List<Module> _modules;
    private MultiValuedMap<String, ConfigProperty> _configPropertyMap = new HashSetValuedHashMap<>();

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
        return getInstance() == null ? null : getInstance()._servletContext;
    }

    /** Do basic module loading, shared between the web server and remote pipeline deployments */
    public List<Module> doInit(List<File> explodedModuleDirs)
    {
        _log.debug("ModuleLoader init");

        verifyJavaVersion();
        rollErrorLogFile(_log);

        // make sure ConvertHelper is initialized
        ConvertHelper.getPropertyEditorRegistrar();

        //load module instances using Spring
        _modules = loadModules(explodedModuleDirs);

        //sort the modules by dependencies
        ModuleDependencySorter sorter = new ModuleDependencySorter();
        _modules = sorter.sortModulesByDependencies(_modules);

        for (Module module : _modules)
        {
            _moduleMap.put(module.getName(), module);
            _moduleClassMap.put(module.getClass(), module);
        }

        return _modules;
    }

    /** Full web-server initialization */
    private void doInit(ServletContext servletCtx) throws Exception
    {
        _log.info(BANNER);

        _servletContext = servletCtx;

        AppProps.getInstance().setContextPath(_servletContext.getContextPath());

        setTomcatVersion();

        _webappDir = FileUtil.getAbsoluteCaseSensitiveFile(new File(servletCtx.getRealPath("")));

        // load startup configuration information from properties, side-effect may set newinstall=true
        loadStartupProps();

        List<File> explodedModuleDirs;

        try
        {
            ClassLoader webappClassLoader = getClass().getClassLoader();
            Method m = webappClassLoader.getClass().getMethod("getExplodedModuleDirectories");
            explodedModuleDirs = (List<File>)m.invoke(webappClassLoader);
        }
        catch (NoSuchMethodException e)
        {
            throw new ConfigurationException("Could not find expected method.", "You probably need to copy labkeyBootstrap.jar into $CATALINA_HOME/lib and/or edit your " + AppProps.getInstance().getWebappConfigurationFilename() + " to include <Loader loaderClass=\"org.labkey.bootstrap.LabKeyBootstrapClassLoader\" />", e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }

        doInit(explodedModuleDirs);

        // set the project source root before calling .initialize() on modules
        Module coreModule = _modules.isEmpty() ? null : _modules.get(0);
        if (coreModule == null || !DefaultModule.CORE_MODULE_NAME.equals(coreModule.getName()))
            throw new IllegalStateException("Core module was not first or could not find the Core module. Ensure that Tomcat user can create directories under the <LABKEY_HOME>/modules directory.");
        setProjectRoot(coreModule);

        // Do this after we've checked to see if we can find the core module. See issue 22797.
        verifyProductionModeMatchesBuild();

        // Initialize data sources before initializing modules; modules will fail to initialize if the appropriate data sources aren't available
        initializeDataSources();

        // Start up a thread that lets us hit a breakpoint in the debugger, even if all the real working threads are hung.
        // This lets us invoke methods in the debugger, gain easier access to statics, etc.
        new BreakpointThread().start();

        // Start listening for requests for thread and heap dumps
        File coreModuleDir = coreModule.getExplodedPath();
        File modulesDir = coreModuleDir.getParentFile();
        new DebugInfoDumper(modulesDir);

        if (getTableInfoModules().getTableType() == DatabaseTableType.NOT_IN_DB)
            _newInstall = true;

        boolean coreRequiredUpgrade = upgradeCoreModule();

        initializeAndPruneModules();

        // Clear the map to remove schemas associated with modules that failed to load
        clearAllSchemaDetails();

        // Now that the core module is upgraded, upgrade the "labkey" schema in all module-required external data sources
        // to match the core module version. Each external data source records their upgrade scripts and versions their
        // module schemas via the tables in its own "labkey" schema.
        upgradeLabKeySchemaInExternalDataSources();

        for (ModuleContext context : getAllModuleContexts())
            _contextMap.put(context.getName(), context);

        //Make sure we have a context for all modules, even ones we haven't seen before
        for (Module module : _modules)
        {
            ModuleContext context = _contextMap.get(module.getName());
            if (null == context)
            {
                context = new ModuleContext(module);
                _contextMap.put(context.getName(), context);
            }
            if (context.getInstalledVersion() < module.getVersion())
                context.setModuleState(ModuleState.InstallRequired);
            else
                context.setModuleState(ModuleState.ReadyToStart);
            /*
        else if (!context.isEnabled())
            context.setModuleState(ModuleState.Disabled);
            */
        }

        // Core module should be upgraded and ready-to-run
        ModuleContext coreCtx = _contextMap.get(DefaultModule.CORE_MODULE_NAME);
        assert (ModuleState.ReadyToStart == coreCtx.getModuleState());

        List<String> modulesRequiringUpgrade = new LinkedList<>();
        List<String> additionalSchemasRequiringUpgrade = new LinkedList<>();

        for (Module m : _modules)
        {
            ModuleContext ctx = getModuleContext(m);
            if (ctx.isNewInstall() || ctx.getInstalledVersion() < m.getVersion())
            {
                modulesRequiringUpgrade.add(ctx.getName());
            }
            else
            {
                // Module doesn't require an upgrade, but we still need to check if schemas in this module require upgrade.
                // The scenario is a schema in an external data source that needs to be installed or upgraded.
                List<String> schemasInThisModule = additionalSchemasRequiringUpgrade(m);
                additionalSchemasRequiringUpgrade.addAll(schemasInThisModule);
            }
        }

        if (!modulesRequiringUpgrade.isEmpty())
            _log.info("Modules requiring upgrade: " + modulesRequiringUpgrade.toString());

        if (!additionalSchemasRequiringUpgrade.isEmpty())
            _log.info((modulesRequiringUpgrade.isEmpty() ? "Schemas" : "Additional schemas") + " requiring upgrade: " + additionalSchemasRequiringUpgrade.toString());

        if (!modulesRequiringUpgrade.isEmpty() || !additionalSchemasRequiringUpgrade.isEmpty())
            setUpgradeState(UpgradeState.UpgradeRequired);

        // terminateAfterStartup flag allows "headless upgrade". Now supports initial install as well
        boolean terminateAfterStartup = Boolean.valueOf(System.getProperty("terminateAfterStartup"));
        Execution execution = terminateAfterStartup ? Execution.Synchronous : Execution.Asynchronous;

        startNonCoreUpgradeAndStartup(User.getSearchUser(), execution, coreRequiredUpgrade);  // TODO: Change search user to system user

        _log.info("LabKey Server startup is complete; " + (Execution.Synchronous == execution ? "all modules have been upgraded and initialized" : "modules are being upgraded and initialized in the background"));

        if (terminateAfterStartup)
            System.exit(0);
    }

    // If in production mode then make sure this isn't a development build, #21567
    private void verifyProductionModeMatchesBuild()
    {
        if (!AppProps.getInstance().isDevMode())
        {
            if (isDevelopmentBuild(getCoreModule()))
                throw new ConfigurationException("This server does not appear to be compiled for production mode");

            getModules()
                .stream()
                .filter(module -> module.getBuildType() != null && isDevelopmentBuild(module))
                .forEach(module -> {
                    addModuleFailure(module.getName(), new IllegalStateException("Module " + module + " was not compiled in production mode"));
                });
        }
    }

    private boolean isDevelopmentBuild(Module module)
    {
        return !PRODUCTION_BUILD_TYPE.equalsIgnoreCase(module.getBuildType());
    }

    /** Goes through all the modules, initializes them, and removes the ones that fail to start up */
    private void initializeAndPruneModules()
    {
        ListIterator<Module> iterator = _modules.listIterator();
        Module core = iterator.next();  // Skip core because we already initialized it
        if (!core.equals(getCoreModule()))
            throw new IllegalStateException("First module should be core");

        //initialize each module in turn
        while (iterator.hasNext())
        {
            Module module = iterator.next();

            try
            {
                try
                {
                    // Make sure all its dependencies initialized successfully
                    verifyDependencies(module);
                    module.initialize();
                }
                catch (DatabaseNotSupportedException | ModuleDependencyException e)
                {
                    // In production mode, treat these exceptions as a module initialization error
                    if (!AppProps.getInstance().isDevMode())
                        throw e;

                    // In dev mode, make them warnings so devs can easily switch databases
                    removeModule(iterator, module, false, e);
                }
            }
            catch(Throwable t)
            {
                removeModule(iterator, module, true, t);
            }
        }

        // Make the collections of modules read-only since we expect no further modifications
        _modules = Collections.unmodifiableList(_modules);
        _moduleMap = Collections.unmodifiableMap(_moduleMap);
        _moduleClassMap = Collections.unmodifiableMap(_moduleClassMap);

        // All modules are initialized (controllers are registered), so initialize the controller-related maps
        ViewServlet.initialize();
        ModuleLoader.getInstance().initControllerToModule();

        // Doesn't really belong here, but needs to happen after all modules' init() but before first request. CONSIDER: Split
        // AuthenticationManager into a couple singletons, AuthenticationProviderRegistry and AuthenticationManager. Modules
        // could then register their providers with the registry and first reference to the manager would trigger initialize().
        AuthenticationManager.initialize();
    }

    // Check a module's dependencies and throw on the first one that's not present (i.e., it was removed because its initialize() failed)
    private void verifyDependencies(Module module)
    {
        for (String dependency : module.getModuleDependenciesAsSet())
            if (!_moduleMap.containsKey(dependency))
                throw new ModuleDependencyException(dependency);
    }

    private static class ModuleDependencyException extends ConfigurationException
    {
        public ModuleDependencyException(String dependencyName)
        {
            super("This module depends on the \"" + dependencyName + "\" module, which failed to initialize");
        }
    }

    private void removeModule(ListIterator<Module> iterator, Module current, boolean treatAsError, Throwable t)
    {
        String name = current.getName();

        if (treatAsError)
        {
            _log.error("Unable to initialize module " + name, t);
            //noinspection ThrowableResultOfMethodCallIgnored
            _moduleFailures.put(name, t);
        }
        else
        {
            _log.warn("Unable to initialize module " + name + " due to: " + t.getMessage());
        }

        iterator.remove();
        removeMapValue(current, _moduleClassMap);
        removeMapValue(current, _moduleMap);
    }

    private void removeMapValue(Module module, Map<?, Module> map)
    {
        map.entrySet().removeIf(entry -> entry.getValue() == module);
    }

    private List<String> additionalSchemasRequiringUpgrade(Module module)
    {
        SqlScriptProvider provider = new FileSqlScriptProvider(module);
        List<String> schemaNames = new LinkedList<>();

        for (DbSchema schema : provider.getSchemas())
        {
            SqlScriptManager manager = SqlScriptManager.get(provider, schema);

            if (manager.requiresUpgrade())
                schemaNames.add(schema.getDisplayName());
        }

        return schemaNames;
    }

    // Set the project source root based upon the core module's source path or the project.root system property.
    private void setProjectRoot(Module core)
    {
        List<String> possibleRoots = new ArrayList<>();
        if (null != System.getProperty("project.root"))
            possibleRoots.add(System.getProperty("project.root"));
        if (null != core.getSourcePath() && !core.getSourcePath().isEmpty())
            possibleRoots.add(core.getSourcePath() + "/../../..");

        for (String root : possibleRoots)
        {
            File projectRoot = new File(root);
            if (projectRoot.exists())
            {
                AppProps.getInstance().setProjectRoot(FileUtil.getAbsoluteCaseSensitiveFile(projectRoot).toString());
                // set the root only once
                break;
            }
        }
    }

    /** We want to roll the file every time the server starts, which isn't directly supported by Log4J so we do it manually */
    private void rollErrorLogFile(Logger logger)
    {
        while (logger != null && !logger.getAllAppenders().hasMoreElements())
        {
            logger = (Logger)logger.getParent();
        }

        if (logger == null)
        {
            return;
        }

        for (Enumeration e2 = logger.getAllAppenders(); e2.hasMoreElements();)
        {
            final Appender appender = (Appender)e2.nextElement();
            if (appender instanceof RollingFileAppender && "ERRORS".equals(appender.getName()))
            {
                RollingFileAppender rfa = (RollingFileAppender)appender;
                String fileName = rfa.getFile();
                if (fileName == null)
                {
                    throw new IllegalStateException("Error rolling labkey-errors.log file, likely a file permissions problem in CATALINA_HOME/logs");
                }
                rfa.rollOver();
            }
        }
    }

    private List<Module> loadModules(List<File> explodedModuleDirs)
    {
        ApplicationContext parentContext = ServiceRegistry.get().getApplicationContext();

        Map<String, File> moduleNameToFile = new CaseInsensitiveHashMap<>();
        List<Module> modules = new ArrayList<>();
        Pattern moduleNamePattern = Pattern.compile(MODULE_NAME_REGEX);
        for(File moduleDir : explodedModuleDirs)
        {
            File moduleXml = new File(moduleDir, "config/module.xml");
            try
            {
                Module module;
                if (moduleXml.exists())
                {
                    module = loadModuleFromXML(parentContext, moduleXml);
                }
                else
                {
                    module = loadModuleFromProperties(parentContext, moduleDir);
                }

                if (null != module)
                {
                    module.lock();

                    //don't load if we've already loaded a module of the same name
                    if (moduleNameToFile.containsKey(module.getName()))
                    {
                        _log.warn("Module with name '" + module.getName() + "' has already been loaded from "
                                + moduleNameToFile.get(module.getName()).getAbsolutePath() + ". Skipping additional copy of the module in " + moduleDir);
                    }
                    else if (!moduleNamePattern.matcher(module.getName()).matches())
                    {
                        IllegalArgumentException t = new IllegalArgumentException("Module names may only contain alpha, numeric, and underscore characters. Invalid name: '" + module.getName() + "'");
                        _log.error("Invalid module", t);
                        //noinspection ThrowableResultOfMethodCallIgnored
                        _moduleFailures.put(moduleDir.getName(), t);
                    }
                    else
                    {
                        module.setExplodedPath(moduleDir);
                        modules.add(module);
                        moduleNameToFile.put(module.getName(), moduleDir);
                    }

                    // Check for LabKey module info. Missing info is only a warning for now, but may be an error later.
                    if (module.getOrganization() != null && module.getOrganization().toLowerCase().contains("labkey"))
                    {
                        List<String> report = checkLabKeyModuleInfo(module);
                        if (report != null)
                            _log.warn("Missing expected info on module '" + module.getName() + "': " + StringUtils.join(report, ", "));
                    }
                }
                else
                    _log.error("No module class was found for the module '" + moduleDir.getName() + "'");
            }
            catch (Throwable t)
            {
                _log.error("Unable to instantiate module " + moduleDir, t);
                //noinspection ThrowableResultOfMethodCallIgnored
                _moduleFailures.put(moduleDir.getName(), t);
            }
        }
        return modules;
    }

    /** Load module metadata from a .properties file */
    private Module loadModuleFromProperties(ApplicationContext parentContext, File moduleDir) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException
    {
        //check for simple .properties file
        File modulePropsFile = new File(moduleDir, "config/module.properties");
        Properties props = new Properties();
        if (modulePropsFile.exists())
        {
            try (FileInputStream in = new FileInputStream(modulePropsFile))
            {
                props.load(in);
            }
            catch (IOException e)
            {
                _log.error("Error reading module properties file '" + modulePropsFile.getAbsolutePath() + "'", e);
            }
        }

        //assume that module name is directory name
        String moduleName = moduleDir.getName();
        if (props.containsKey("name"))
            moduleName = props.getProperty("name");

        if (moduleName == null || moduleName.length() == 0)
            throw new ConfigurationException("Simple module must specify a name in config/module.xml or config/module.properties: " + moduleDir.getParent());

        // Create the module instance
        DefaultModule simpleModule;
        if (props.containsKey("ModuleClass"))
        {
            String moduleClassName = props.getProperty("ModuleClass");
            Class<DefaultModule> moduleClass = (Class<DefaultModule>)Class.forName(moduleClassName);
            simpleModule = moduleClass.newInstance();
        }
        else
        {
            simpleModule = new SimpleModule();
        }

        simpleModule.setName(moduleName);

        //get SourcePath property if there is one
        String srcPath = (String)props.get("SourcePath");

        //Ensure property value isn't blank
        if (StringUtils.isNotBlank(srcPath))
            simpleModule.setSourcePath(srcPath);
        else
            simpleModule.setSourcePath(moduleDir.getAbsolutePath());
        BeanUtils.populate(simpleModule, props);
        simpleModule.setApplicationContext(parentContext);

        return simpleModule;
    }

    /** Read module metadata out of XML file */
    private Module loadModuleFromXML(ApplicationContext parentContext, File moduleXml)
    {
        ApplicationContext applicationContext;
        if (null != ModuleLoader.getInstance() && null != ModuleLoader.getServletContext())
        {
            XmlWebApplicationContext beanFactory = new XmlWebApplicationContext();
            beanFactory.setConfigLocations(new String[]{moduleXml.toURI().toString()});
            beanFactory.setParent(parentContext);
            beanFactory.setServletContext(new SpringModule.ModuleServletContextWrapper(ModuleLoader.getServletContext()));
            beanFactory.refresh();
            applicationContext = beanFactory;
        }
        else
        {
            FileSystemXmlApplicationContext beanFactory = new FileSystemXmlApplicationContext();
            beanFactory.setConfigLocations(new String[]{moduleXml.toURI().toString()});
            beanFactory.setParent(parentContext);
            beanFactory.refresh();
            applicationContext = beanFactory;
        }

        try
        {
            return applicationContext.getBean("moduleBean", Module.class);
        }
        catch (NoSuchBeanDefinitionException x)
        {
            _log.error("module configuration does not specify moduleBean: " + moduleXml);
        }
        catch (RuntimeException x)
        {
            _log.error("error reading module configuration: " + moduleXml.getPath(), x);
        }
        return null;
    }

    public @Nullable List<String> checkLabKeyModuleInfo(Module m)
    {
        List<String> missing = new ArrayList<>(5);

        if (StringUtils.isBlank(m.getLabel()))
            missing.add("Label");

//        if (StringUtils.isBlank(m.getDescription()))
//            missing.add("Description");
//
//        if (StringUtils.isBlank(m.getUrl()))
//            missing.add("URL");

        if (!"https://www.labkey.com/".equals(m.getOrganizationUrl()))
            missing.add("OrganizationURL");

//        if (StringUtils.isBlank(m.getMaintainer()))
//            missing.add("Maintainer");

        if (StringUtils.isBlank(m.getLicense()))
            missing.add("License");

//        if (StringUtils.isBlank(m.getLicenseUrl()))
//            missing.add("LicenseURL");

        return missing.isEmpty() ? null : missing;
    }

    public void setWebappDir(File webappDir)
    {
        if (_webappDir != null && !_webappDir.equals(webappDir))
        {
            throw new IllegalStateException("WebappDir is already set to " + _webappDir + ", cannot reset it to " + webappDir);
        }
        _webappDir = webappDir;
    }

    // Attempt to parse "enlistment.id" property from a file named "enlistment.properties" in this directory, if it exists
    public @Nullable String loadEnlistmentId(File directory)
    {
        String enlistmentId = null;
        File file = new File(directory, "enlistment.properties");

        if (file.exists())
        {
            Properties props = new Properties();

            try (InputStream is = new FileInputStream(file))
            {
                props.load(is);
                enlistmentId = props.getProperty("enlistment.id");
            }
            catch (IOException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        return enlistmentId;
    }


    public File getWebappDir()
    {
        return _webappDir;
    }

    /**
     * Checks Java version and throws if it's not supported.
     *
     * Warnings for deprecated Java versions are specified in BootstrapTemplate#buildWarnings
     *
     * @throws ConfigurationException if Java version is not supported
     */
    @JavaRuntimeVersion
    private void verifyJavaVersion() throws ConfigurationException
    {
        if (!SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_8))
            throw new ConfigurationException("Unsupported Java runtime version: " + SystemUtils.JAVA_VERSION + ". LabKey Server requires Java 8.");
    }

    /**
     * Sets the running Tomcat version, if servlet container is recognized and supported. Otherwise, version is left UNKNOWN.
     *
     * Warnings for deprecated Tomcat versions are specified in BootstrapTemplate#buildWarnings
     *
     * @throws ConfigurationException if Tomcat version is not supported
     */
    private void setTomcatVersion()
    {
        String serverInfo = ModuleLoader.getServletContext().getServerInfo();

        if (serverInfo.startsWith(APACHE_TOMCAT_SERVER_NAME_PREFIX))
        {
            String[] versionParts = serverInfo.substring(APACHE_TOMCAT_SERVER_NAME_PREFIX.length()).split("\\.");
            int majorVersion = Integer.valueOf(versionParts[0]);

            if (majorVersion < 7)
                throw new ConfigurationException("Unsupported Tomcat version: " + serverInfo + ". LabKey Server requires Apache Tomcat 7 or above.");

            _tomcatVersion = TomcatVersion.get(majorVersion);
        }
    }

    public TomcatVersion getTomcatVersion()
    {
        return _tomcatVersion;
    }

    // Enumerate each jdbc DataSource in labkey.xml and tell DbScope to initialize them
    private void initializeDataSources() throws ServletException
    {
        _log.debug("Ensuring that all databases specified by datasources in webapp configuration xml are present");

        Map<String, DataSource> dataSources = new TreeMap<>(String::compareTo);

        String labkeyDsName;

        try
        {
            // Ensure that the labkeyDataSource (or cpasDataSource, for old installations) exists in
            // labkey.xml / cpas.xml and create the associated database if it doesn't already exist.
            labkeyDsName = ensureDatabase(LABKEY_DATA_SOURCE, CPAS_DATA_SOURCE);

            InitialContext ctx = new InitialContext();
            Context envCtx = (Context) ctx.lookup("java:comp/env");
            NamingEnumeration<Binding> iter = envCtx.listBindings("jdbc");

            while (iter.hasMore())
            {
                try
                {
                    Binding o = iter.next();
                    String dsName = o.getName();
                    DataSource ds = (DataSource) o.getObject();
                    dataSources.put(dsName, ds);
                }
                catch (NamingException e)
                {
                    _log.error("DataSources are not properly configured in " + AppProps.getInstance().getWebappConfigurationFilename() + ".", e);
                }
            }
        }
        catch (Exception e)
        {
            throw new ConfigurationException("DataSources are not properly configured in " + AppProps.getInstance().getWebappConfigurationFilename() + ".", e);
        }

        DbScope.initializeScopes(labkeyDsName, dataSources);
    }

    // For each name, look for a matching data source in labkey.xml. If found, attempt a connection and
    // create the database if it doesn't already exist, report any errors and return the name.
    public String ensureDatabase(@NotNull String primaryName, String... alternativeNames) throws NamingException, ServletException
    {
        List<String> dsNames = new ArrayList<>();
        dsNames.add(primaryName);
        dsNames.addAll(Arrays.asList(alternativeNames));

        InitialContext ctx = new InitialContext();
        Context envCtx = (Context) ctx.lookup("java:comp/env");

        DataSource dataSource = null;
        String dsName = null;

        for (String name : dsNames)
        {
            dsName = name;

            try
            {
                dataSource = (DataSource)envCtx.lookup("jdbc/" + dsName);
                break;
            }
            catch (NameNotFoundException e)
            {
                // Name not found is fine (for now); keep looping through alternative names
            }
            catch (NamingException e)
            {
                String message = e.getMessage();

                // dataSource is defined but the database doesn't exist. This happens only with the Tomcat JDBC
                // connection pool, which attempts a connection on bind. In this case, we need to use some horrible
                // reflection to get the properties we need to create the database.
                if ((message.contains("FATAL: database") && message.contains("does not exist")) ||
                    (message.contains("Cannot open database") && message.contains("requested by the login. The login failed.")))
                {
                    try
                    {
                        Object namingContext = envCtx.lookup("jdbc");
                        Field bindingsField = namingContext.getClass().getDeclaredField("bindings");
                        bindingsField.setAccessible(true);
                        Map bindings = (Map)bindingsField.get(namingContext);
                        Object namingEntry = bindings.get(dsName);
                        Field valueField = namingEntry.getClass().getDeclaredField("value");
                        Reference reference = (Reference)valueField.get(namingEntry);

                        String driverClassname = (String)reference.get("driverClassName").getContent();
                        SqlDialect dialect = SqlDialectManager.getFromDriverClassname(dsName, driverClassname);
                        String url = (String)reference.get("url").getContent();
                        String password = (String)reference.get("password").getContent();
                        String username = (String)reference.get("username").getContent();

                        DbScope.createDataBase(dialect, url, username, password, DbScope.isPrimaryDataSource(dsName));
                    }
                    catch (Exception e2)
                    {
                        throw new ConfigurationException("Failed to retrieve \"" + dsName + "\" properties from " + AppProps.getInstance().getWebappConfigurationFilename() + ". Try creating the database manually and restarting the server.", e2);
                    }

                    // Try it again
                    dataSource = (DataSource)envCtx.lookup("jdbc/" + dsName);
                    break;
                }

                throw new ConfigurationException("Failed to load DataSource \"" + dsName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ".", e);
            }
        }

        if (null == dataSource)
            throw new ConfigurationException("You must have a DataSource named \"" + primaryName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ".");

        DbScope.ensureDataBase(dsName, dataSource);

        return dsName;
    }


    // Initialize and update the CoreModule "manually", outside the normal UI-based process. We want to change the core
    // tables before we display pages, require login, check permissions, or initialize any of the other modules.
    // Returns true if core module required upgrading, otherwise false
    private boolean upgradeCoreModule() throws ServletException
    {
        Module coreModule = ModuleLoader.getInstance().getCoreModule();
        if (coreModule == null)
        {
            throw new IllegalStateException("CoreModule does not exist");
        }

        coreModule.initialize();

        ModuleContext coreContext;

        // If modules table doesn't exist (bootstrap case), then new up a core context
        if (getTableInfoModules().getTableType() == DatabaseTableType.NOT_IN_DB)
            coreContext = new ModuleContext(coreModule);
        else
            coreContext = getModuleContext("Core");

        // Does the core module need to be upgraded?
        if (coreContext.getInstalledVersion() >= coreModule.getVersion())
            return false;

        if (coreContext.isNewInstall())
        {
            _log.debug("Initializing core module to " + coreModule.getFormattedVersion());
        }
        else
        {
            if (coreContext.getInstalledVersion() < EARLIEST_UPGRADE_VERSION)
                throw new ConfigurationException("Can't upgrade from LabKey Server version " + coreContext.getInstalledVersion() + "; installed version must be " + EARLIEST_UPGRADE_VERSION + " or greater.");

            _log.debug("Upgrading core module from " + ModuleContext.formatVersion(coreContext.getInstalledVersion()) + " to " + coreModule.getFormattedVersion());
        }

        _contextMap.put(coreModule.getName(), coreContext);

        try
        {
            ModuleUpgrader coreUpgrader = new ModuleUpgrader(Collections.singletonList(coreModule));
            coreUpgrader.upgrade();
        }
        catch (Exception e)
        {
            Throwable cause = e.getCause();

            if (cause instanceof ServletException)
                throw (ServletException)cause;

            throw new ServletException(e);
        }

        return true;
    }


    // TODO: Move this code into SqlScriptManager
    private void upgradeLabKeySchemaInExternalDataSources()
    {
        // Careful... the "labkey" scripts are sourced from and versioned based on the core module, but are run and
        // tracked within the external data source's "labkey" schema. This odd situation is orchestrated by the special
        // LabKeyDbSchema subclass, working with ExternalDataSourceSqlScriptManager.

        // Look for "labkey" script files in the "core" module. Version the labkey schema in all scopes to current version of core.
        Module coreModule = ModuleLoader.getInstance().getCoreModule();
        FileSqlScriptProvider provider = new FileSqlScriptProvider(coreModule);
        double to = coreModule.getVersion();

        for (String name : getAllModuleDataSourceNames())
        {
            try
            {
                DbScope scope = DbScope.getDbScope(name);
                if (null == scope || !scope.getSqlDialect().canExecuteUpgradeScripts())
                    continue;

                // This should return a special DbSchema subclass (LabKeyDbSchema) that eliminates the data source prefix
                // from display name, causing labkey-*-*.sql scripts to be found.
                DbSchema labkeySchema = scope.getLabKeySchema();
                SqlScriptManager manager = SqlScriptManager.get(provider, labkeySchema);
                List<SqlScript> scripts = manager.getRecommendedScripts(to);

                if (!scripts.isEmpty())
                {
                    _log.info("Upgrading the \"labkey\" schema in \"" + scope.getDisplayName() + "\" to " + to);
                    SqlScriptRunner.runScripts(coreModule, ModuleLoader.getInstance().getUpgradeUser(), scripts);
                }

                manager.updateSchemaVersion(to);
            }
            catch (SqlScriptException | SQLException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
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

    public void addModuleFailure(String moduleName, Throwable t)
    {
        //noinspection ThrowableResultOfMethodCallIgnored
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
            return new HashMap<>(_moduleFailures);
        }
    }

    private TableInfo getTableInfoModules()
    {
        return _core.getTableInfoModules();
    }

    public ModuleContext getModuleContext(Module module)
    {
        return _contextMap.get(module.getName());
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        if (isUpgradeRequired())
        {
            setDeferUsageReport(true);
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

    private void runDropScripts() throws SqlScriptException, SQLException
    {
        synchronized (UPGRADE_LOCK)
        {
            List<Module> modules = getModules();
            ListIterator<Module> iter = modules.listIterator(modules.size());

            while (iter.hasPrevious())
                runScripts(iter.previous(), SchemaUpdateType.Before);
        }
    }

    private void runCreateScripts() throws SqlScriptException, SQLException
    {
        synchronized (UPGRADE_LOCK)
        {
            for (Module module : getModules())
                runScripts(module, SchemaUpdateType.After);
        }
    }

    public void runScripts(Module module, SchemaUpdateType type)
    {
        FileSqlScriptProvider provider = new FileSqlScriptProvider(module);

        for (DbSchema schema : type.orderSchemas(provider.getSchemas()))
        {
            if (schema.getSqlDialect().canExecuteUpgradeScripts())
            {
                try
                {
                    SqlScript script = type.getScript(provider, schema);

                    if (null != script)
                        SqlScriptRunner.runScripts(module, null, Collections.singletonList(script));
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Error running scripts in module " + module.getName(), e);
                }
            }
        }
    }

    // Runs the drop and create scripts in every module
    public void recreateViews() throws SqlScriptException, SQLException
    {
        synchronized (UPGRADE_LOCK)
        {
            runDropScripts();
            runCreateScripts();
        }
    }

    /**
     * Module upgrade scripts have completed, and we are now completing module startup.
     * @return true if module startup in progress.
     */
    public boolean isStartupInProgress()
    {
        synchronized (STARTUP_LOCK)
        {
            return _startupState == StartupState.StartupInProgress;
        }
    }

    /**
     * All module startup is complete.
     * @return true if complete.
     */
    public boolean isStartupComplete()
    {
        synchronized (STARTUP_LOCK)
        {
            return _startupState == StartupState.StartupComplete;
        }
    }

    private void setStartupState(StartupState state)
    {
        synchronized (STARTUP_LOCK)
        {
            _startupState = state;
        }
    }

    public String getStartingUpMessage()
    {
        synchronized (STARTUP_LOCK)
        {
            return _startingUpMessage;
        }
    }

    /** Set a message that will be displayed in the upgrade/startup UI. */
    public void setStartingUpMessage(String message)
    {
        synchronized (STARTUP_LOCK)
        {
            _startingUpMessage = message;
            if (message != null)
                _log.info(message);
        }
    }

    /**
     * Initiate the module startup process.
     *
     */
    private void initiateModuleStartup()
    {
        if (isStartupInProgress() || isStartupComplete())
            return;

        if (isUpgradeRequired())
            throw new IllegalStateException("Can't start modules before upgrade is complete");

        setStartupState(StartupState.StartupInProgress);
        setStartingUpMessage("Starting up modules");

        // Run module startup
        try
        {
            completeStartup();
            attemptStartBackgroundThreads();
            if (isNewInstall())
                ContextListener.afterNewInstallComplete();
        }
        catch (Throwable t)
        {
            ModuleLoader.getInstance().setStartupFailure(t);
            _log.error("Failure during module startup", t);
        }
    }

    private volatile boolean _backgroundThreadsStarted = false;

    public void attemptStartBackgroundThreads()
    {
        synchronized (STARTUP_LOCK)
        {
            if (!_backgroundThreadsStarted && isStartupComplete() && AppProps.getInstance().isSetBaseServerUrl())
            {
                _backgroundThreadsStarted = true;

                for (Module module : _modules)
                {
                    try
                    {
                        module.startBackgroundThreads();
                    }
                    catch (Throwable t)
                    {
                        _log.error("Error starting background threads for module \"" + module.getName() + "\"", t);
                    }
                }
            }
        }
    }

    /**
     * Perform the final stage of startup:
     * <ol>
     *     <li>{@link Module#startup(ModuleContext) module startup}</li>
     *     <li>Register module resources (eg, creating module assay providers)</li>
     *     <li>Deferred upgrade tasks</li>
     *     <li>Startup listeners</li>
     * </ol>
     *
     * Once the deferred upgrade tasks have run, the module is considered {@link ModuleState#Started started}.
     */
    private void completeStartup()
    {
        for (Module m : _modules)
        {
            // Module startup
            try
            {
                ModuleContext ctx = getModuleContext(m);
                ctx.setModuleState(ModuleState.Starting);
                setStartingUpMessage("Starting module '" + m.getName() + "'");
                m.startup(ctx);
            }
            catch (Throwable x)
            {
                setStartupFailure(x);
                _log.error("Failure starting module: " + m.getName(), x);
            }
        }

        // Run any deferred upgrades, after all of the modules are in the Running state so that we
        // know they've registered their listeners
        for (Module m : _modules)
        {
            try
            {
                ModuleContext ctx = getModuleContext(m);
                m.runDeferredUpgradeRunnables();
                ctx.setModuleState(ModuleState.Started);
            }
            catch (Throwable x)
            {
                setStartupFailure(x);
                _log.error("Failure starting module: " + m.getName(), x);
            }
        }

        // Finally, fire the startup complete event
        ContextListener.moduleStartupComplete(_servletContext);

        clearAllSchemaDetails();
        setStartupState(StartupState.StartupComplete);
        setStartingUpMessage("Module startup complete");
    }


    void saveModuleContext(ModuleContext context)
    {
        ModuleContext stored = getModuleContext(context.getName());
        if (null == stored)
            Table.insert(null, getTableInfoModules(), context);
        else
            Table.update(null, getTableInfoModules(), context, context.getName());
    }


    // Not transacted: SQL Server sp_dropapprole can't be called inside a transaction
    public void removeModule(ModuleContext context)
    {
        DbScope scope = _core.getSchema().getScope();
        SqlDialect dialect = _core.getSqlDialect();

        try
        {
            String moduleName = context.getName();
            _log.info("Deleting module " + moduleName);
            String sql = "DELETE FROM " + _core.getTableInfoSqlScripts() + " WHERE ModuleName = ? AND Filename " + dialect.getCaseInsensitiveLikeOperator() + " ?";

            for (String schema : context.getSchemaList())
            {
                _log.info("Dropping schema " + schema);
                new SqlExecutor(_core.getSchema()).execute(sql, moduleName, schema + "-%");
                scope.getSqlDialect().dropSchema(_core.getSchema(), schema);
            }

            Table.delete(getTableInfoModules(), context.getName());
        }
        catch (SQLException e)
        {
            _log.error("Error attempting to delete module " + context.getName());
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }


    public void startNonCoreUpgradeAndStartup(User user, Execution execution, boolean coreRequiredUpgrade) throws Exception
    {
        synchronized(UPGRADE_LOCK)
        {
            if (_upgradeState == UpgradeState.UpgradeRequired)
            {
                List<Module> modules = new ArrayList<>(getModules());
                modules.remove(ModuleLoader.getInstance().getCoreModule());
                setUpgradeState(UpgradeState.UpgradeInProgress);
                setUpgradeUser(user);

                ModuleUpgrader upgrader = new ModuleUpgrader(modules);
                upgrader.upgrade(() -> {
                    afterUpgrade(true);
                }, execution);
            }
            else
            {
                execution.run(() -> afterUpgrade(coreRequiredUpgrade));
            }
        }
    }


    // Final step in upgrade process: set the upgrade state to complete, perform post-upgrade tasks, and start up the modules.
    // performedUpgrade is true if any module required upgrading
    private void afterUpgrade(boolean performedUpgrade)
    {
        setUpgradeState(UpgradeState.UpgradeComplete);

        if (performedUpgrade)
        {
            handleUnkownModules();
            updateModuleProperties();
        }

        initiateModuleStartup();
        verifyRequiredModules();
    }


    // If the "requiredModules" parameter is present in labkey.xml then fail startup if any specified module is missing.
    // Particularly interesting for compliant deployments, e.g., <Parameter name="requiredModules" value="Compliance"/>
    private void verifyRequiredModules()
    {
        String requiredModules = getServletContext().getInitParameter("requiredModules");

        if (null != requiredModules)
        {
            List<String> missedModules = Arrays.stream(requiredModules.split(","))
                .filter(name->!_moduleMap.containsKey(name))
                .collect(Collectors.toList());

            if (!missedModules.isEmpty())
                setStartupFailure(new ConfigurationException("Required module" + (missedModules.size() > 1 ? "s" : "") + " not present: " + missedModules));
        }
    }


    // Remove all unknown modules that are marked as AutoUninstall
    public void handleUnkownModules()
    {
        getUnknownModuleContexts()
            .values()
            .stream()
            .filter(ModuleContext::isAutoUninstall)
            .forEach(this::removeModule);
    }


    private void updateModuleProperties()
    {
        for (Module module : getModules())
        {
            try
            {
                Map<String, Object> map = new HashMap<>();
                map.put("AutoUninstall", module.isAutoUninstall());
                map.put("Schemas", StringUtils.join(module.getSchemaNames(), ','));
                Table.update(getUpgradeUser(), getTableInfoModules(), map, module.getName());
            }
            catch (RuntimeSQLException e)
            {
                // This should be fixed now (see #24473), but leave detailed logging in place just in case
                ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.ExtraMessage, module.getName(), false);
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }


    public void setUpgradeUser(User user)
    {
        synchronized(UPGRADE_LOCK)
        {
            assert null == _upgradeUser;
            _upgradeUser = user;
        }
    }

    public User getUpgradeUser()
    {
        synchronized(UPGRADE_LOCK)
        {
            return _upgradeUser;
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

    // Did this server start up with no modules installed?  If so, it's a new install. This lets us tailor the
    // module upgrade UI to "install" or "upgrade," as appropriate.
    public boolean isNewInstall()
    {
        return _newInstall;
    }

    public void destroy()
    {
        // In the case of a startup failure, _modules may be null. We want to allow a context reload to succeed in this case,
        // since the reload may contain the code change to fix the problem
        if (_modules != null)
        {
            _modules.forEach(Module::destroy);
        }
    }

    public boolean hasModule(String name)
    {
        return _moduleMap.containsKey(name);
    }

    public Module getModule(String name)
    {
        return _moduleMap.get(name);
    }

    public <M extends Module> M getModule(Class<M> moduleClass)
    {
        return (M) _moduleClassMap.get(moduleClass);
    }

    public Module getCoreModule()
    {
        return getModule(DefaultModule.CORE_MODULE_NAME);
    }

    /** @return all known modules, sorted in dependency order */
    public List<Module> getModules()
    {
        return _modules;
    }

    public List<Module> getModules(boolean userHasEnableRestrictedModulesPermission)
    {
        if (userHasEnableRestrictedModulesPermission)
            return getModules();

        return _modules
            .stream()
            .filter(module -> !module.getRequireSitePermission())
            .collect(Collectors.toList());
    }

    /**
     * @return the modules, sorted in reverse dependency order. Typically used to resolve the most specific version of
     * a resource when one module "subclasses" another module.
     */
    public Collection<Module> orderModules(Collection<Module> modules)
    {
        List<Module> result = new ArrayList<>(modules.size());
        result.addAll(ModuleLoader.getInstance().getModules()
            .stream().filter(modules::contains)
            .collect(Collectors.toList()));
        Collections.reverse(result);
        return result;
    }

    // Returns a set of data source names representing all external data sources that are required for module schemas
    public Set<String> getAllModuleDataSourceNames()
    {
        // Find all the external data sources that modules require
        Set<String> allModuleDataSourceNames = new LinkedHashSet<>();

        for (Module module : _modules)
            allModuleDataSourceNames.addAll(getModuleDataSourceNames(module));

        return allModuleDataSourceNames;
    }

    public Set<String> getModuleDataSourceNames(Module module)
    {
        Set<String> moduleDataSourceNames = new LinkedHashSet<>();

        for (String schemaName : module.getSchemaNames())
        {
            int idx = schemaName.indexOf('.');

            if (-1 != idx)
                moduleDataSourceNames.add(schemaName.substring(0, idx) + "DataSource");
        }

        return moduleDataSourceNames;
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
        LinkedList<String> list = new LinkedList<>();

        for (Module m : _modules)
            list.addAll(m.getSummary(c));

        return list;
    }

    public void initControllerToModule()
    {
        synchronized(_controllerNameToModule)
        {
            if (!_controllerNameToModule.isEmpty())
                return;
            List<Module> allModules = ModuleLoader.getInstance().getModules();
            for (Module module : allModules)
            {
                TreeSet<String> set = new CaseInsensitiveTreeSet();

                for (Map.Entry<String, Class<? extends Controller>> entry : module.getControllerNameToClass().entrySet())
                {
                    String key = entry.getKey();
                    if (!set.add(key))
                        continue;   // Avoid duplicate work

                    _controllerNameToModule.put(key, module);
                    _controllerNameToModule.put(key.toLowerCase(), module);

                    Class clazz = entry.getValue();
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


    /** This is not for static java controllers, only use for dynamically loaded controllers */
    public void addControllerAlias(Module m, String name, Class clss)
    {
        synchronized (_controllerNameToModule)
        {
            _controllerNameToModule.put(name, m);
            _controllerNameToModule.put(name.toLowerCase(), m);
        }
    }


    public Module getModuleForController(String controllerName)
    {
        synchronized(_controllerNameToModule)
        {
            Module module = _controllerNameToModule.get(controllerName);
            if (null != module)
                return module;

            int i = controllerName.indexOf('-');
            if (-1 == i)
                return null;

            String prefix = controllerName.substring(0,i);
            module = _controllerNameToModule.get(prefix);
            if (null != module)
                _controllerNameToModule.put(controllerName, module);
            return module;
        }
    }


    /*  Use data source qualified name (e.g., core or external.myschema)  */
    public @Nullable Module getModuleForSchemaName(String fullyQualifiedSchemaName)
    {
        SchemaDetails details = getSchemaDetails(fullyQualifiedSchemaName);

        return null != details ? details.getModule() : null;
    }

    /*  Use data source qualified name (e.g., core or external.myschema)  */
    public @Nullable DbSchemaType getSchemaTypeForSchemaName(String fullyQualifiedSchemaName)
    {
        SchemaDetails details = getSchemaDetails(fullyQualifiedSchemaName);

        return null != details ? details.getType() : null;
    }

    /**
     *  Use data source qualified name (e.g., core or external.myschema)
     */
    private @Nullable SchemaDetails getSchemaDetails(String fullyQualifiedSchemaName)
    {
        // Note: Do not reference any DbSchemas (directly or indirectly) in this method. We may be in the midst of loading
        // a DbSchema and don't want to cause CacheLoader re-entrancy. See #26037.
        synchronized(_schemaNameToSchemaDetails)
        {
            if (_schemaNameToSchemaDetails.isEmpty())
            {
                for (Module module : getModules())
                {
                    Set<String> provisioned = Sets.newCaseInsensitiveHashSet(module.getProvisionedSchemaNames());

                    for (String name : module.getSchemaNames())
                    {
                        DbSchemaType type = provisioned.contains(name) ? DbSchemaType.Provisioned : DbSchemaType.Module;
                        _schemaNameToSchemaDetails.put(name, new SchemaDetails(module, type));
                    }

                    // Now register the special "labkey" schema we create in each module data source and associate it with the core module
                    Set<String> moduleDataSourceNames = getModuleDataSourceNames(module);

                    for (String moduleDataSourceName : moduleDataSourceNames)
                    {
                        DbScope scope = DbScope.getDbScope(moduleDataSourceName);

                        if (null != scope && scope.getSqlDialect().canExecuteUpgradeScripts())
                        {
                            String labkeySchemaName = DbSchema.getDisplayName(scope, "labkey");
                            _schemaNameToSchemaDetails.put(labkeySchemaName, new SchemaDetails(getCoreModule(), DbSchemaType.Module));
                        }
                    }
                }
            }

            return _schemaNameToSchemaDetails.get(fullyQualifiedSchemaName);
        }
    }

    public void clearAllSchemaDetails()
    {
        synchronized(_schemaNameToSchemaDetails)
        {
            _schemaNameToSchemaDetails.clear();
        }
    }

    /** @return true if the UrlProvider exists. */
    public <P extends UrlProvider> boolean hasUrlProvider(Class<P> inter)
    {
        return _urlProviderToImpl.get(inter) != null;
    }

    @Nullable
    public <P extends UrlProvider> P getUrlProvider(Class<P> inter)
    {
        Class<? extends UrlProvider> clazz = _urlProviderToImpl.get(inter);

        if (clazz == null)
            return null;

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
        registerResourcePrefix(prefix, module.getName(), module.getSourcePath(), module.getBuildPath());
    }

    public void registerResourcePrefix(String prefix, String name, String sourcePath, String buildPath)
    {
        if (null == prefix)
            return;

        if (!new File(sourcePath).isDirectory() || !new File(buildPath).isDirectory())
            return;

        ResourceFinder finder = new ResourceFinder(name, sourcePath, buildPath);

        synchronized(_resourceFinders)
        {
            Collection<ResourceFinder> col = _resourceFinders.get(prefix);

            if (null == col)
            {
                col = new ArrayList<>();
                _resourceFinders.put(prefix, col);
            }

            col.add(finder);
        }
    }

    public @NotNull Collection<ResourceFinder> getResourceFindersForPath(String path)
    {
        //NOTE: jasper encodes underscores and dashes in JSPs, so decode this here
        path = path.replaceAll("_005f", "_");
        path = path.replaceAll("_002d", "-");

        Collection<ResourceFinder> finders = new LinkedList<>();

        synchronized (_resourceFinders)
        {
            for (Map.Entry<String, Collection<ResourceFinder>> e : _resourceFinders.entrySet())
                if (path.startsWith(e.getKey() + "/"))
                    finders.addAll(e.getValue());
        }

        return finders;
    }

    public Resource getResource(Path path)
    {
        for (Module m : _modules)
        {
            Resource r = m.getModuleResource(path);
            if (r != null && r.exists())
                return r;
        }

        return null;
    }

    public Resource getResource(Module module, Path path)
    {
        return module.getModuleResource(path);
    }

    public Resource getResource(String moduleName, Path path)
    {
        return getModule(moduleName).getModuleResource(path);
    }

    public Module getCurrentModule()
    {
        return ModuleLoader.getInstance().getModuleForController(HttpView.getRootContext().getActionURL().getController());
    }


    public ModuleContext getModuleContext(String name)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Name"), name);
        return new TableSelector(getTableInfoModules(), filter, null).getObject(ModuleContext.class);
    }


    public Collection<ModuleContext> getAllModuleContexts()
    {
        return new TableSelector(getTableInfoModules()).getCollection(ModuleContext.class);
    }


    public Map<String, ModuleContext> getUnknownModuleContexts()
    {
        Map<String, ModuleContext> unknownContexts = new HashMap<>();

        for (ModuleContext moduleContext : getAllModuleContexts())
        {
            String name = moduleContext.getName();
            Module module = getModule(moduleContext.getName());

            if (null == module || !name.equals(module.getName()))
                unknownContexts.put(name, moduleContext);
        }

        return unknownContexts;
    }

    /**
     * Returns the config properties for the specified scope. If no scope is
     * specified then all properties are returned.
     *
     * @param scope
     * @return
     */
    @NotNull
    public Collection<ConfigProperty> getConfigProperties(@Nullable String scope)
    {
        if (!_configPropertyMap.isEmpty())
        {
            if (scope != null)
            {
                if (_configPropertyMap.containsKey(scope))
                    return _configPropertyMap.get(scope);
            }
            else
                return _configPropertyMap.values();
        }
        return Collections.emptyList();
    }


    /**
     * Sets the entire config properties MultiValueMap.
     *
     * @param configProperties
     * @return
     */
    public void setConfigProperties(@Nullable MultiValuedMap<String, ConfigProperty> configProperties)
    {
        _configPropertyMap = configProperties;
    }


    private void loadStartupProps()
    {
        File propsDir = new File(_webappDir.getParent(), "startup");
        if (!propsDir.isDirectory())
            return;

        File newinstall = new File(propsDir,"newinstall");
        if (newinstall.isFile())
        {

            _newInstall = true;
            if (newinstall.canWrite())
                newinstall.delete();
            else
                throw new ConfigurationException("file 'newinstall'  exists, but is not writeable: " + newinstall.getAbsolutePath());
        }

        File[] propFiles = propsDir.listFiles((File dir, String name) -> StringUtils.equalsIgnoreCase(FileUtil.getExtension(name),("properties")));

        if (propFiles != null)
        {
            List<File> sortedPropFiles = Arrays.stream(propFiles)
                    .sorted(Comparator.comparing(File::getName).reversed())
                    .collect(Collectors.toList());

            for (File propFile : sortedPropFiles)
            {
                try (FileInputStream in = new FileInputStream(propFile))
                {
                    Properties props = new Properties();
                    props.load(in);

                    for (Map.Entry<Object, Object> entry : props.entrySet())
                    {
                        if (entry.getKey() instanceof String && entry.getValue() instanceof String)
                        {
                            ConfigProperty config = createConfigProperty(entry.getKey().toString(), entry.getValue().toString());
                            if (_configPropertyMap.containsMapping(config.getScope(), config))
                                _configPropertyMap.removeMapping(config.getScope(), config);
                            _configPropertyMap.put(config.getScope(), config);
                        }
                    }
                }
                catch (Exception e)
                {
                    _log.error("Error parsing startup config properties file '" + propFile.getAbsolutePath() + "'", e);
                }
            }

            // load any system properties with the labkey prop prefix
            for (Map.Entry<Object, Object> entry : System.getProperties().entrySet())
            {
                String name = String.valueOf(entry.getKey());
                String value = String.valueOf(entry.getValue());

                if (name != null && name.startsWith(ConfigProperty.SYS_PROP_PREFIX) && value != null)
                {
                    ConfigProperty config = createConfigProperty(name.substring(ConfigProperty.SYS_PROP_PREFIX.length()), value);
                    if (_configPropertyMap.containsMapping(config.getScope(), config))
                        _configPropertyMap.removeMapping(config.getScope(), config);
                    _configPropertyMap.put(config.getScope(), config);
                }
            }
        }
    }


    /**
     * Parse the config property name and construct a ConfigProperty object. A config property
     * can have an optional dot delimited scope and an optional semicolon delimited modifier, for example:
     * siteSettings.baseServerUrl;bootstrap defines a property named : baseServerUrl in the siteSettings scope and
     * having the bootstrap modifier.
     */
    private ConfigProperty createConfigProperty(String key, String value)
    {
        String name;
        String scope = null;
        String modifier = null;

        // the first dot delimited section is always the scope, the rest is the name
        if (key.contains("."))
        {
            scope = key.substring(0, key.indexOf('.'));
            key = key.substring(key.indexOf('.') + 1, key.length());
        }

        String[] parts = key.split(";");
        if (parts.length == 2)
        {
            name = parts[0];
            modifier = parts[1];
        }
        else
            name = key;

        return new ConfigProperty(name, value, modifier, scope);
    }

    private class SchemaDetails
    {
        private final Module _module;
        private final DbSchemaType _type;

        private SchemaDetails(Module module, DbSchemaType type)
        {
            _module = module;
            _type = type;
        }

        public Module getModule()
        {
            return _module;
        }

        public DbSchemaType getType()
        {
            return _type;
        }
    }
}
