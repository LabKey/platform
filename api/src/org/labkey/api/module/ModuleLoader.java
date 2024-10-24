/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import com.google.common.collect.Maps;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.Constants;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.action.UrlProviderService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveHashSetValuedMap;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.CopyOnWriteHashMap;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaNameCache;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.ModuleUpgrader.Execution;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.MapBasedStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.settings.StartupPropertyHandler;
import org.labkey.api.util.BreakpointThread;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;
import org.labkey.bootstrap.ExplodedModuleService;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.servlet.mvc.Controller;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Drives the process of initializing all the modules at startup time and otherwise managing their life cycle.
 */
public class ModuleLoader implements MemTrackerListener
{
    /** System property name for an extra directory of static content */
    private static final String EXTRA_WEBAPP_DIR = "extrawebappdir";

    private static final ModuleLoader INSTANCE = new ModuleLoader();
    private static final Logger _log = LogHelper.getLogger(ModuleLoader.class, "Initializes and starts up all modules");
    private static final Map<String, Throwable> _moduleFailures = new CopyOnWriteHashMap<>();
    private static final Map<String, Module> _controllerNameToModule = new CaseInsensitiveHashMap<>();
    private static final Map<String, SchemaDetails> _schemaNameToSchemaDetails = new CaseInsensitiveHashMap<>();
    private static final CopyOnWriteHashMap<String, Collection<ResourceFinder>> _resourceFinders = new CopyOnWriteHashMap<>();
    private static final CoreSchema _core = CoreSchema.getInstance();
    private static final Object UPGRADE_LOCK = new Object();
    private static final Object STARTUP_LOCK = new Object();

    public static final String MODULE_NAME_REGEX = "\\w+";
    public static final String PRODUCTION_BUILD_TYPE = "Production";
    public static final Object SCRIPT_RUNNING_LOCK = new Object();

    private static Throwable _startupFailure = null;
    private static boolean _newInstall = false;
    private static TomcatVersion _tomcatVersion = null;
    private static JavaVersion _javaVersion = null;

    private static final String BANNER = """

             __                                  \s
             ||  |  _ |_ |/ _     (¯ _  _   _  _
            (__) |_(_||_)|\\(/_\\/  _)(/_| \\/(/_| \s
                              /                 \s""".indent(2);

    private ServletContext _servletContext = null;
    private FileLike _webappDir;
    private FileLike _extraWebappDir;
    private FileLike _startupPropertiesDir;
    private UpgradeState _upgradeState;

    private final SqlScriptRunner _upgradeScriptRunner = new SqlScriptRunner();

    // NOTE: the following startup fields are synchronized under STARTUP_LOCK
    private StartupState _startupState = StartupState.StartupIncomplete;
    private String _startingUpMessage = null;

    private enum UpgradeState {UpgradeRequired, UpgradeInProgress, UpgradeComplete}

    private enum StartupState {StartupIncomplete, StartupInProgress, StartupComplete}

    public enum ModuleState
    {
        Disabled,
        Loading,
        InstallRequired,
        Installing,
        InstallComplete,
        ReadyToStart,
        Starting,
        Started
    }

    /** Stash these warnings as a member variable so they can be registered after the WarningService has been initialized */
    private final List<HtmlString> _duplicateModuleErrors = new ArrayList<>();

    // these four collections are protected by _modulesLock
    // all names start with _modules to make it easier to search for usages
    private final Object _modulesLock = new Object();
    private final Map<String, ModuleContext> _moduleContextMap = new HashMap<>();
    /**
     * We use an immutable map for _moduleMap and _moduleClassMap so their access doesn't need to be synchronized,
     * reducing contention for these frequently accessed collections (dozens or hundreds of times per request)
     */
    private volatile Map<String, Module> _moduleMap = Collections.emptyMap();
    private volatile Map<Class<? extends Module>, Module> _moduleClassMap = Collections.emptyMap();
    /** Use a CopyOnWriteArrayList since the underlying collection is frequently accessed and very infrequently mutated */
    private final List<Module> _modules = new CopyOnWriteArrayList<>();
    /**
     * Immutable wrapper over _modules so that we don't need to create a new wrapper every time we return it, which
     * can be thousands of times per request
     */
    private final List<Module> _modulesImmutable = Collections.unmodifiableList(_modules);

    // Allow multiple StartupPropertyHandlers with the same scope as long as the StartupProperty impl class is different.
    private final Set<StartupPropertyHandler<? extends StartupProperty>> _startupPropertyHandlers = new ConcurrentSkipListSet<>(Comparator.comparing((StartupPropertyHandler<?> sph)->sph.getScope(), String.CASE_INSENSITIVE_ORDER).thenComparing(StartupPropertyHandler::getStartupPropertyClassName));
    private final MultiValuedMap<String, StartupPropertyEntry> _startupPropertyMap = new CaseInsensitiveHashSetValuedMap<>();

    private ModuleLoader()
    {
        MemTracker.getInstance().register(this);
    }

    public static ModuleLoader getInstance()
    {
        return INSTANCE;
    }

    public void init(ServletContext servletCtx)
    {
        _servletContext = servletCtx;

        // terminateAfterStartup flag allows "headless" install/upgrade where Tomcat terminates after all modules are upgraded,
        // started, and initialized. This flag implies synchronousStartup=true.
        boolean terminateAfterStartup = Boolean.valueOf(System.getProperty("terminateAfterStartup"));
        // synchronousStartup=true ensures that all modules are upgraded, started, and initialized before Tomcat startup is
        // complete. No webapp requests will be processed until startup is complete, unlike the usual asynchronous upgrade mode.
        boolean synchronousStartup = Boolean.valueOf(System.getProperty("synchronousStartup"));
        Execution execution = terminateAfterStartup || synchronousStartup ? Execution.Synchronous : Execution.Asynchronous;

        try
        {
            doInit(execution);
        }
        catch (Throwable t)
        {
            if (_modules.isEmpty())
            {
                _log.fatal("Failure occurred during ModuleLoader init.", t);
                System.err.println("The server cannot start. Check the server error log.");
                System.exit(1);
            }
            setStartupFailure(t);
            _log.error("Failure occurred during ModuleLoader init.", t);
        }
        finally
        {
            if (terminateAfterStartup)
                System.exit(0);
        }
    }

    @Nullable
    public static ServletContext getServletContext()
    {
        return getInstance()._servletContext;
    }

    /** Do basic module loading, shared between the web server and remote pipeline deployments */
    public List<Module> doInit(List<File> explodedModuleDirs)
    {
        List<Map.Entry<File,File>> moduleDirs = explodedModuleDirs.stream()
            .map(dir -> new AbstractMap.SimpleEntry<File,File>(dir,null))
            .collect(Collectors.toList());
        return doInitWithSourceModule(moduleDirs);
    }

    public List<Module> doInitWithSourceModule(List<Map.Entry<File,File>> explodedModuleDirs)
    {
        _log.debug("ModuleLoader init");

        setJavaVersion();

        // make sure ConvertHelper is initialized
        ConvertHelper.getPropertyEditorRegistrar();

        // Populate early so module include/exclude properties are available for loadModules()... and not reloaded when
        // creating and loading modules using the module editor.
        ModuleLoaderStartupProperties.populate();
        // Load module instances using Spring
        List<Module> moduleList = loadModules(explodedModuleDirs);

        //sort the modules by dependencies
        synchronized (_modulesLock)
        {
            ModuleDependencySorter sorter = new ModuleDependencySorter();
            _modules.addAll(sorter.sortModulesByDependencies(moduleList));

            Map<String, Module> newModuleMap = new CaseInsensitiveHashMap<>();
            Map<Class<? extends Module>, Module> newModuleClassMap = new HashMap<>();

            for (Module module : _modules)
            {
                newModuleMap.put(module.getName(), module);
                newModuleClassMap.put(module.getClass(), module);
            }

            _moduleMap = Collections.unmodifiableMap(newModuleMap);
            _moduleClassMap = Collections.unmodifiableMap(newModuleClassMap);
        }

        return getModules();
    }

    // Proxy to teleport ExplodedModuleService from outer ClassLoader to inner
    private static class _Proxy implements InvocationHandler
    {
        private final Object _delegate;
        private final Map<String,Method> _methods = new HashMap<>();

