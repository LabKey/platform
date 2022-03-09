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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.ModuleXml.ModuleXmlCacheHandler;
import org.labkey.api.query.OlapSchemaInfo;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.ContainerUser;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Standard base class for modules, supplies no-op implementations for many optional methods.
 * User: migra
 * Date: Jul 14, 2005
 */
public abstract class DefaultModule implements Module, ApplicationContextAware
{
    public static final String CORE_MODULE_NAME = "Core";

    private static final String DEPENDENCIES_FILE_PATH = "credits/dependencies.txt";
    private static final Logger _log = LogManager.getLogger(DefaultModule.class);
    private static final Set<Pair<Class<? extends DefaultModule>, String>> INSTANTIATED_MODULES = new HashSet<>();

    static final ModuleResourceCache<ModuleXml> MODULE_XML_CACHE = ModuleResourceCaches.create("module.xml files", new ModuleXmlCacheHandler(), ResourceRootProvider.getStandard(new Path()));

    private final Queue<Pair<String, Runnable>> _deferredUpgradeRunnables = new LinkedList<>();
    private final Map<String, Class<? extends Controller>> _controllerNameToClass = new LinkedHashMap<>();
    private final Map<Class<? extends Controller>, String> _controllerClassToName = new HashMap<>();
    private final Set<String> _moduleDependencies = new CaseInsensitiveHashSet();
    private final Map<String, ModuleProperty> _moduleProperties = new LinkedHashMap<>();

    private Set<Module> _resolvedModuleDependencies;
    private Collection<WebPartFactory> _webPartFactories;
    private ModuleResourceResolver _resolver;
    private String _name = null;
    private String _label = null;
    private String _description = null;
    private Double _schemaVersion = null;
    private double _requiredServerVersion = 0.0;
    private String _moduleDependenciesString = null;
    private String _url = null;
    private String _organization = null;
    private String _organizationUrl = null;
    private String _buildType = null;
    private String _author = null;
    private String _maintainer = null;
    private String _license = null;
    private String _licenseUrl = null;
    private String _vcsRevision = null;
    private String _vcsUrl = null;
    private String _vcsBranch = "Unknown";
    private String _vcsTag = "Unknown";
    private String _buildUser = null;
    private String _buildTime = null;
    private String _buildOS = null;
    private String _buildPath = null;
    private String _sourcePath = null;
    private String _buildNumber = null;
    private String _enlistmentId = null;
    private File _explodedPath = null;
    private File _zippedPath = null;
    private Boolean _manageVersion = null;
    private String _releaseVersion = null;

    // for displaying development status of module
    private boolean _sourcePathMatched = false;
    private boolean _sourceEnlistmentIdMatched = false;

    protected String _resourcePath = null;

    protected DefaultModule()
    {
        assert MemTracker.getInstance().put(this);
    }

    @Override
    public final int compareTo(@NotNull Module m)
    {
        // Ensure that the "Core" module is listed first
        if (this.getName().equalsIgnoreCase("core") && !m.getName().equalsIgnoreCase("core"))
        {
            return 1;
        }
        else if (!this.getName().equalsIgnoreCase("core") && m.getName().equalsIgnoreCase("core"))
        {
            return -1;
        }
        else
        {
            return getName().compareToIgnoreCase(m.getName());
        }
    }

    @Override
    final public void initialize()
    {
        for (String dsName : ModuleLoader.getInstance().getModuleDataSourceNames(this))
        {
            Throwable t = DbScope.getDataSourceFailure(dsName);

            // Data source is defined but connection wasn't successful
            if (null != t)
                throw new ConfigurationException("This module requires a properly configured data source called \"" + dsName + "\"", t);

            // Data source is defined and ready to go
            if (null != DbScope.getDbScope(dsName))
                continue;

            if (AppProps.getInstance().isDevMode())
            {
                // A module data source is missing and we're in dev mode, so attempt to create a proxy data source that uses the labkey
                // database. This can be helpful on test and dev machines. See #23730.
                DbScope scope = DbScope.getLabKeyScope();

                _log.warn("Module \"" + getName() + "\" requires a data source called \"" + dsName + "\". It's not configured, so it will be created against the primary labkey database (\"" + scope.getDatabaseName() + "\") instead.");
                DbScope.addScope(dsName, scope.getDataSource(), scope.getLabKeyProps());
                if (null == DbScope.getDbScope(dsName)) // Force immediate connection to test
                    throw new ConfigurationException("Failed to connect to data source \"" + dsName + "\", created against the labkey database (\"" + scope.getDatabaseName() + "\").");
            }
            else
            {
                // A module data source is missing and we're in production mode, so issue a warning. The data source might be optional, e.g., on staging servers. See #23830
                _log.warn("Module \"" + getName() + "\" requires a data source called \"" + dsName + "\" but it's not configured. This module will be loaded, but it might not operate correctly.");
            }
        }

        synchronized (INSTANTIATED_MODULES)
        {
            //simple modules all use the same Java class, so we need to also include
            //the module name in the instantiated modules set
            Pair<Class<? extends DefaultModule>, String> reg = new Pair<>(getClass(), getName());
            if (INSTANTIATED_MODULES.contains(reg))
                throw new IllegalStateException("An instance of module " + getClass() +  " with name '" + getName() + "' has already been created. Modules should be singletons");
            else
                INSTANTIATED_MODULES.add(reg);
        }

        ModuleLoader.getInstance().registerResourcePrefix(getResourcePath(), this);

//        _resolver = new ModuleResourceResolver(this, getResourceDirectories(), getResourceClasses());

        init();
    }