        public static ExplodedModuleService newInstance(Object obj)
        {
            if (!"org.labkey.bootstrap.LabKeyBootstrapClassLoader".equals(obj.getClass().getName()) &&
                    !"org.labkey.bootstrap.LabkeyServerBootstrapClassLoader".equals(obj.getClass().getName()) &&
                    !"org.labkey.embedded.LabKeySpringBootClassLoader".equals(obj.getClass().getName()))
                return null;
            return (ExplodedModuleService)java.lang.reflect.Proxy.newProxyInstance(ExplodedModuleService.class.getClassLoader(),
                    new Class[] {ExplodedModuleService.class},
                    new _Proxy(obj));
        }

        private _Proxy(Object obj)
        {
            Set<String> methodNames = Set.of("getExplodedModuleDirectories", "getExplodedModules", "updateModule", "getExternalModulesDirectory", "getDeletedModulesDirectory", "newModule");
            _delegate = obj;
            Arrays.stream(obj.getClass().getMethods()).forEach(method -> {
                if (methodNames.contains(method.getName()))
                    _methods.put(method.getName(), method);
            });
            methodNames.forEach(name -> { if (null == _methods.get(name)) throw new ConfigurationException("LabKeyBootstrapClassLoader seems to be mismatched to the labkey server deployment.  Could not find method: " + name); });
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable
        {
            try
            {
                Method delegate_method = _methods.get(m.getName());
                if (null == delegate_method)
                    throw new IllegalArgumentException(m.getName());
                return delegate_method.invoke(_delegate, args);
            }
            catch (InvocationTargetException e)
            {
                throw e.getTargetException();
            }
            catch (Exception e)
            {
                throw new RuntimeException("unexpected invocation exception: " + e.getMessage());
            }
        }
    }

    /* this is called if new archive is exploded (for existing or new modules) */
    public void updateModuleDirectory(File dir, File archive)
    {
        Module moduleCreated;
        boolean fireModuleChanged = false;
        // TODO move call to ContextListener.fireModuleChangeEvent() into this method
        synchronized (_modulesLock)
        {
            List<Module> moduleList = loadModules(List.of(new AbstractMap.SimpleEntry<>(dir,archive)));
            if (moduleList.isEmpty())
            {
                throw new IllegalStateException("Not a valid module: " + archive.getName());
            }
            moduleCreated = moduleList.get(0);
            if (null != archive)
                moduleCreated.setZippedPath(archive);

            /* VERY IMPORTANT: we expect all these additions to file-based, non-schema modules! */
            if (SimpleModule.class != moduleCreated.getClass())
                throw new IllegalStateException("Can only add file-based module after startup");
            if (!moduleCreated.getSchemaNames().isEmpty())
                throw new IllegalStateException("Can not add modules with schema after startup");

            ModuleContext context = new ModuleContext(moduleCreated);
            saveModuleContext(context);

            Module moduleExisting = null;
            for (Module module : getModules())
                if (dir.equals(module.getExplodedPath()))
                    moduleExisting = module;

            // This should have been verified way before we get here, but just to be safe
            if (null != moduleExisting && !equalsIgnoreCase(moduleCreated.getName(), moduleExisting.getName()))
                throw new IllegalStateException("Module name should not have changed.  Found '" + moduleCreated.getName() + "' and '" + moduleExisting.getName() + "'");

            _moduleContextMap.put(context.getName(), context);

            Map<String, Module> newModuleMap = new CaseInsensitiveHashMap<>(_moduleMap);
            newModuleMap.put(moduleCreated.getName(), moduleCreated);
            _moduleMap = Collections.unmodifiableMap(newModuleMap);

            if (null != moduleExisting)
                _modules.remove(moduleExisting);
            _modules.add(moduleCreated);

            Map<Class<? extends Module>, Module> newModuleClassMap = new HashMap<>(_moduleClassMap);
            newModuleClassMap.put(moduleCreated.getClass(), moduleCreated);
            _moduleClassMap = Collections.unmodifiableMap(newModuleClassMap);

            synchronized (_controllerNameToModule)
            {
                _controllerNameToModule.put(moduleCreated.getName(), moduleCreated);
            }

            if (null != moduleExisting)
            {
                ContextListener.fireModuleChangeEvent(moduleExisting);
                ((DefaultModule)moduleExisting).unregister();
            }

            try
            {
                // avoid error in startup, DefaultModule does not expect to see module with same name initialized again
                ((DefaultModule)moduleCreated).unregister();
                _moduleFailures.remove(moduleCreated.getName());
                pruneModules(moduleList);
                initializeModules(moduleList);

                Throwable t = _moduleFailures.get(moduleCreated.getName());
                if (null != t)
                    throw t;

                if (_moduleMap.containsKey(moduleCreated.getName()))
                {
                    // Module startup
                    ModuleContext ctx = getModuleContext(moduleCreated);
                    ctx.setModuleState(ModuleState.Starting);
                    moduleCreated.startup(ctx);
                    ctx.setModuleState(ModuleState.Started);
                    fireModuleChanged = true;
                }
            }
            catch (Throwable x)
            {
                _log.error("Failure starting module: " + moduleCreated.getName(), x);
                throw UnexpectedException.wrap(x);
            }
        }

        // Issue 48225 - fire event outside the synchronized block to avoid deadlocks
        if (fireModuleChanged)
        {
            ContextListener.fireModuleChangeEvent(moduleCreated);
        }
    }

    /** Full web-server initialization */
    private void doInit(Execution execution) throws ServletException
    {
        _log.info(BANNER);

        AppProps.getInstance().setContextPath(_servletContext.getContextPath());

        setTomcatVersion();

        var webapp = FileUtil.getAbsoluteCaseSensitiveFile(new File(_servletContext.getRealPath("")));
        _webappDir = new FileSystemLike.Builder(webapp).readonly().noMemCheck().root();

        String extraWebappPath = System.getProperty(EXTRA_WEBAPP_DIR);
        File extraWebappDir;
        if (extraWebappPath == null)
        {
            extraWebappDir = FileUtil.appendName(webapp.getParentFile(), "extraWebapp");
        }
        else
        {
            extraWebappDir = new File(extraWebappPath);
        }
        if (extraWebappDir.isDirectory())
            _extraWebappDir = new FileSystemLike.Builder(extraWebappDir).readonly().noMemCheck().root();

        var startup = FileUtil.appendName(webapp.getParentFile(), "startup");
        if (startup.isDirectory())
            _startupPropertiesDir = new FileSystemLike.Builder(startup).readonly().noMemCheck().root();

        // load startup configuration information from properties, side-effect may set _newinstall=true
        // Wiki: https://www.labkey.org/Documentation/wiki-page.view?name=bootstrapProperties#using
        loadStartupProps();

        List<Map.Entry<File,File>> explodedModuleDirs = new ArrayList<>();

        // find modules exploded by LabKeyBootStrapClassLoader
        ClassLoader webappClassLoader = getClass().getClassLoader();
        ExplodedModuleService service = _Proxy.newInstance(webappClassLoader);
        if (null != service)
        {
            explodedModuleDirs.addAll(service.getExplodedModules());
            ServiceRegistry.get().registerService(ExplodedModuleService.class, service);
        }

        // support WAR style deployment (w/o LabKeyBootstrapClassLoader) if modules are found at webapp/WEB-INF/modules
        File webinfModulesDir = FileUtil.appendPath(webapp, Path.parse("WEB-INF/modules"));
        if (!webinfModulesDir.isDirectory() && null == service)
            throw new ConfigurationException("Could not find required class LabKeyBootstrapClassLoader. You probably need to copy labkeyBootstrap.jar into $CATALINA_HOME/lib and/or edit your " + AppProps.getInstance().getWebappConfigurationFilename() + " to include <Loader loaderClass=\"org.labkey.bootstrap.LabKeyBootstrapClassLoader\" />");
        File[] webInfModules = webinfModulesDir.listFiles(File::isDirectory);
        if (null != webInfModules)
        {
            Arrays.stream(webInfModules)
                .map(m -> new AbstractMap.SimpleEntry<File,File>(m,null))
                .forEach(explodedModuleDirs::add);
        }

        doInitWithSourceModule(explodedModuleDirs);

        // set the project source root before calling .initialize() on modules
        var modules = getModules();
        Module coreModule = modules.isEmpty() ? null : modules.get(0);
        if (coreModule == null || !DefaultModule.CORE_MODULE_NAME.equals(coreModule.getName()))
            throw new IllegalStateException("Core module was not first or could not find the Core module. Ensure that Tomcat user can create directories under the <LABKEY_HOME>/modules directory.");
        setProjectRoot(coreModule);

        for (Module module : modules)
        {
            module.registerFilters(_servletContext);
        }
        for (Module module : modules)
        {
            module.registerServlets(_servletContext);
        }
        for (Module module : modules)
        {
            module.registerFinalServlets(_servletContext);
        }

        // Do this after we've checked to see if we can find the core module. See issue 22797.
        verifyProductionModeMatchesBuild();

        // Initialize data sources before initializing modules; modules will fail to initialize if the appropriate data sources aren't available
        DbScope.initializeDataSources();

        // Start up a thread that lets us hit a breakpoint in the debugger, even if all the real working threads are hung.
        // This lets us invoke methods in the debugger, gain easier access to statics, etc.
        new BreakpointThread().start();

        // Start listening for requests for thread and heap dumps
        File coreModuleDir = coreModule.getExplodedPath();
        File modulesDir = coreModuleDir.getParentFile();
        new DebugInfoDumper(modulesDir);

        final File lockFile = createLockFile(modulesDir);

        // Prune modules before upgrading core module, see Issue 42150
        synchronized (_modulesLock)
        {
            pruneModules(_modules);
        }

        if (getTableInfoModules().getTableType() == DatabaseTableType.NOT_IN_DB)
        {
            _newInstall = true;
        }
        else
        {
            // Refuse to start up if any LabKey-managed module has a schema version that's too old. Issue 46922.

            // Modules that are designated as "managed" and reside in LabKey-managed repositories
            var labkeyModules = _modules.stream()
                .filter(Module::shouldManageVersion)
                .filter(this::isFromLabKeyRepository) // Do the check only for modules in LabKey repositories, Issue 47369
                .toList();

            // Likely empty if running in dev mode... no need to log or do other work
            if (!labkeyModules.isEmpty())
            {
                _log.info("Checking " + StringUtilsLabKey.pluralize(labkeyModules.size(), "LabKey-managed module") + " to ensure " + (labkeyModules.size() > 1? "they're" : "it's") + " recent enough to upgrade");

                // Module contexts with non-null schema versions
                Map<String, ModuleContext> moduleContextMap = getAllModuleContexts().stream()
                    .filter(ctx -> ctx.getSchemaVersion() != null)
                    .collect(Collectors.toMap(ModuleContext::getName, ctx->ctx));

                // Names of LabKey-managed modules with schemas where the installed version is less than "earliest upgrade version"
                var tooOld = labkeyModules.stream()
                    .map(m -> moduleContextMap.get(m.getName()))
                    .filter(Objects::nonNull)
                    .filter(ctx -> ctx.getInstalledVersion() < Constants.getEarliestUpgradeVersion())
                    .map(ModuleContext::getName)
                    .toList();

                if (!tooOld.isEmpty())
                {
                    String countPhrase = 1 == tooOld.size() ? " of this module is" : "s of these modules are";
                    throw new ConfigurationException("Can't upgrade this deployment. The installed schema version" + countPhrase + " too old: " + tooOld + " This version of LabKey Server supports upgrading modules from schema version " + Constants.getEarliestUpgradeVersion() + " and greater.");
                }
            }
        }

        boolean coreRequiredUpgrade = upgradeCoreModule();

        // Issue 40422 - log server and session GUIDs during startup. Do it after the core module has
        // been bootstrapped/upgraded to ensure that AppProps is ready
        _log.info("Starting LabKey Server " + AppProps.getInstance().getReleaseVersion());
        _log.info("Server installation GUID: " + AppProps.getInstance().getServerGUID() + ", server session GUID: " + AppProps.getInstance().getServerSessionGUID());
        _log.info("Deploying to context path " + AppProps.getInstance().getContextPath());

        synchronized (_modulesLock)
        {
            checkForRenamedModules();
            // use _modules here because this List<> needs to be modifiable
            initializeModules(_modules);
        }

        if (!_duplicateModuleErrors.isEmpty())
        {
            WarningService.get().register(new WarningProvider()
            {
                @Override
                public void addStaticWarnings(@NotNull Warnings warnings, boolean showAllWarnings)
                {
                    for (HtmlString error : _duplicateModuleErrors)
                    {
                        warnings.add(error);
                    }
                }
            });
        }

        // Clear the map to remove schemas associated with modules that failed to load
        clearAllSchemaDetails();

        // Now that the core module is upgraded, upgrade the "labkey" schema in all module-required external data sources
        // to match the core module version. Each external data source records their upgrade scripts and versions their
        // module schemas via the tables in its own "labkey" schema.
        upgradeLabKeySchemaInExternalDataSources();

        ModuleContext coreCtx;
        List<String> modulesRequiringUpgrade = new LinkedList<>();
        List<String> additionalSchemasRequiringUpgrade = new LinkedList<>();
        List<String> downgradedModules = new LinkedList<>();

        synchronized (_modulesLock)
        {
            for (ModuleContext context : getAllModuleContexts())
                _moduleContextMap.putIfAbsent(context.getName(), context); // Don't replace the "Core" context otherwise we'll overwrite its _newInstall and _originalVersion properties

            warnAboutDuplicateSchemas(_moduleContextMap.values());

            // Refresh our list of modules as some may have been filtered out based on dependencies or DB platform
            modules = getModules();
            for (Module module : modules)
            {
                ModuleContext context = getModuleContext(module);

                if (null == context)
                {
                    // Make sure we have a context for all modules, even ones we haven't seen before
                    context = new ModuleContext(module);
                    _moduleContextMap.put(module.getName(), context);
                }

                if (context.getModuleState() != ModuleState.ReadyToStart) // If upgraded, core module is ready-to-start
                {
                    if (context.needsUpgrade(module.getSchemaVersion()))
                    {
                        context.setModuleState(ModuleState.InstallRequired);
                        modulesRequiringUpgrade.add(context.getName());
                    }
                    else
                    {
                        context.setModuleState(ModuleState.ReadyToStart);

                        // Module doesn't require an upgrade, but we still need to check if schemas in this module require upgrade.
                        // The scenario is a schema in an external data source that needs to be installed or upgraded.
                        List<String> schemasInThisModule = additionalSchemasRequiringUpgrade(module);
                        additionalSchemasRequiringUpgrade.addAll(schemasInThisModule);

                        // Also check for module "downgrades" so we can warn admins, #30773
                        if (context.isDowngrade(module.getSchemaVersion()))
                            downgradedModules.add(context.getName());
                    }
                }
            }

            coreCtx = _moduleContextMap.get(DefaultModule.CORE_MODULE_NAME);
        }

        // Core module should be upgraded and ready-to-run
        assert (ModuleState.ReadyToStart == coreCtx.getModuleState());

        if (WarningService.get().showAllWarnings() || !downgradedModules.isEmpty())
        {
            if (WarningService.get().showAllWarnings() && downgradedModules.isEmpty())
            {
                downgradedModules.add("core");
                downgradedModules.add("flow");
            }

            int count = downgradedModules.size();
            String message = "This server is running with " + StringUtilsLabKey.pluralize(count, "downgraded module") + ". The server will not operate properly and could corrupt your data. You should immediately stop the server and contact LabKey for assistance. Modules affected: " + downgradedModules;
            _log.error(message);
            WarningService.get().register(new WarningProvider()
            {
                @Override
                public void addStaticWarnings(@NotNull Warnings warnings, boolean showAllWarnings)
                {
                    warnings.add(HtmlString.of(message));
                }
            });
        }

        if (!modulesRequiringUpgrade.isEmpty())
            _log.info("Modules requiring upgrade: " + modulesRequiringUpgrade);

        if (!additionalSchemasRequiringUpgrade.isEmpty())
            _log.info((modulesRequiringUpgrade.isEmpty() ? "Schemas" : "Additional schemas") + " requiring upgrade: " + additionalSchemasRequiringUpgrade);

        if (!modulesRequiringUpgrade.isEmpty() || !additionalSchemasRequiringUpgrade.isEmpty())
            setUpgradeState(UpgradeState.UpgradeRequired);

        startNonCoreUpgradeAndStartup(execution, lockFile);

        _log.info("LabKey Server startup is complete; " + execution.getLogMessage());
    }