    public void unregister()
    {
        synchronized(INSTANTIATED_MODULES)
        {
            Pair<Class<?>, String> reg = new Pair<>(getClass(), getName());
            INSTANTIATED_MODULES.remove(reg);
        }
    }

    protected abstract void init();

    /**
     * Create the WebPartFactories that this module defines in code. File-based webpart factories are handled implicitly.
     *
     * @return A collection of WebPartFactories
     */
    protected abstract @NotNull Collection<? extends WebPartFactory> createWebPartFactories();

    /** @return true if this module has SQL upgrade scripts that should be run as part of startup */
    public abstract boolean hasScripts();

    @Override
    final public void startup(ModuleContext moduleContext)
    {
        doStartup(moduleContext);
    }

    protected abstract void doStartup(ModuleContext moduleContext);

    @Override
    public String getResourcePath()
    {
        return _resourcePath;
    }

    // resourcePath can optionally be set in the module.properties / xml files; called by spring
    @SuppressWarnings("UnusedDeclaration")
    public void setResourcePath(String resourcePath)
    {
        // If the resourcePath was set in the module.properties or xml file, override the path derived from the
        // module class.
        if (StringUtils.isNotEmpty(resourcePath))
        {
            _resourcePath = resourcePath;
        }
        else _resourcePath = "/" + getClass().getPackage().getName().replaceAll("\\.", "/");
    }

    // Note: First controller registered in a module is special: getTabURL() treats it as the "default controller", e.g.
    protected void addController(String primaryName, Class<? extends Controller> cl, String... aliases)
    {
        if (!Controller.class.isAssignableFrom(cl))
            throw new IllegalArgumentException(cl.toString());

        // Map controller class to canonical name
        addControllerClass(cl, primaryName);

        // Map aliases to controller class
        for (String alias : aliases)
            addControllerName(alias, cl);
    }


    // Map controller class to canonical name
    private void addControllerClass(Class<? extends Controller> controllerClass, String primaryName)
    {
        assert !_controllerNameToClass.containsValue(controllerClass) : "Controller class '" + controllerClass + "' is already registered";
        _controllerClassToName.put(controllerClass, primaryName);
        addControllerName(primaryName, controllerClass);
    }


    // Map all names to controller class
    private void addControllerName(String controllerName, Class<? extends Controller> controllerClass)
    {
        assert null == _controllerNameToClass.get(controllerName) : "Controller name '" + controllerName + "' is already registered";
        _controllerNameToClass.put(controllerName, controllerClass);
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return getName();
    }

    @Override
    public void beforeUpdate(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
            ModuleLoader.getInstance().runUpgradeScripts(this, SchemaUpdateType.Before);
    }

    /**
     * Upgrade each schema in this module to the latest version.
     */
    @Override
    public void versionUpdate(ModuleContext moduleContext) throws Exception
    {
        if (hasScripts())
        {
            if (null == getSchemaVersion())
            {
                throw new IllegalStateException("getSchemaVersion() was null for module: " + getName() + " even though hasScripts() was true");
            }

            SqlScriptProvider provider = new FileSqlScriptProvider(this);
            SqlScriptRunner runner = ModuleLoader.getInstance().getUpgradeScriptRunner();

            for (DbSchema schema : provider.getSchemas())
            {
                SqlScriptManager manager = SqlScriptManager.get(provider, schema);
                List<SqlScript> scripts = manager.getRecommendedScripts(getSchemaVersion());

                if (!scripts.isEmpty())
                    runner.runScripts(this, scripts);

                SqlScript script = SchemaUpdateType.After.getScript(provider, schema);

                if (null != script)
                    runner.runScripts(this, Collections.singletonList(script));
            }
        }
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
    }