    // Check for multiple modules claiming the same schema. Inspired by Issue 47547.
    private void warnAboutDuplicateSchemas(Collection<ModuleContext> values)
    {
        Map<String, String> schemaToModule = CaseInsensitiveHashMap.of();
        for (ModuleContext ctx : values)
        {
            String schemas = ctx.getSchemas();
            if (null != schemas)
            {
                for (String schema : schemas.split(","))
                {
                    String previousModule = schemaToModule.get(schema);
                    if (previousModule != null)
                        _log.error("Schema " + schema + " is claimed by more than one module: " + previousModule + " and " + ctx.getName());
                    else
                        schemaToModule.put(schema, ctx.getName());
                }
            }
        }
    }

    /**
     * Does this module live in a repository that's managed by LabKey Corporation?
     * @param module a Module
     * @return true if the module's VCS URL is non-null and starts with one of the GitHub organizations that LabKey manages
     */
    private boolean isFromLabKeyRepository(Module module)
    {
        return StringUtils.startsWithAny(module.getVcsUrl(), "https://github.com/LabKey/", "https://github.com/FDA-MyStudies/");
    }

    private static final Map<String, String> _moduleRenames = Map.of("MobileAppStudy", "Response" /* Renamed in 21.3 */);

    private void checkForRenamedModules()
    {
        getModules().stream()
            .filter(module -> _moduleRenames.containsKey(module.getName())).findAny()
            .ifPresent(module -> {
                String msg = String.format("Invalid LabKey deployment. The '%s' module has been renamed, %s", module.getName(),
                    AppProps.getInstance().getProjectRoot() == null
                        ? "please deploy an updated distribution and restart LabKey." // Likely production environment
                        : "please update your enlistment." // Likely dev environment
                );

                throw new IllegalStateException(msg);
            });
    }

    /** Create a file that indicates the server is in the midst of the upgrade process. Refuse to start up
     * if a previous startup left the lock file in place. */
    private File createLockFile(File modulesDir) throws ConfigurationException
    {
        File result = new File(modulesDir.getParentFile(), "labkeyUpgradeLockFile");
        if (result.exists())
        {
            if (AppProps.getInstance().isDevMode())
            {
                _log.warn("Lock file " + FileUtil.getAbsoluteCaseSensitiveFile(result) + " already exists - a previous upgrade attempt may have left the server in an indeterminate state.");
                _log.warn("Bravely continuing because this server is running in Dev mode.");
            }
            else
            {
                throw new ConfigurationException("Lock file " + FileUtil.getAbsoluteCaseSensitiveFile(result) + " already exists - a previous upgrade attempt may have left the server in an indeterminate state. Proceed with extreme caution as the database may not be properly upgraded. To continue, delete the file and restart Tomcat.");

            }
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(result), StringUtilsLabKey.DEFAULT_CHARSET)))
        {
            writer.write("LabKey instance beginning initialization at " + DateUtil.formatIsoDateShortTime(new Date()));

        }
        catch (IOException e)
        {
            throw new ConfigurationException("Unable to write lock file at " + FileUtil.getAbsoluteCaseSensitiveFile(result) + " - ensure the user executing Tomcat has permission to create files in parent directory.", e);
        }
        return result;
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
                .forEach(module -> addModuleFailure(module.getName(), new IllegalStateException("Module " + module + " was not compiled in production mode")));
        }
    }

    private boolean isDevelopmentBuild(Module module)
    {
        return !PRODUCTION_BUILD_TYPE.equalsIgnoreCase(module.getBuildType());
    }

    /** Enumerates all the modules, removing the ones that don't support the core database */
    private void pruneModules(List<Module> modules)
    {
        Module core = getCoreModule();

        SupportedDatabase coreType = SupportedDatabase.get(CoreSchema.getInstance().getSqlDialect());
        for (Module module : modules)
        {
            if (module == core)
                continue;
            if (!module.getSupportedDatabasesSet().contains(coreType))
            {
                var e = new DatabaseNotSupportedException("This module does not support " + CoreSchema.getInstance().getSqlDialect().getProductName());
                // In production mode, treat these exceptions as a module initialization error
                // In dev mode, make them warnings so devs can easily switch databases
                removeModule(modules, module, !AppProps.getInstance().isDevMode(), e);
            }
        }
    }

    /** Enumerates all remaining modules, initializing them and removing any that fail to initialize */
    private void initializeModules(List<Module> modules)
    {
        Module core = getCoreModule();

        /*
         * NOTE: Module.initialize() really should not ask for resources from _other_ modules,
         * as they may have not initialized themselves yet.  However, we did not enforce that
         * so this cross-module behavior may have crept in.
         *
         * To help mitigate this a little, we remove modules that do not support this DB type
         * before calling initialize().
         *
         * NOTE: see FolderTypeManager.get().registerFolderType() for an example of enforcing this
         */

        //initialize each module in turn
        ListIterator<Module> iterator = modules.listIterator();
        while (iterator.hasNext())
        {
            Module module = iterator.next();
            if (module == core)
                continue;

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
                    removeModule(modules, module, false, e);
                }
            }
            catch(Throwable t)
            {
                removeModule(modules, module, true, t);
            }
        }

        // All modules are initialized (controllers are registered), so initialize the controller-related maps
        ViewServlet.initialize();
        initControllerToModule();
    }

    // Check a module's dependencies and throw on the first one that's not present (i.e., it was removed because its initialize() failed)
    private void verifyDependencies(Module module)
    {
        synchronized (_modulesLock)
        {
            for (String dependency : module.getModuleDependenciesAsSet())
                if (!_moduleMap.containsKey(dependency))
                    throw new ModuleDependencyException(dependency);
        }
    }

    private static class ModuleDependencyException extends ConfigurationException
    {
        public ModuleDependencyException(String dependencyName)
        {
            super("This module depends on the \"" + dependencyName + "\" module, which failed to initialize");
        }
    }

    private void removeModule(List<Module> modules, Module current, boolean treatAsError, Throwable t)
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

        synchronized (_modulesLock)
        {
            modules.remove(current);
            _moduleClassMap = removeMapValue(current, new HashMap<>(_moduleClassMap));
            _moduleMap = removeMapValue(current, new CaseInsensitiveHashMap<>(_moduleMap));
            removeMapValue(current, _controllerNameToModule);
        }
    }

    /** @return an immutable wrapper over the modified map */
    private <K> Map<K, Module> removeMapValue(Module module, Map<K, Module> map)
    {
        map.entrySet().removeIf(entry -> entry.getValue() == module);
        return Collections.unmodifiableMap(map);
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
            possibleRoots.add(core.getSourcePath() + "/../../../.."); // assuming core source path is trunk/server/platform/core and we want labkey home path

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

    private List<Module> loadModules(List<Map.Entry<File,File>> explodedModuleDirs)
    {
        ApplicationContext parentContext = ServiceRegistry.get().getApplicationContext();

        CaseInsensitiveHashMap<File> moduleNameToFile = new CaseInsensitiveHashMap<>();
        CaseInsensitiveTreeMap<Module> moduleNameToModule = new CaseInsensitiveTreeMap<>();
        Pattern moduleNamePattern = Pattern.compile(MODULE_NAME_REGEX);
        for (var moduleSource : explodedModuleDirs)
        {
            File moduleDir = moduleSource.getKey();
            File moduleFile = moduleSource.getValue();

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
                        String error = "Module with name '" + module.getName() + "' has already been loaded from "
                                + moduleNameToFile.get(module.getName()).getAbsolutePath() + ". Skipping additional copy of the module in " + moduleDir + ". You should delete the extra copy and restart the server.";
                        _duplicateModuleErrors.add(HtmlString.of(error));
                        _log.error(error);
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
                        if (null != moduleFile && moduleFile.getName().endsWith(".module") && moduleFile.isFile())
                            module.setZippedPath(moduleFile);

                        moduleNameToFile.put(module.getName(), moduleDir);
                        moduleNameToModule.put(module.getName(), module);
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

        // filter by startup properties if they were specified
        LinkedList<String> includeList = ModuleLoaderStartupProperties.include.getList();
        Set<String> excludeSet = Sets.newCaseInsensitiveHashSet(ModuleLoaderStartupProperties.exclude.getList());

        List<String> missingModules = new ArrayList<>();
        CaseInsensitiveTreeMap<Module> includedModules = moduleNameToModule;
        if (!includeList.isEmpty())
        {
            includeList.addAll(Arrays.asList("Core", "API"));
            includedModules = new CaseInsensitiveTreeMap<>();
            while (!includeList.isEmpty())
            {
                String moduleName = includeList.removeFirst();
                if (!excludeSet.contains(moduleName)) // Don't look up excluded modules or include their dependencies
                {
                    Module m = moduleNameToModule.get(moduleName);
                    if (m == null)
                    {
                        missingModules.add(moduleName);
                    }
                    else
                    {
                        // add module to includedModules, add dependencies to includeList (of course it's too soon to call getResolvedModuleDependencies)
                        if (null == includedModules.put(m.getName(), m))
                            includeList.addAll(m.getModuleDependenciesAsSet());
                    }
                }
            }
        }

        if (!missingModules.isEmpty())
        {
            _log.info("Problem in startup property 'ModuleLoader.include'. Unable to find requested module(s): " +
                    String.join(", ", missingModules));
        }

        for (String e : excludeSet)
        {
            Module module = includedModules.remove(e);
            if (module != null)
                _log.info("Excluding module {} since it was specified in the exclude startup property", module.getName());
        }

        return new ArrayList<>(includedModules.values());
    }

    /** Load module metadata from a .properties file */
    private Module loadModuleFromProperties(ApplicationContext parentContext, File moduleDir) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException
    {
        //check for simple .properties file
        File modulePropsFile = new File(moduleDir, "config/module.properties");
        Map<String, String> props = Collections.emptyMap();
        if (modulePropsFile.exists())
        {
            try (FileInputStream in = new FileInputStream(modulePropsFile))
            {
                Properties p = new Properties();
                p.load(in);
                props = Maps.fromProperties(p);
            }
            catch (IOException e)
            {
                _log.error("Error reading module properties file '" + modulePropsFile.getAbsolutePath() + "'", e);
            }
        }

        //assume that module name is directory name
        String moduleName = moduleDir.getName();
        if (props.containsKey("name"))
            moduleName = props.get("name");

        if (moduleName == null || moduleName.isEmpty())
            throw new ConfigurationException("Simple module must specify a name in config/module.xml or config/module.properties: " + moduleDir.getParent());

        // Create the module instance
        DefaultModule simpleModule;
        if (props.containsKey("ModuleClass"))
        {
            String moduleClassName = props.get("ModuleClass");
            Class<DefaultModule> moduleClass = (Class<DefaultModule>)Class.forName(moduleClassName);
            simpleModule = moduleClass.newInstance();
        }
        else
        {
            simpleModule = new SimpleModule();
        }

        simpleModule.setName(moduleName);

        //get SourcePath property if there is one
        String srcPath = props.get("SourcePath");

        if (StringUtils.isNotBlank(srcPath))
            simpleModule.setSourcePath(srcPath);
        BeanUtils.populate(simpleModule, props);
        simpleModule.setApplicationContext(parentContext);

        return simpleModule;
    }


    /** Read module metadata out of XML file */
    private Module loadModuleFromXML(ApplicationContext parentContext, File moduleXml)
    {
        ApplicationContext applicationContext;
        if (null != getServletContext())
        {
            XmlWebApplicationContext beanFactory = new XmlWebApplicationContext();
            beanFactory.setConfigLocations(moduleXml.toURI().toString());
            beanFactory.setParent(parentContext);
            beanFactory.setServletContext(new SpringModule.ModuleServletContextWrapper(getServletContext()));
            beanFactory.refresh();
            applicationContext = beanFactory;
        }
        else
        {
            FileSystemXmlApplicationContext beanFactory = new FileSystemXmlApplicationContext();
            beanFactory.setConfigLocations(moduleXml.toURI().toString());
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
        if (_webappDir != null && !_webappDir.toNioPathForRead().equals(webappDir.toPath()))
        {
            throw new IllegalStateException("WebappDir is already set to " + _webappDir + ", cannot reset it to " + webappDir);
        }
        _webappDir = new FileSystemLike.Builder(webappDir).readonly().root();
        assert MemTracker.get().remove(_webappDir);
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


    public FileLike getWebappDir()
    {
        return _webappDir;
    }

    public FileLike getExtraWebappDir()
    {
        return _extraWebappDir;
    }

    /**
     * Sets the current Java version, if it's supported. Otherwise, ConfigurationException is thrown and server fails to start.
     *
     * Warnings for deprecated Java versions are handled in CoreWarningProvider.
     *
     * @throws ConfigurationException if Java version is not supported
     */
    private void setJavaVersion()
    {
        _javaVersion = JavaVersion.get();

        if (!_javaVersion.isTested())
            _log.warn("LabKey Server has not been tested against Java runtime version " + JavaVersion.getJavaVersionDescription() + ".");
    }

    public JavaVersion getJavaVersion()
    {
        return _javaVersion;
    }

    /**
     * Sets the running Tomcat version, if servlet container is recognized and supported. Otherwise, ConfigurationException is thrown and server fails to start.
     *
     * Warnings for deprecated Tomcat versions are handled in CoreWarningProvider.
     *
     * @throws ConfigurationException if Tomcat version is not recognized or supported
     */
    private void setTomcatVersion()
    {
        _tomcatVersion = TomcatVersion.get();
    }

    public TomcatVersion getTomcatVersion()
    {
        return _tomcatVersion;
    }

    /**
     * Initialize and update the Core module first. We want to change the core tables before we display pages, request
     * login, check permissions, or initialize any of the other modules.
     * @return true if core module required upgrading, otherwise false
     */
    private boolean upgradeCoreModule() throws ServletException
    {
        Module coreModule = getCoreModule();
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
            coreContext = getModuleContextFromDatabase("Core");

        // Does the core module need to be upgraded?
        if (!coreContext.needsUpgrade(coreModule.getSchemaVersion()))
            return false;

        if (coreContext.isNewInstall())
        {
            _log.debug("Initializing core module to " + coreModule.getFormattedSchemaVersion());
        }
        else
        {
            _log.debug("Upgrading core module from " + ModuleContext.formatVersion(coreContext.getInstalledVersion()) + " to " + coreModule.getFormattedSchemaVersion());
        }

        synchronized (_modulesLock)
        {
            _moduleContextMap.put(coreModule.getName(), coreContext);
        }

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
        Module coreModule = getCoreModule();
        FileSqlScriptProvider provider = new FileSqlScriptProvider(coreModule);
        double to = coreModule.getSchemaVersion();

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
                    getUpgradeScriptRunner().runScripts(coreModule, scripts);
                }

                manager.updateSchemaVersion(to);
            }
            catch (SqlScriptException e)
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

        if (Boolean.valueOf(System.getProperty("terminateOnStartupFailure")))
        {
            // Issue 40038: Ride-or-die Mode
            _log.fatal("Startup failure, terminating", t);
            System.exit(1);
        }
    }

    public void addModuleFailure(String moduleName, Throwable t)
    {
        //noinspection ThrowableResultOfMethodCallIgnored
        _moduleFailures.put(moduleName, t);
    }

    public Map<String, Throwable> getModuleFailures()
    {
        if (_moduleFailures.isEmpty())
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
        synchronized (_modulesLock)
        {
            ModuleContext result = _moduleContextMap.get(module.getName());
            if (result != null)
            {
                verifyModuleName(result, module.getName());
            }
            return result;
        }
    }

    public SqlScriptRunner getUpgradeScriptRunner()
    {
        return _upgradeScriptRunner;
    }

    // Run scripts using the default upgrade script runner
    public void runUpgradeScripts(Module module, SchemaUpdateType type)
    {
        runScripts(getUpgradeScriptRunner(), module, type);
    }

    public void runScripts(SqlScriptRunner runner, Module module, SchemaUpdateType type)
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
                        runner.runScripts(module, Collections.singletonList(script));
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Error running scripts in module " + module.getName(), e);
                }
            }
        }
    }

    // Runs the drop and create scripts in every module using a new SqlScriptRunner
    public void recreateViews()
    {
        SqlScriptRunner runner = new SqlScriptRunner();

        synchronized (UPGRADE_LOCK)
        {
            List<Module> modules = getModules();
            ListIterator<Module> iter = modules.listIterator(modules.size());

            // Run all the drop scripts (in reverse dependency order)
            while (iter.hasPrevious())
                runScripts(runner, iter.previous(), SchemaUpdateType.Before);

            // Run all the create scripts
            for (Module module : getModules())
                runScripts(runner, module, SchemaUpdateType.After);
        }

        if (_startupState == StartupState.StartupComplete)
        {
            WarningService.get().rerunSchemaCheck();
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
     * Initiate the module startup process, including any deferred upgrades and firing startup listeners.
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
            setStartupFailure(t);
            _log.error("Failure during module startup", t);
        }
    }

    private boolean _backgroundThreadsStarted = false;
    private static final Object BACKGROUND_THREAD_LOCK = new Object();

    public void attemptStartBackgroundThreads()
    {
        synchronized (BACKGROUND_THREAD_LOCK)
        {
            if (!_backgroundThreadsStarted && isStartupComplete() && AppProps.getInstance().isSetBaseServerUrl())
            {
                _backgroundThreadsStarted = true;

                for (Module module : getModules())
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
        var modules = getModules();

        for (Module m : modules)
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
        for (Module m : modules)
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
        ensureAtLeastOneRootAdminExists();
        setStartingUpMessage("Module startup complete");
    }

    // Now that we're done bootstrapping / starting up, verify that there's at least one root admin
    private void ensureAtLeastOneRootAdminExists()
    {
        try
        {
            if (UserManager.getActiveRealUserCount() > 0)
                SecurityManager.ensureAtLeastOneRootAdminExists();
        }
        catch (UnauthorizedException e)
        {
            throw new IllegalArgumentException("This deployment lacks a root administrator; it must have a Site Administrator, " +
                "an Application Administrator, or an Impersonating Troubleshooter. Use startup properties to assign one of " +
                "these roles to one or more users.");
        }
    }

    public void saveModuleContext(ModuleContext context)
    {
        ModuleContext stored = getModuleContextFromDatabase(context.getName());
        if (null == stored)
            Table.insert(null, getTableInfoModules(), context);
        else if (!stored.isDowngrade(context.getSchemaVersion())) // Never "downgrade" a module version, #30773
            Table.update(null, getTableInfoModules(), context, context.getName());
    }

    // Not transacted: SQL Server sp_dropapprole can't be called inside a transaction
    public void removeModule(ModuleContext context)
    {
        removeModule(context, false);
    }

    public void removeModule(ModuleContext context, boolean deleteFiles)
    {
        DbScope scope = _core.getSchema().getScope();
        SqlDialect dialect = _core.getSqlDialect();

        String moduleName = context.getName();

        _log.info("Deleting module {}", moduleName);
        String sql = "DELETE FROM " + _core.getTableInfoSqlScripts() + " WHERE ModuleName = ? AND Filename " + dialect.getCaseInsensitiveLikeOperator() + " ?";

        Module m = getModule(moduleName);
        SchemaActions schemaActions = getSchemaActions(m, context);

        schemaActions.deleteList().forEach(schema -> {
            _log.info("Dropping schema \"{}\"", schema);
            new SqlExecutor(_core.getSchema()).execute(sql, moduleName, schema + "-%");
            scope.getSqlDialect().dropSchema(_core.getSchema(), schema);
            scope.invalidateSchema(schema, DbSchemaType.Unknown); // Invalidates all versions of the schema and tables in the non-provisioned caches (e.g., module, bare, fast)
            SchemaNameCache.get().remove(scope); // Invalidates the list of schema names associated with this scope
        });

        schemaActions.skipList().forEach(sam -> _log.info("Skipping drop of schema \"{}\" because it's in use by module \"{}\"", sam.schema(), sam.module()));

        Table.delete(getTableInfoModules(), context.getName());

        if (null != m && deleteFiles)
        {
            FileUtil.deleteDir(m.getExplodedPath());
            if (null != m.getZippedPath())
                m.getZippedPath().delete();
        }

        synchronized (_modulesLock)
        {
            _moduleClassMap = removeMapValue(m, new HashMap<>(_moduleClassMap));
            _moduleMap = removeMapValue(m, new CaseInsensitiveHashMap<>(_moduleMap));
            removeMapValue(m, _controllerNameToModule);
            _moduleContextMap.remove(context.getName());
            _modules.remove(m);
        }

        if (m instanceof DefaultModule defaultModule)
            defaultModule.unregister();

        ContextListener.fireModuleChangeEvent(m);
        clearUnknownModuleCount();
    }

    public record SchemaAndModule(String schema, String module) {}
    public record SchemaActions(List<String> deleteList, List<SchemaAndModule> skipList){}

    // Divide the schemas reported by the specified module context into two lists: schemas that should be deleted and
    // schemas that shouldn't. If module is not-null (known) then the delete list contains all schemas and the skip list
    // is empty. If the module is null (unknown) then the delete list contains the schemas that no known module claims
    // and the skip list contains schemas that known modules still claim.
    public SchemaActions getSchemaActions(@Nullable Module module, ModuleContext context)
    {
        // If we're deleting an "unknown module" then avoid deleting any schema that's owned by a known module, Issue 47547
        Map<String, String> inUseSchemas = null == module ?
            getModules().stream()
                .flatMap(mod -> mod.getSchemaNames().stream().map(name -> Pair.of(name, mod.getName())))
                .collect(LabKeyCollectors.toCaseInsensitiveMap(Pair::getKey, Pair::getValue)) :
            Collections.emptyMap();

        List<String> deleteList = new LinkedList<>();
        List<SchemaAndModule> skipList = new LinkedList<>();

        for (String schema : context.getSchemaList())
        {
            String usingModuleName = inUseSchemas.get(schema);
            if (usingModuleName != null)
            {
                skipList.add(new SchemaAndModule(schema, usingModuleName));
            }
            else
            {
                deleteList.add(schema);
            }
        }

        return new SchemaActions(deleteList, skipList);
    }

    private void startNonCoreUpgradeAndStartup(Execution execution, File lockFile)
    {
        synchronized(UPGRADE_LOCK)
        {
            if (_upgradeState == UpgradeState.UpgradeRequired)
            {
                List<Module> modules = new ArrayList<>(getModules());
                modules.remove(getCoreModule());
                setUpgradeState(UpgradeState.UpgradeInProgress);

                ModuleUpgrader upgrader = new ModuleUpgrader(modules);
                upgrader.upgrade(() -> afterUpgrade(lockFile), execution);
            }
            else
            {
                execution.run(() -> afterUpgrade(lockFile));
            }
        }
    }

    // Final step in upgrade process: set the upgrade state to complete, perform post-upgrade tasks, and start up the modules.
    private void afterUpgrade(File lockFile)
    {
        setUpgradeState(UpgradeState.UpgradeComplete);

        handleUnknownModules();
        updateModuleProperties();
        initiateModuleStartup();

        // We're out of the critical section (regular and deferred upgrades are complete) so remove the lock file
        lockFile.delete();

        verifyRequiredModules();
    }

    // If the "requiredModules" parameter is present in application.properties then fail startup if any specified module is missing.
    // Particularly interesting for compliant deployments, e.g., <Parameter name="requiredModules" value="Compliance"/>
    private void verifyRequiredModules()
    {
        String requiredModules = getServletContext().getInitParameter("requiredModules");

        if (null != requiredModules)
        {
            List<String> missedModules = Arrays.stream(requiredModules.split(","))
                .filter(name -> !_moduleMap.containsKey(name)).toList();

            if (!missedModules.isEmpty())
                setStartupFailure(new ConfigurationException("Required module" + (missedModules.size() > 1 ? "s" : "") + " not present: " + missedModules));
        }
    }

    // Remove all unknown modules that are marked as AutoUninstall
    public void handleUnknownModules()
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
            ModuleContext context = getModuleContext(module);
            try
            {
                Map<String, Object> map = new HashMap<>();
                if (!Objects.equals(module.getClass().getName(), context.getClassName()))
                    map.put("ClassName", module.getClass().getName());
                if (module.isAutoUninstall() != context.isAutoUninstall())
                    map.put("AutoUninstall", module.isAutoUninstall());
                // Sort schema names to ensure consistency
                String schemaNames = StringUtils.trimToNull(
                    module.getSchemaNames().stream()
                        .sorted()
                        .collect(Collectors.joining(","))
                );
                if (!Objects.equals(schemaNames, context.getSchemas()))
                    map.put("Schemas", schemaNames);
                if (!map.isEmpty())
                    Table.update(null, getTableInfoModules(), map, module.getName());
            }
            catch (RuntimeSQLException e)
            {
                // This should be fixed now (see Issue 24473), but leave detailed logging in place just in case
                ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.ExtraMessage, module.getName(), false);
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    private void setUpgradeState(UpgradeState state)
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

    // Did this server start up with no modules installed? If so, it's a new install. This lets us tailor the
    // module upgrade UI to "install" or "upgrade," as appropriate.
    public boolean isNewInstall()
    {
        return _newInstall;
    }

    public void destroy()
    {
        // In the case of a startup failure, _modules may be null. We want to allow a context reload to succeed in this case,
        // since the reload may contain the code change to fix the problem
        var modules = getModules();
        if (modules != null)
        {
            modules.forEach(Module::destroy);
        }
    }

    public boolean hasModule(String name)
    {
        return getModule(name) != null;
    }

    public Module getModule(String name)
    {
        // _moduleMap is immutable so no need to synchronize for reads
        return _moduleMap.get(name);
    }

    public <M extends Module> M getModule(Class<M> moduleClass)
    {
        // _moduleClassMap is immutable so no need to synchronize for reads
        return (M) _moduleClassMap.get(moduleClass);
    }

    public Module getCoreModule()
    {
        return getModule(DefaultModule.CORE_MODULE_NAME);
    }

    /** @return all known modules, sorted in dependency order */
    public List<Module> getModules()
    {
        // We can return the immutable, thread-safe list without needing to lock anything
        return _modulesImmutable;
    }

    public List<Module> getModules(boolean userHasEnableRestrictedModulesPermission)
    {
        if (userHasEnableRestrictedModulesPermission)
            return getModules();

        return getModules()
            .stream()
            .filter(module -> !module.getRequireSitePermission())
            .toList();
    }

    /**
     * @return the modules, sorted in reverse dependency order. Typically used to resolve the most specific version of
     * a resource when one module "subclasses" another module.
     */
    public Collection<Module> orderModules(Collection<Module> modules)
    {
        List<Module> result = new ArrayList<>(modules.size());
        result.addAll(getModules()
            .stream()
            .filter(modules::contains)
            .toList());
        Collections.reverse(result);
        return result;
    }

    // Returns a set of data source names representing all external data sources that are required for module schemas.
    // These are just the names that modules advertise; there's no guarantee that they're defined or
    // valid. Be sure to null check after attempting to resolve each to a DbScope.
    public Set<String> getAllModuleDataSourceNames()
    {
        // Find all the external data sources that modules require
        Set<String> allModuleDataSourceNames = new LinkedHashSet<>();

        for (Module module : getModules())
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
        if (isUpgradeRequired() && UserManager.hasUsers())
        {
            return "This site is currently being upgraded to a new version of LabKey Server.";
        }
        return AppProps.getInstance().getAdminOnlyMessage();
    }

    // CONSIDER: ModuleUtil.java
    public Collection<String> getModuleSummaries(Container c)
    {
        List<String> list = new LinkedList<>();

        for (Module m : getModules())
            list.addAll(m.getSummary(c));

        return list;
    }

    public void initControllerToModule()
    {
        synchronized(_controllerNameToModule)
        {
            if (!_controllerNameToModule.isEmpty())
                return;
            List<Module> allModules = getModules();
            for (Module module : allModules)
            {
                TreeSet<String> set = new CaseInsensitiveTreeSet();

                for (Map.Entry<String, Class<? extends Controller>> entry : module.getControllerNameToClass().entrySet())
                {
                    String key = entry.getKey();
                    if (!set.add(key))
                        continue;   // Avoid duplicate work

                    _controllerNameToModule.put(key, module);

                    Class<?> clazz = entry.getValue();
                    for (Class<?> innerClass : clazz.getClasses())
                    {
                        for (Class<?> inter : innerClass.getInterfaces())
                        {
                            Class<?>[] supr = inter.getInterfaces();
                            if (supr.length == 1 && UrlProvider.class.equals(supr[0]))
                                UrlProviderService.getInstance().registerUrlProvider((Class<UrlProvider>)inter, innerClass);
                        }
                    }
                }
            }
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


    public @Nullable Module getModule(DbScope scope, String schemaName)
    {
        SchemaDetails details = getSchemaDetails(scope, schemaName);

        return null != details ? details.getModule() : null;
    }

    public @Nullable DbSchemaType getSchemaType(DbScope scope, String schemaName)
    {
        SchemaDetails details = getSchemaDetails(scope, schemaName);

        return null != details ? details.getType() : null;
    }

    private @Nullable SchemaDetails getSchemaDetails(DbScope scope, String schemaName)
    {
        // Consider: change this to a per-scope cache (similar to SchemaTableInfo and DbSchema caching)

        String fullyQualifiedSchemaName = DbSchema.getDisplayName(scope, schemaName);

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
                        DbScope moduleScope = DbScope.getDbScope(moduleDataSourceName);

                        if (null != moduleScope && moduleScope.getSqlDialect().canExecuteUpgradeScripts())
                        {
                            String labkeySchemaName = DbSchema.getDisplayName(moduleScope, "labkey");
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

    public void registerResourcePrefix(String prefix, Module module)
    {
        registerResourcePrefix(prefix, module.getName(), module.getSourcePath(), module.getBuildPath());
    }

    private void registerResourcePrefix(String prefix, String name, String sourcePath, String buildPath)
    {
        if (null == prefix || isEmpty(sourcePath) || isEmpty(buildPath))
            return;

        if (!new File(sourcePath).isDirectory() || !new File(buildPath).isDirectory())
            return;

        ResourceFinder finder = new ResourceFinder(name, sourcePath, buildPath);
        Collection<ResourceFinder> col = _resourceFinders.computeIfAbsent(prefix, k -> new ArrayList<>());

        synchronized(col)
        {
            col.add(finder);
        }
    }

    public @NotNull Collection<ResourceFinder> getResourceFindersForPath(String path)
    {
        //NOTE: jasper encodes underscores and dashes in JSPs, so decode them here
        path = path.replaceAll("_005f", "_");
        path = path.replaceAll("_002d", "-");

        Collection<ResourceFinder> finders = new LinkedList<>();

        for (Map.Entry<String, Collection<ResourceFinder>> e : _resourceFinders.entrySet())
            if (path.startsWith(e.getKey() + "/"))
                finders.addAll(e.getValue());

        return finders;
    }

    public Resource getResource(Path path)
    {
        for (Module m : getModules())
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
        return getModuleForController(HttpView.getRootContext().getActionURL().getController());
    }

    @Nullable
    public ModuleContext getModuleContextFromDatabase(@NotNull String name)
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ").
                append(getTableInfoModules(), "m").
                append(" WHERE LOWER(Name) = LOWER(?)").
                add(name);
        ModuleContext result = new SqlSelector(_core.getSchema(), sql).getObject(ModuleContext.class);
        if (result != null)
        {
            verifyModuleName(result, name);
        }
        return result;
    }

    /**
     * Issue 50763: Improve handling of module case-only rename during startup
     * It would be easy to fix up this record to match but other places store module names, like ETLs, reports, and more
     */
    private void verifyModuleName(ModuleContext moduleContext, String name)
    {
        if (!name.equals(moduleContext.getName()))
        {
            throw new IllegalStateException("Found an existing module record with a different casing from the module's new name. " +
                    "This is not supported. Existing: '" + moduleContext.getName() + "' New: '" + name + "'");
        }
    }

    public Collection<ModuleContext> getAllModuleContexts()
    {
        return new TableSelector(getTableInfoModules()).getCollection(ModuleContext.class);
    }

    private volatile Integer _unknownModuleCount = null;

    public int getUnknownModuleCount()
    {
        if (null == _unknownModuleCount)
            _unknownModuleCount = getUnknownModuleContexts().size();

        return _unknownModuleCount;
    }

    public void clearUnknownModuleCount()
    {
        _unknownModuleCount = null;
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

    public <T extends StartupProperty> void handleStartupProperties(MapBasedStartupPropertyHandler<T> handler)
    {
        if (handler.performChecks())
            startupPropertyChecks(handler);
        Map<String, T> props = handler.getProperties();
        Map<T, StartupPropertyEntry> map = new LinkedHashMap<>();
        handler.getStartupPropertyEntries().forEach(entry -> {
            T sp = props.get(entry.getName());
            if (null != sp)
            {
                entry.setStartupProperty(sp);
                map.put(sp, entry);
            }
        });
        handler.handle(map);
    }

    public <T extends StartupProperty> void handleStartupProperties(LenientStartupPropertyHandler<T> handler)
    {
        if (handler.performChecks())
            startupPropertyChecks(handler);
        StartupProperty sp = handler.getProperty();
        handler.handle(handler.getStartupPropertyEntries().stream()
            .peek(entry -> entry.setStartupProperty(sp))
            .toList());
    }

    private void startupPropertyChecks(StartupPropertyHandler<?> handler)
    {
        assert !isStartupComplete() : "All startup properties must be handled during startup";
        boolean notExists = _startupPropertyHandlers.add(handler);
        assert notExists : "StartupPropertyHandler with scope " + handler.getScope() + " has already been registered!";
    }

    public static class StartupPropertyStartupListener implements StartupListener
    {
        @Override
        public String getName()
        {
            return "Startup Property Validation";
        }

        @Override
        public void moduleStartupComplete(ServletContext servletContext)
        {
            // Log error for any unknown startup properties (admin error)
            List<String> unknown = ModuleLoader.getInstance().getStartupPropertyEntries(null)
                .stream()
                .filter(entry -> null == entry.getStartupProperty())
                .map(entry -> entry.getScope() + "." + entry.getName() + ": " + entry.getValue()).toList();

            if (!unknown.isEmpty())
                _log.info("Unknown startup propert" + (unknown.size() == 1 ? "y: " : "ies: ") + unknown);

            // Failing this check indicates a coding issue, so execute it only when assertions are on
            assert checkPropertyScopeMapping();
         }
    }

    private static boolean checkPropertyScopeMapping()
    {
        // Enumerate all StartupPropertyHandlers and verify that every supplied StartupProperty maps to a single
        // source. If not, there's a coding error.
        Map<StartupProperty, String> propertyScopeMap = new HashMap<>();
        ModuleLoader.getInstance().getStartupPropertyHandlers()
            .forEach(handler -> handler.getProperties().values().forEach(sp -> {
                String previousScope = propertyScopeMap.put(sp, handler.getScope());
                assert previousScope == null : "Two scopes (\"" + previousScope + "\" and \"" + handler.getScope() + "\") both used the same StartupProperty (\"" + sp + "\")!";
            }));

        Set<String> scopeNameMap = new CaseInsensitiveHashSet();
        ModuleLoader.getInstance().getStartupPropertyHandlers()
            .forEach(handler -> handler.getProperties().values().forEach(sp -> {
                boolean notPresent = scopeNameMap.add(handler.getScope() + ":" + sp.getPropertyName());
                assert notPresent : "Startup property \"" + handler.getScope() + "." + sp.getPropertyName() + "\" is handled by two separate code paths!";
            }));

        return true;
    }

    public Set<StartupPropertyHandler<? extends StartupProperty>> getStartupPropertyHandlers()
    {
        return _startupPropertyHandlers;
    }

    /**
     * Returns the startup property entries for the specified scope. If no scope is specified then all properties are
     * returned. If the server is bootstrapping then properties with both the bootstrap and startup modifiers are
     * returned otherwise only startup properties are returned.
     *
     * Do not call this directly! Use a StartupPropertyHandler instead.
     */
    @NotNull
    public Collection<StartupPropertyEntry> getStartupPropertyEntries(@Nullable String scope)
    {
        Collection<StartupPropertyEntry> props = Collections.emptyList();
        if (!_startupPropertyMap.isEmpty())
        {
            if (scope != null)
            {
                if (_startupPropertyMap.containsKey(scope))
                    props = _startupPropertyMap.get(scope);
            }
            else
                props = _startupPropertyMap.values();
        }

        // We filter here because loadStartupProps() gets called very early, before _newInstall is set
        return props.stream()
            .filter(prop -> prop.getModifier() != StartupPropertyEntry.modifier.bootstrap || isNewInstall())
            .collect(Collectors.toList());
    }

    public FileLike getStartupPropDirectory()
    {
        return _startupPropertiesDir;
    }

    /**
     * Loads startup/bootstrap properties from configuration files.
     * <a href="https://www.labkey.org/Documentation/wiki-page.view?name=bootstrapProperties#using">Documentation Page</a>
     */
    private void loadStartupProps()
    {
        FileLike propsDir = getStartupPropDirectory();
        if (null == propsDir)
            return;

        if (!propsDir.isDirectory())
            return;

        FileLike newinstall = propsDir.resolveChild("newinstall");
        if (newinstall.isFile())
        {
            _log.debug("'newinstall' file detected: " + newinstall.toNioPathForRead());

            _newInstall = true;

            // propsDir is readonly, so we need to cheat to get a File
            var newInstallFile = newinstall.toNioPathForRead().toFile();
            if (newInstallFile.canWrite())
                newInstallFile.delete();
            else
                throw new ConfigurationException("file 'newinstall' exists, but is not writeable: " + newinstall.toNioPathForRead());
        }
        else
        {
            _log.debug("no 'newinstall' file detected");
        }

        List<FileLike> propFiles = propsDir.getChildren().stream().filter(f -> f.getName().endsWith(".properties")).toList();

        if (!propFiles.isEmpty())
        {
            List<FileLike> sortedPropFiles = propFiles.stream()
                .sorted(Comparator.comparing(FileLike::getName).reversed())
                .toList();

            for (FileLike propFile : sortedPropFiles)
            {
                _log.debug("loading propsFile: " + propFile.toNioPathForRead());

                try (InputStream in = propFile.openInputStream())
                {
                    Properties props = new Properties();
                    props.load(in);

                    for (Map.Entry<Object, Object> entry : props.entrySet())
                    {
                        if (entry.getKey() instanceof String && entry.getValue() instanceof String)
                        {
                            _log.trace("property '" + entry.getKey() + "' resolved to value: '" + entry.getValue() + "'");

                            addStartupPropertyEntry(entry.getKey().toString(), entry.getValue().toString());
                        }
                    }
                }
                catch (Exception e)
                {
                    _log.error("Error parsing startup config properties file '" + propFile.toNioPathForRead() + "'", e);
                }
            }
        }
        else
        {
            _log.debug("no propFiles to load");
        }

        // load any system properties with the labkey prop prefix
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet())
        {
            String name = String.valueOf(entry.getKey());
            String value = String.valueOf(entry.getValue());

            if (name != null && name.startsWith(StartupPropertyEntry.SYS_PROP_PREFIX) && value != null)
            {
                addStartupPropertyEntry(name.substring(StartupPropertyEntry.SYS_PROP_PREFIX.length()), value);
            }
        }
    }

    private void addStartupPropertyEntry(String scope, String value)
    {
        StartupPropertyEntry entry = createConfigProperty(scope, value);
        if (_startupPropertyMap.containsMapping(entry.getScope(), entry))
            _startupPropertyMap.removeMapping(entry.getScope(), entry);
        _startupPropertyMap.put(entry.getScope(), entry);
    }

    /**
     * Parse the startup property line and construct a StartupPropertyEntry object. A startup property
     * can have an optional dot delimited scope and an optional semicolon delimited modifier, for example:
     * siteSettings.baseServerUrl;bootstrap defines a property named "baseServerUrl" in the "siteSettings"
     * scope having the bootstrap modifier.
     */
    private StartupPropertyEntry createConfigProperty(String key, String value)
    {
        String name;
        String scope = null;
        String modifier = null;

        // the first dot delimited section is always the scope, the rest is the name
        if (key.contains("."))
        {
            scope = key.substring(0, key.indexOf('.'));
            key = key.substring(key.indexOf('.') + 1);
        }

        String[] parts = key.split(";");
        if (parts.length == 2)
        {
            name = parts[0];
            modifier = parts[1];
        }
        else
            name = key;

        return new StartupPropertyEntry(name, value, modifier, scope);
    }

    private static class SchemaDetails
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

    @Override
    public void beforeReport(Set<Object> set)
    {
        set.addAll(getModules());
    }
}