    // TODO: Move getWebPartFactories() and _webPartFactories into Portal... shouldn't be the module's responsibility
    // This should also allow moving SimpleWebPartFactoryCache and dependencies into Internal

    private final Object FACTORY_LOCK = new Object();

    @Override
    public final @NotNull Collection<WebPartFactory> getWebPartFactories()
    {
        synchronized (FACTORY_LOCK)
        {
            if (null == _webPartFactories)
            {
                Collection<WebPartFactory> wpf = new ArrayList<>();

                // Get all the Java webpart factories
                for (WebPartFactory webPartFactory : createWebPartFactories())
                {
                    // Must setModule(), since they aren't initialized with this information
                    webPartFactory.setModule(this);
                    wpf.add(webPartFactory);
                }

                // File-based webpart factories; no need to call setModule() since module is initialized in constructor
                wpf.addAll(Portal.WEB_PART_FACTORY_CACHE.getResourceMap(this));

                _webPartFactories = wpf;
            }
            return _webPartFactories;
        }
    }


    public final void clearWebPartFactories()
    {
        synchronized (FACTORY_LOCK)
        {
            _webPartFactories = null;
        }
    }


    @Override
    public void destroy()
    {
    }


    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }


    @Override
    public final Map<String, Class<? extends Controller>> getControllerNameToClass()
    {
        return _controllerNameToClass;
    }


    @Override
    public final Map<Class<? extends Controller>, String> getControllerClassToName()
    {
        return _controllerClassToName;
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        Map<String, Class<? extends Controller>> map = getControllerNameToClass();

        // Some modules have no controllers (e.g., BigIron)
        if (!map.isEmpty())
        {
            // Note: First registered controller is special -- its BeginAction becomes the tab URL for the module
            Map.Entry<String, Class<? extends Controller>> entry = map.entrySet().iterator().next();
            Controller controller = getController(null, entry.getValue());
            if (controller instanceof SpringActionController)
            {
                Controller action = ((SpringActionController) controller).getActionResolver().resolveActionName(controller, "begin");
                if (action != null)
                {
                    // Use the deprecated constructor, since passing in an action class like SimpleAction that is used
                    // to back multiple URLs with different static HTML files can't be resolved to the right URL
                    return new ActionURL(entry.getKey(), "begin", c);
                }
            }
        }
        return null;
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE;
    }

    protected void addWebPart(String name, Container c, @Nullable String location)
    {
        addWebPart(name, c, location, -1, new HashMap<>());
    }

    protected void addWebPart(String name, Container c, String location, int partIndex)
    {
        addWebPart(name, c, location, partIndex, new HashMap<>());
    }

    protected void addWebPart(String name, Container c, @Nullable String location, int partIndex, Map<String, String> properties)
    {
        boolean foundPart = false;

        for (Portal.WebPart part : Portal.getParts(c))
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
                Portal.addPart(c, desc, location, partIndex, properties);
            }
        }
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

    /**
     * Returns all non-provisioned schemas claimed by the module in {@link #getSchemaNames()}. Override if a different
     * set of schemas should be tested.
     */
    @Override
    @NotNull
    @JsonIgnore
    public Set<DbSchema> getSchemasToTest()
    {
        Set<String> schemaNames = new LinkedHashSet<>(getSchemaNames());
        schemaNames.removeAll(getProvisionedSchemaNames());

        Set<DbSchema> result = new LinkedHashSet<>();

        for (String schemaName : schemaNames)
        {
            DbSchema schema = DbSchema.get(schemaName, DbSchemaType.Module);
            result.add(schema);
        }
        return result;
    }

    @NotNull
    @Override
    public Collection<String> getProvisionedSchemaNames()
    {
        return Collections.emptySet();
    }

    protected static final Set<SupportedDatabase> ALL_DATABASES = Set.of(SupportedDatabase.mssql, SupportedDatabase.pgsql);

    private Set<SupportedDatabase> _supportedDatabases = ALL_DATABASES;

    @NotNull
    @Override
    public final Set<SupportedDatabase> getSupportedDatabasesSet()
    {
        return _supportedDatabases;
    }


    // Used by Spring configuration reflection
    @SuppressWarnings("UnusedDeclaration")
    public final String getSupportedDatabases()
    {
        Set<SupportedDatabase> set = getSupportedDatabasesSet();
        return StringUtils.join(set, ",");
    }


    // Used by Spring configuration reflection
    @SuppressWarnings("UnusedDeclaration")
    public final void setSupportedDatabases(String list)
    {
        Set<SupportedDatabase> supported = new HashSet<>();
        String[] dbs = StringUtils.split(list, ',');

        for (String db : dbs)
        {
            if (StringUtils.isEmpty(db))
                continue;
            supported.add(SupportedDatabase.valueOf(db));
        }

        if (!supported.isEmpty())
            _supportedDatabases = supported;
    }


    @Override
    public String getName()
    {
        return _name;
    }

    public final void setName(String name)
    {
        checkLocked();
        if (StringUtils.isEmpty(name))
            return;
        if (!StringUtils.isEmpty(_name))
        {
            if (!_name.equals(name))
                _log.error("Attempt to change name of module from " + _name + " to " + name + ".");
            return;
        }
        _name = name;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return _schemaVersion;
    }

    public final void setSchemaVersion(Double schemaVersion)
    {
        checkLocked();
        if (null == schemaVersion)
            return;
        if (null != _schemaVersion)
        {
            if (!_schemaVersion.equals(schemaVersion))
                _log.error("Attempt to change version of module from " + _schemaVersion + " to " + schemaVersion + ".");
            return;
        }
        _schemaVersion = schemaVersion;
    }

    public final double getRequiredServerVersion()
    {
        return _requiredServerVersion;
    }

    public final void setRequiredServerVersion(double requiredServerVersion)
    {
        checkLocked();
        if (0.0 != requiredServerVersion)
            _requiredServerVersion = requiredServerVersion;
    }

    @Nullable
    @Override
    public final String getLabel()
    {
        return _label;
    }

    public final void setLabel(String label)
    {
        checkLocked();
        _label = label;
    }

    @Nullable
    @Override
    public final String getDescription()
    {
        return _description;
    }

    public final void setDescription(String description)
    {
        checkLocked();
        _description = description;
    }

    @Nullable
    @Override
    public final String getUrl()
    {
        return _url;
    }

    public final void setUrl(String url)
    {
        checkLocked();
        _url = url;
    }

    @Nullable
    @Override
    public final String getAuthor()
    {
        return _author;
    }

    public final void setAuthor(String author)
    {
        checkLocked();
        _author = author;
    }

    @Nullable
    @Override
    public final String getMaintainer()
    {
        return _maintainer;
    }

    @SuppressWarnings("unused")
    public final void setMaintainer(String maintainer)
    {
        checkLocked();
        _maintainer = maintainer;
    }

    @Nullable
    @Override
    public final String getOrganization()
    {
        return _organization;
    }

    public final void setOrganization(String organization)
    {
        checkLocked();
        _organization = organization;
    }

    @Nullable
    @Override
    public String getBuildType()
    {
        return _buildType;
    }

    @SuppressWarnings("unused")
    public void setBuildType(String buildType)
    {
        _buildType = buildType;
    }

    @Nullable
    @Override
    public final String getOrganizationUrl()
    {
        return _organizationUrl;
    }

    @SuppressWarnings("unused")
    public final void setOrganizationUrl(String organizationUrl)
    {
        checkLocked();
        _organizationUrl = organizationUrl;
    }

    @Nullable
    @Override
    public final String getLicense()
    {
        return _license;
    }

    @SuppressWarnings("unused")
    public final void setLicense(String license)
    {
        checkLocked();
        _license = license;
    }

    @Nullable
    @Override
    public final String getLicenseUrl()
    {
        return _licenseUrl;
    }

    @SuppressWarnings("unused")
    public final void setLicenseUrl(String licenseUrl)
    {
        checkLocked();
        _licenseUrl = licenseUrl;
    }


    @Override
    public final Set<String> getModuleDependenciesAsSet()
    {
        return _moduleDependencies;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setModuleDependencies(String dependencies)
    {
        checkLocked();
        _moduleDependenciesString = dependencies;

        if (null == dependencies || dependencies.isEmpty())
            return;

        String[] depArray = dependencies.split(",");
        for (String dependency : depArray)
        {
            dependency = dependency.trim();
            if (!dependency.isEmpty())
                _moduleDependencies.add(dependency.toLowerCase());
        }
        _resolvedModuleDependencies = null;
    }

    public final String getModuleDependencies()
    {
        return _moduleDependenciesString;
    }

    @Nullable
    @Override
    public final String getVcsRevision()
    {
        return _vcsRevision;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setVcsRevision(String vcsRevision)
    {
        checkLocked();
        _vcsRevision = vcsRevision;
    }

    @Nullable
    @Override
    public final String getVcsUrl()
    {
        return _vcsUrl;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setVcsUrl(String vcsUrl)
    {
        checkLocked();
        _vcsUrl = vcsUrl;
    }

    @Nullable
    @Override
    public String getVcsBranch()
    {
        return _vcsBranch;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setVcsBranch(String vcsBranch)
    {
        _vcsBranch = vcsBranch;
    }

    @Nullable
    @Override
    public String getVcsTag()
    {
        return _vcsTag;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setVcsTag(String vcsTag)
    {
        _vcsTag = vcsTag;
    }

    public final String getBuildUser()
    {
        return _buildUser;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setBuildUser(String buildUser)
    {
        _buildUser = buildUser;
    }

    @Override
    public final String getBuildTime()
    {
        return _buildTime;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setBuildTime(String buildTime)
    {
        _buildTime = buildTime;
    }

    public final String getBuildOS()
    {
        return _buildOS;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setBuildOS(String buildOS)
    {
        _buildOS = buildOS;
    }

    @Override
    public final String getSourcePath()
    {
        return _sourcePath;
    }

    public final void setSourcePath(String sourcePath)
    {
        if (!AppProps.getInstance().isIgnoreModuleSource())
        {
            _sourcePath = sourcePath;
        }
    }

    @Override
    public final String getBuildPath()
    {
        return _buildPath;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setBuildPath(String buildPath)
    {
        if (!AppProps.getInstance().isIgnoreModuleSource())
        {
            _buildPath = buildPath;
        }
    }

    @Override
    public final String getBuildNumber()
    {
        return _buildNumber;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setBuildNumber(String buildNumber)
    {
        _buildNumber = buildNumber;
    }

    public final String getEnlistmentId()
    {
        return _enlistmentId;
    }

    @SuppressWarnings("UnusedDeclaration")
    public final void setEnlistmentId(String enlistmentId)
    {
        _enlistmentId = enlistmentId;
    }

    @SuppressWarnings("unused")
    public Boolean getManageVersion()
    {
        return _manageVersion;
    }

    @SuppressWarnings("unused")
    public void setManageVersion(Boolean manageVersion)
    {
        _manageVersion = manageVersion;
    }

    @Override
    public boolean shouldManageVersion()
    {
        return _manageVersion != Boolean.FALSE;
    }

    @Override
    public @Nullable String getReleaseVersion()
    {
        return _releaseVersion;
    }

    @SuppressWarnings("unused")
    public void setReleaseVersion(String releaseVersion)
    {
        _releaseVersion = releaseVersion;
    }

    @Override
    public final Map<String, String> getProperties()
    {
        Map<String, String> props = new LinkedHashMap<>();

        props.put("Module Class", getClass().getName());
        props.put("Schema Version", getFormattedSchemaVersion());
        if (StringUtils.isNotBlank(getReleaseVersion()))
            props.put("Release Version", getReleaseVersion());
        if (StringUtils.isNotBlank(getAuthor()))
            props.put("Author", getAuthor());
        if (StringUtils.isNotBlank(getMaintainer()))
            props.put("Maintainer", getMaintainer());
        if (StringUtils.isNotBlank(getOrganization()))
            props.put("Organization", getOrganization());
        if (StringUtils.isNotBlank(getOrganizationUrl()))
            props.put("OrganizationURL", getOrganizationUrl());
        if (StringUtils.isNotBlank(getBuildType()))
            props.put("Build Type", getBuildType());
        if (StringUtils.isNotBlank(getLicense()))
            props.put("License", getLicense());
        if (StringUtils.isNotBlank(getLicenseUrl()))
            props.put("LicenseURL", getLicenseUrl());
        props.put("Extracted Path", getExplodedPath().getAbsolutePath());
        props.put("VCS URL", getVcsUrl());
        props.put("VCS Revision", getVcsRevision());
        props.put("VCS Branch", getVcsBranch());
        props.put("VCS Tag", getVcsTag());
        props.put("Build OS", getBuildOS());

        props.put("Build Time", getBuildTime());
        props.put("Build User", getBuildUser());
        props.put("Build Path", getBuildPath());
        props.put("Source Path", getSourcePath());
        if (null != getZippedPath())
            props.put("Module File", getZippedPath().getPath());
        props.put("Build Number", getBuildNumber());
        props.put("Enlistment ID", getEnlistmentId());
        props.put("Module Dependencies", StringUtils.trimToNull(getModuleDependencies()) == null ? "<none>" : getModuleDependencies());

        return props;
    }

    @Override
    public final File getExplodedPath()
    {
        return _explodedPath;
    }

    @Override
    public final void setExplodedPath(File path)
    {
        _explodedPath = path.getAbsoluteFile();
    }

    @Override
    public @Nullable File getZippedPath()
    {
        return _zippedPath;
    }

    @Override
    public void setZippedPath(File zipped)
    {
        _zippedPath = zipped;
    }

    @Override
    public final Set<String> getSqlScripts(@NotNull DbSchema schema)
    {
        SqlDialect dialect = schema.getSqlDialect();

        String sqlScriptsPath = getSqlScriptsPath(dialect);
        Resource dir = getModuleResource(sqlScriptsPath);
        if (dir == null || !dir.isCollection())
            return Collections.emptySet();

        Set<String> fileNames = new HashSet<>();

        for (String script : dir.listNames())
        {
            if ((StringUtils.endsWithIgnoreCase(script, ".sql") || StringUtils.endsWithIgnoreCase(script, ".jsp")) && StringUtils.startsWithIgnoreCase(script, schema.getResourcePrefix() + "-"))
                fileNames.add(script);
        }

        return fileNames;
    }

    @Override
    public final String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        return "schemas/dbscripts/" + dialect.getSQLScriptPath() + "/";
    }

    @Override
    public final ModuleResourceResolver getModuleResolver()
    {
        if (_resolver == null)
            _resolver = new ModuleResourceResolver(this, getResourceDirectory());

        return _resolver;
    }

    @Override
    public final Resource getModuleResource(Path path)
    {
        return getModuleResolver().lookup(path);
    }

    @Override
    public final Resource getModuleResource(String path)
    {
        return getModuleResource(Path.parse(path));
    }

    @Override
    public final InputStream getResourceStream(String path) throws IOException
    {
        Resource r = getModuleResource(path);
        if (r != null && r.isFile())
            return r.getInputStream();
        return null;
    }

    @Override
    public String toString()
    {
        return getName() + " " + getReleaseVersion() + " " + getClass().getName();
    }


    @Override
    public @Nullable UpgradeCode getUpgradeCode()
    {
        return null;
    }


    @Override
    public void dispatch(HttpServletRequest request, HttpServletResponse response, ActionURL url)
            throws ServletException, IOException
    {
        int stackSize = -1;
        Controller controller = getController(request, url.getController());

        if (controller == null)
        {
            ExceptionUtil.handleException(request, response, new NotFoundException("No LabKey Server controller registered to handle request: " + url.getController()), null, false);
            return;
        }

        ViewContext rootContext = new ViewContext(request, response, url);

        try
        {
            stackSize = HttpView.getStackSize();

            response.setContentType("text/html;charset=UTF-8");
            ResponseHelper.setNoCache(response);

            HttpView.initForRequest(rootContext, request, response);
            assert rootContext == HttpView.currentContext();

            // Store the original URL in case we need to redirect for authentication
            if (request.getAttribute(ViewServlet.ORIGINAL_URL_STRING) == null)
            {
                URLHelper helper = new URLHelper(request);
                request.setAttribute(ViewServlet.ORIGINAL_URL_STRING, helper.getURIString());
                request.setAttribute(ViewServlet.ORIGINAL_URL_URLHELPER, helper);
            }
            request.setAttribute(ViewServlet.REQUEST_ACTION_URL, url);

            if (controller instanceof HasViewContext)
                ((HasViewContext)controller).setViewContext(rootContext);
            controller.handleRequest(request, response);
        }
        catch (ServletException | IOException x)
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

    @Override
    public Controller getController(HttpServletRequest request, String name)
    {
        Class<? extends Controller> cls = _controllerNameToClass.get(name);
        if (null == cls)
            return null;

        return getController(request, cls);
    }


    public Controller getController(@Nullable HttpServletRequest request, Class<? extends Controller> cls)
    {
        try
        {
            return cls.getConstructor().newInstance();
        }
        catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    @NotNull
    public List<File> getStaticFileDirectories()
    {
        List<File> l = new ArrayList<>(3);
        String build = getBuildPath();
        File exploded = getExplodedPath();
        String source = getSourcePath();

        if (AppProps.getInstance().isDevMode())
        {
            if (null != source)
            {
                File f = new File(new File(source), "web");
                if (f.isDirectory())
                    l.add(f);
                f = new File(new File(source), "resources/web");
                if (f.isDirectory())
                    l.add(f);
                f = new File(new File(source), "webapp");
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

    File _resourceDirectory;
    File NULL_FILE = new File("....");

    public File getResourceDirectory()
    {
        if (null == _resourceDirectory)
        {
            File dir = computeResourceDirectory();
            _resourceDirectory = null==dir ? NULL_FILE : dir;
        }
        return NULL_FILE==_resourceDirectory ? null : _resourceDirectory;
    }

    protected File computeResourceDirectory()
    {
        // We load resources from the module's source directory if all of the following conditions are true:
        //
        // - devmode = true
        // - The module's source path is a directory that exists on the web server
        // - The module has an enlistment ID set
        // - The module's enlistment ID matches EITHER the enlistment ID in the web server's source root OR the enlistment ID in
        //   the module's sourcePath
        //
        if (AppProps.getInstance().isDevMode())
        {
            String sourcePath = getSourcePath();

            if (null != sourcePath)
            {
                File sourceDir = new File(sourcePath);

                if (sourceDir.isDirectory())
                {
                    _sourcePathMatched = true;
                    String moduleEnlistmentId = getEnlistmentId();

                    if (StringUtils.isNotBlank(moduleEnlistmentId))
                    {
                        String serverEnlistmentId = AppProps.getInstance().getEnlistmentId();
                        boolean useSource = (null != serverEnlistmentId && serverEnlistmentId.equals(moduleEnlistmentId));

                        // Server enlistment ID didn't work... try module enlistment ID
                        if (!useSource)
                        {
                            String moduleSourceEnlistmentId = ModuleLoader.getInstance().loadEnlistmentId(sourceDir);
                            useSource = (null != moduleSourceEnlistmentId && moduleSourceEnlistmentId.equals(moduleEnlistmentId));
                        }

                        if (useSource)
                        {
                            _sourceEnlistmentIdMatched = true;
                            return getResourceDirectory(sourceDir);
                        }
                    }
                }
            }
        }

        File exploded = getExplodedPath();

        if (exploded != null && exploded.isDirectory())
            return getResourceDirectory(exploded);

        return null;
    }


    protected File getResourceDirectory(File dir)
    {
        File resourcesDir = new File(dir, "resources");

        // If we have a "resources" directory then look for resources there (Java module layout)
        // If not, treat all top-level directories as resource directories (simple module layout)
        if (resourcesDir.isDirectory())
            return FileUtil.getAbsoluteCaseSensitiveFile(resourcesDir);
        else
            return FileUtil.getAbsoluteCaseSensitiveFile(dir);
    }


    protected Set<String> getDependenciesFromFile()
    {
        Set<String> fileNames = new CaseInsensitiveTreeSet();
        Resource resource = getModuleResource(DEPENDENCIES_FILE_PATH);
        if (resource != null)
        {
            try
            {
                fileNames.addAll(PageFlowUtil.getStreamContentsAsList(resource.getInputStream(), true));
            }
            catch (IOException e)
            {
                _log.error("Problem reading dependencies file for resource " + resource.getName(), e);
            }
        }

        return fileNames;
    }

    @Override
    @NotNull
    public Collection<String> getJarFilenames()
    {
        if (!AppProps.getInstance().isDevMode())
            return Collections.emptySet();

        return getDependenciesFromFile();
    }

    /**
     * List of .jar files that might be produced from module's own source.
     * This default set is not meant to be definitive for every module; not all of these jars
     * exist for all modules, but they are the most common.
     * Some modules may have additional known internal jar artifacts; override and add to the list
     * as needed.
     *
     */
    @NotNull
    protected Collection<String> getInternalJarFilenames()
    {
        return List.of(_name + ".jar", _name + "_api.jar", _name + "_jsp.jar", "schemas.jar");
    }

    protected ApplicationContext _applicationContext = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        if (null != _applicationContext)
            throw new IllegalStateException("already set");
        _applicationContext = applicationContext;
    }


    @JsonIgnore
    public ApplicationContext getApplicationContext()
    {
        return _applicationContext;
    }


    @Override
    public boolean isAutoUninstall()
    {
        return false;
    }

    @Override
    public void addDeferredUpgradeRunnable(String description, Runnable runnable)
    {
        _deferredUpgradeRunnables.add(new Pair<>(description, runnable));
    }

    @Override
    public void runDeferredUpgradeRunnables()
    {
        while (!_deferredUpgradeRunnables.isEmpty())
        {
            Pair<String, Runnable> pair = _deferredUpgradeRunnables.remove();
            try
            {
                ModuleLoader.getInstance().setStartingUpMessage("Running deferred upgrade for module '" + getName() + "': " + pair.first);
                pair.second.run();
            }
            finally
            {
                ModuleLoader.getInstance().setStartingUpMessage(null);
            }
        }
    }

    @Override
    public Set<Module> getResolvedModuleDependencies()
    {
        if (_resolvedModuleDependencies == null)
        {
            Set<Module> modules = new HashSet<>();
            Module module;
            for(String m : getModuleDependenciesAsSet())
            {
                module = ModuleLoader.getInstance().getModule(m);
                if (module != null)
                {
                    modules.add(module);
                    modules.addAll(module.getResolvedModuleDependencies());
                }
            }
            _resolvedModuleDependencies = modules;
        }
        return _resolvedModuleDependencies;
    }

    @Override
    public JSONObject getPageContextJson(ContainerUser context)
    {
        return new JSONObject(getDefaultPageContextJson(context.getContainer()));
    }

    protected @NotNull Map<String, String> getDefaultPageContextJson(Container c)
    {
        Map<String, String> props = new HashMap<>();
        for (ModuleProperty p : getModuleProperties().values())
        {
            if (!p.isExcludeFromClientContext())
                props.put(p.getName(), p.getEffectiveValue(c));
        }
        return props;
    }

    private ModuleXml getModuleXml()
    {
        return MODULE_XML_CACHE.getResourceMap(this);
    }

    @Override
    public Map<String, ModuleProperty> getModuleProperties()
    {
        Map<String, ModuleProperty> map = new LinkedHashMap<>(getModuleXml().getModuleProperties());
        map.putAll(_moduleProperties);

        return map;
    }

    protected void addModuleProperty(ModuleProperty property)
    {
        _moduleProperties.put(property.getName(), property);
    }

    @Override
    @NotNull
    public List<Supplier<ClientDependency>> getClientDependencies(Container c)
    {
        return getModuleXml().getClientDependencySuppliers();
    }

    @Override
    public boolean getRequireSitePermission()
    {
        return getModuleXml().getRequireSitePermission();
    }

    @Override
    public OlapSchemaInfo getOlapSchemaInfo()
    {
        return null;
    }

    @Override
    public String getDatabaseSchemaName(String fullyQualifiedSchemaName)
    {
        return fullyQualifiedSchemaName;
    }

    @Override
    public DbSchema createModuleDbSchema(DbScope scope, String metaDataName, Map<String, SchemaTableInfoFactory> tableInfoFactoryMap)
    {
        return new DbSchema(metaDataName, DbSchemaType.Module, scope, tableInfoFactoryMap, this);
    }

    // for development mode info only
    public final boolean isSourcePathMatched()
    {
        return _sourcePathMatched;
    }

    // for development mode info only
    public final boolean isSourceEnlistmentIdMatched()
    {
        return _sourceEnlistmentIdMatched;
    }

    private boolean _locked = false;

    @Override
    public void lock()
    {
        checkLocked();
        _locked = true;
    }

    void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("Module info setters can only be called in constructor.");
    }

    public void copyPropertiesFrom(DefaultModule from)
    {
        this.setAuthor(from.getAuthor());
        this.setBuildNumber(from.getBuildNumber());
        this.setBuildOS(from.getBuildOS());
        this.setBuildPath(from.getBuildPath());
        this.setBuildTime(from.getBuildTime());
        this.setBuildType(from.getBuildType());
        this.setBuildUser(from.getBuildUser());
        this.setDescription(from.getDescription());
        this.setEnlistmentId(from.getEnlistmentId());
        this.setLabel(from.getLabel());
        this.setLicense(from.getLicense());
        this.setLicenseUrl(from.getLicenseUrl());
        this.setMaintainer(from.getMaintainer());
        this.setOrganization(from.getOrganization());
        this.setOrganizationUrl(from.getOrganizationUrl());
        this.setUrl(from.getUrl());
        this.setVcsBranch(from.getVcsBranch());
        this.setVcsRevision(from.getVcsRevision());
        this.setVcsTag(from.getVcsTag());
        this.setVcsUrl(from.getVcsUrl());
        this.setZippedPath(from.getZippedPath());
    }
}
