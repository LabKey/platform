/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.SqlScriptManager;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.OlapSchemaInfo;
import org.labkey.api.reports.report.ModuleJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryReportDescriptor;
import org.labkey.api.reports.report.ModuleRReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.data.xml.PermissionType;
import org.labkey.moduleProperties.xml.DependencyType;
import org.labkey.moduleProperties.xml.ModuleDocument;
import org.labkey.moduleProperties.xml.ModuleType;
import org.labkey.moduleProperties.xml.PropertyType;
import org.labkey.moduleProperties.xml.RequiredModuleType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: migra
 * Date: Jul 14, 2005
 * Time: 11:42:09 AM
 */
public abstract class DefaultModule implements Module, ApplicationContextAware
{
    public static final String CORE_MODULE_NAME = "Core";

    private static final Logger _log = Logger.getLogger(DefaultModule.class);
    private static final Set<Pair<Class, String>> INSTANTIATED_MODULES = new HashSet<>();
    private static final String XML_FILENAME = "module.xml";
    private static final Cache<Path, ReportDescriptor> REPORT_DESCRIPTOR_CACHE = CacheManager.getCache(CacheManager.UNLIMITED, CacheManager.DAY, "Report descriptor cache");

    private final Queue<Method> _deferredUpgradeTasks = new LinkedList<>();
    private final Map<String, Class<? extends Controller>> _controllerNameToClass = new LinkedHashMap<>();
    private final Map<Class<? extends Controller>, String> _controllerClassToName = new HashMap<>();
    private final Set<String> _moduleDependencies = new CaseInsensitiveHashSet();
    private final Map<String, ModuleProperty> _moduleProperties = new HashMap<>();
    private final LinkedHashSet<ClientDependency> _clientDependencies = new LinkedHashSet<>();

    private Collection<WebPartFactory> _webPartFactories;
    private ModuleResourceResolver _resolver;
    private String _name = null;
    private String _description = null;
    private double _version = 0.0;
    private double _requiredServerVersion = 0.0;
    private String _moduleDependenciesString = null;
    private String _svnRevision = null;
    private String _svnUrl = null;
    private String _buildUser = null;
    private String _buildTime = null;
    private String _buildOS = null;
    private String _buildPath = null;
    private String _sourcePath = null;
    private String _buildNumber = null;
    private String _enlistmentId = null;
    private File _explodedPath = null;
    protected String _resourcePath = null;
    private boolean _requireSitePermission = false;

    protected DefaultModule()
    {
    }

    public int compareTo(@NotNull Module m)
    {
        //sort by name--core module will override to ensure first in sort
        return getName().compareTo(m.getName());
    }

    final public void initialize()
    {
        SupportedDatabase coreType = SupportedDatabase.get(CoreSchema.getInstance().getSqlDialect());
        if (!getSupportedDatabasesSet().contains(coreType))
            throw new DatabaseNotSupportedException("This module does not support " + CoreSchema.getInstance().getSqlDialect().getProductName());

        for (String dsName : ModuleLoader.getInstance().getModuleDataSources(this))
        {
            Throwable t = DbScope.getDataSourceFailure(dsName);

            if (null != t)
                throw new ConfigurationException("This module requires a properly configured data source called \"" + dsName + "\"", t);

            if (null == DbScope.getDbScope(dsName))
                throw new ConfigurationException("This module requires a properly configured data source called \"" + dsName + "\"");
        }

        synchronized (INSTANTIATED_MODULES)
        {
            //simple modules all use the same Java class, so we need to also include
            //the module name in the instantiated modules set
            Pair<Class, String> reg = new Pair<Class,String>(getClass(), getName());
            if (INSTANTIATED_MODULES.contains(reg))
                throw new IllegalStateException("An instance of module " + getClass() +  " with name '" + getName() + "' has already been created. Modules should be singletons");
            else
                INSTANTIATED_MODULES.add(reg);
        }

        ModuleLoader.getInstance().registerResourcePrefix(getResourcePath(), this);

//        _resolver = new ModuleResourceResolver(this, getResourceDirectories(), getResourceClasses());

        init();

        preloadReports();
    }

    Set<Resource> _reportFiles = Collections.synchronizedSet(new TreeSet<>(new Comparator<Resource>(){
        @Override
        public int compare(Resource o, Resource o1)
        {
            return o.getPath().compareTo(o1.getPath());
        }
    }));

    public static final FilenameFilter moduleReportFilter = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return ModuleRReportDescriptor.accept(name) ||
                   name.toLowerCase().endsWith(ModuleJavaScriptReportDescriptor.FILE_EXTENSION);
        }
    };

    protected static final FilenameFilter moduleReportFilterWithQuery = new FilenameFilter(){
        public boolean accept(File dir, String name)
        {
            return moduleReportFilter.accept(dir, name) ||
                    name.toLowerCase().endsWith(ModuleQueryReportDescriptor.FILE_EXTENSION);
        }
    };

    private void preloadReports()
    {
        Resource r = getQueryReportsDir();
        if (null == r || !r.isCollection())
            return;
        _findReports(r);
    }


    private void _findReports(Resource dir)
    {
        for (Resource file : dir.list())
        {
            if (file.isCollection())
                _findReports(file);
            else if (moduleReportFilterWithQuery.accept(null, file.getName()))
                _reportFiles.add(file);
        }
    }

    Resource _queryReportsDir = null;
    protected Resource getQueryReportsDir()
    {
        if (null == _queryReportsDir)
            _queryReportsDir = getModuleResource("reports/schemas");
        return _queryReportsDir;
    }

    public Set<Resource> getReportFiles()
    {
        return _reportFiles;
    }

    protected abstract void init();

    /**
     * Create the WebPartFactories that this module defines in code. File-based webpart factories are handled implicitly.
     *
     * @return A collection of WebPartFactories
     */
    protected abstract @NotNull Collection<? extends WebPartFactory> createWebPartFactories();
    public abstract boolean hasScripts();

    final public void startup(ModuleContext moduleContext)
    {
        Resource xml = getModuleResolver().lookup(Path.parse(XML_FILENAME));
        if (xml != null)
            loadXmlFile(xml);

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
        assert !_controllerNameToClass.values().contains(controllerClass) : "Controller class '" + controllerClass + "' is already registered";
        _controllerClassToName.put(controllerClass, primaryName);
        addControllerName(primaryName, controllerClass);
    }


    // Map all names to controller class
    private void addControllerName(String controllerName, Class<? extends Controller> controllerClass)
    {
        assert null == _controllerNameToClass.get(controllerName) : "Controller name '" + controllerName + "' is already registered";
        _controllerNameToClass.put(controllerName, controllerClass);
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
     * Upgrade each schema in this module to the latest version.
     */
    public void versionUpdate(ModuleContext moduleContext) throws Exception
    {
        if (hasScripts())
        {
            SqlScriptProvider provider = new FileSqlScriptProvider(this);

            for (DbSchema schema : provider.getSchemas())
            {
                SqlScriptManager manager = SqlScriptManager.get(provider, schema);
                List<SqlScript> scripts = manager.getRecommendedScripts(getVersion());

                if (!scripts.isEmpty())
                    SqlScriptRunner.runScripts(this, moduleContext.getUpgradeUser(), scripts);

                SqlScript script = SchemaUpdateType.After.getScript(provider, schema);

                if (null != script)
                    SqlScriptRunner.runScripts(this, null, Arrays.asList(script));
            }
        }
    }

    public void afterUpdate(ModuleContext moduleContext)
    {
    }

    private void runScripts(SchemaUpdateType type)
    {
        try
        {
            if (hasScripts())
            {
                SqlScriptProvider provider = new FileSqlScriptProvider(this);

                for (DbSchema schema : provider.getSchemas())
                {
                    SqlScript script = type.getScript(provider, schema);

                    if (null != script)
                        SqlScriptRunner.runScripts(this, null, Arrays.asList(script));
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error running scripts in module " + getName(), e);
        }
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
                wpf.addAll(Portal.WEB_PART_FACTORY_CACHE.getResources(this));

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

        // Handle modules that have no controllers (e.g., BigIron)
        if (!map.isEmpty())
        {
            Class<? extends Controller> controllerClass = map.values().iterator().next();
            Controller controller = getController(null, controllerClass);
            if (controller instanceof SpringActionController)
            {
                Controller action = ((SpringActionController) controller).getActionResolver().resolveActionName(controller, "begin");
                if (action != null)
                {
                    return new ActionURL(action.getClass(), c);
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
        addWebPart(name, c, location, -1, new HashMap<String, String>());
    }

    protected void addWebPart(String name, Container c, String location, Map<String, String> properties)
    {
        addWebPart(name, c, location, -1, properties);
    }

    protected void addWebPart(String name, Container c, String location, int partIndex)
    {
        addWebPart(name, c, location, partIndex, new HashMap<String, String>());
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

    @Override
    @NotNull
    /**
     * Returns all non-provisioned schemas claimed by the module in {@link:getSchemaNames()}. Override if a different
     * set of schemas should be tested.
     */
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

    @Override
    @NotNull
    public Collection<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<String> getProvisionedSchemaNames()
    {
        return Collections.emptySet();
    }

    protected static final Set<SupportedDatabase> ALL_DATABASES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(SupportedDatabase.mssql, SupportedDatabase.pgsql)));

    private Set<SupportedDatabase> _supportedDatabases = ALL_DATABASES;

    @NotNull
    @Override
    public Set<SupportedDatabase> getSupportedDatabasesSet()
    {
        return _supportedDatabases;
    }


    // Used by Spring configuration reflection
    @SuppressWarnings("UnusedDeclaration")
    public String getSupportedDatabases()
    {
        Set<SupportedDatabase> set = getSupportedDatabasesSet();
        return StringUtils.join(set, ",");
    }


    // Used by Spring configuration reflection
    @SuppressWarnings("UnusedDeclaration")
    public void setSupportedDatabases(String list)
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

    public void setName(String name)
    {
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

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public double getVersion()
    {
        return _version;
    }

    public void setVersion(double version)
    {
        if (0.0 == version)
            return;
        if (0.0 != _version)
        {
            if (_version != version)
                _log.error("Attempt to change version of module from " + _version + " to " + version + ".");
            return;
        }
        _version = version;
    }

    public double getRequiredServerVersion()
    {
        return _requiredServerVersion;
    }

    public void setRequiredServerVersion(double requiredServerVersion)
    {
        if (0.0 != requiredServerVersion)
            _requiredServerVersion = requiredServerVersion;
    }

    public Set<String> getModuleDependenciesAsSet()
    {
        return _moduleDependencies;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setModuleDependencies(String dependencies)
    {
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
    }

    public String getModuleDependencies()
    {
        return _moduleDependenciesString;
    }

    public String getSvnRevision()
    {
        return _svnRevision;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setSvnRevision(String svnRevision)
    {
        _svnRevision = svnRevision;
    }

    public String getSvnUrl()
    {
        return _svnUrl;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setSvnUrl(String svnUrl)
    {
        _svnUrl = svnUrl;
    }

    public String getBuildUser()
    {
        return _buildUser;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setBuildUser(String buildUser)
    {
        _buildUser = buildUser;
    }

    public String getBuildTime()
    {
        return _buildTime;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setBuildTime(String buildTime)
    {
        _buildTime = buildTime;
    }

    public String getBuildOS()
    {
        return _buildOS;
    }

    @SuppressWarnings({"UnusedDeclaration"})
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

    @SuppressWarnings({"UnusedDeclaration"})
    public void setBuildPath(String buildPath)
    {
        _buildPath = buildPath;
    }

    public String getBuildNumber()
    {
        return _buildNumber;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void setBuildNumber(String buildNumber)
    {
        _buildNumber = buildNumber;
    }

    public String getEnlistmentId()
    {
        return _enlistmentId;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setEnlistmentId(String enlistmentId)
    {
        _enlistmentId = enlistmentId;
    }

    public Map<String, String> getProperties()
    {
        Map<String, String> props = new LinkedHashMap<>();

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
        props.put("Source Path", getSourcePath());
        props.put("Build Number", getBuildNumber());
        props.put("Enlistment ID", getEnlistmentId());
        props.put("Module Dependencies", StringUtils.trimToNull(getModuleDependencies()) == null ? "<none>" : getModuleDependencies());

        return props;
    }

    public File getExplodedPath()
    {
        return _explodedPath;
    }

    public void setExplodedPath(File path)
    {
        _explodedPath = path.getAbsoluteFile();
    }

    public Set<String> getSqlScripts(@NotNull DbSchema schema)
    {
        SqlDialect dialect = schema.getSqlDialect();

        String sqlScriptsPath = getSqlScriptsPath(dialect);
        Resource dir = getModuleResource(sqlScriptsPath);
        if (dir == null || !dir.isCollection())
            return Collections.emptySet();

        Set<String> fileNames = new HashSet<>();

        for (String script : dir.listNames())
        {
            // TODO: Ignore case to work around EHR case inconsistencies
            if ( (script.endsWith(".sql") || script.endsWith(".jsp"))  && StringUtils.startsWithIgnoreCase(script, schema.getDisplayName() + "-"))
                fileNames.add(script);
        }

        return fileNames;
    }

    public String getSqlScriptsPath(@NotNull SqlDialect dialect)
    {
        return "schemas/dbscripts/" + dialect.getSQLScriptPath() + "/";
    }

    @Nullable
    public ReportDescriptor getCachedReport(Path path)
    {
        return REPORT_DESCRIPTOR_CACHE.get(new Path("{"+getName()+"}").append(path));
    }

    public void cacheReport(Path path, ReportDescriptor descriptor)
    {
        REPORT_DESCRIPTOR_CACHE.put(new Path("{"+getName()+"}").append(path), descriptor);
    }

    protected void loadXmlFile(Resource r)
    {
        if (r.exists())
        {
            try
            {
                XmlOptions xmlOptions = new XmlOptions();
                Map<String,String> namespaceMap = new HashMap<>();
                namespaceMap.put("", "http://labkey.org/moduleProperties/xml/");
                xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

                ModuleDocument moduleDoc = ModuleDocument.Factory.parse(r.getInputStream(), xmlOptions);
                if (AppProps.getInstance().isDevMode())
                {
                    try
                    {
                        XmlBeansUtil.validateXmlDocument(moduleDoc);
                    }
                    catch (XmlValidationException e)
                    {
                        _log.error("Module XML file failed validation for module: " + getName() + ". Error: " + e.getDetails());
                    }
                }

                ModuleType mt = moduleDoc.getModule();
                if (null != mt && mt.getProperties() != null)
                {
                    for (PropertyType pt : mt.getProperties().getPropertyDescriptorArray())
                    {
                        ModuleProperty mp;
                        if (pt.isSetName())
                            mp = new ModuleProperty(this, pt.getName());
                        else
                            continue;

                        if (pt.isSetCanSetPerContainer())
                            mp.setCanSetPerContainer(pt.getCanSetPerContainer());
                        if (pt.isSetDefaultValue())
                            mp.setDefaultValue(pt.getDefaultValue());
                        if (pt.isSetDescription())
                            mp.setDescription(pt.getDescription());
                        if (pt.isSetEditPermissions() && pt.getEditPermissions() != null && pt.getEditPermissions().getPermissionArray() != null)
                        {
                            List<Class<? extends Permission>> editPermissions = new ArrayList<>();
                            for (PermissionType.Enum permEntry : pt.getEditPermissions().getPermissionArray())
                            {
                                SecurityManager.PermissionTypes perm = SecurityManager.PermissionTypes.valueOf(permEntry.toString());
                                Class<? extends Permission> permClass = perm.getPermission();
                                if (permClass != null)
                                    editPermissions.add(permClass);
                            }

                            if (editPermissions.size() > 0)
                                mp.setEditPermissions(editPermissions);
                        }

                        if (mp.getName() != null)
                            _moduleProperties.put(mp.getName(), mp);
                    }
                }

                if (mt.getClientDependencies() != null && mt.getClientDependencies().getDependencyArray() != null)
                {
                    for (DependencyType rt : mt.getClientDependencies().getDependencyArray())
                    {
                        String path = rt.getPath();
                        if (path != null)
                        {
                            ClientDependency cd = ClientDependency.fromPath(path);
                            if (cd != null)
                                _clientDependencies.add(cd);
                        }
                    }
                }

                if (mt.getRequiredModuleContext() != null && mt.getRequiredModuleContext().getRequiredModuleArray() != null)
                {
                    for (RequiredModuleType rmt : mt.getRequiredModuleContext().getRequiredModuleArray())
                    {
                        if (rmt.getName() != null)
                        {
                            if(rmt.getName().equalsIgnoreCase(getName()))
                                _log.error("Module " + getName() + " lists itself as a dependency in module.xml");
                            else
                                _clientDependencies.add(ClientDependency.fromModuleName(rmt.getName()));
                        }
                    }
                }

                if (mt.getEnableOptions() != null && mt.getEnableOptions().isSetRequireSitePermission())
                {
                    _requireSitePermission = true;
                }
            }
            catch(Exception e)
            {
                _log.error("Error trying to read and parse the metadata XML for module " + getName() + " from " + r.getPath(), e);
            }
        }
    }

    @NotNull
    @Override
    public Set<? extends ModuleResourceLoader> getResourceLoaders()
    {
        return Collections.emptySet();
    }

    @Override
    public Resolver getModuleResolver()
    {
        if (_resolver == null)
            _resolver = new ModuleResourceResolver(this, getResourceDirectories(), getResourceClasses());

        return _resolver;
    }

    @Override
    public Resource getModuleResource(Path path)
    {
        return getModuleResolver().lookup(path);
    }

    public Resource getModuleResource(String path)
    {
        return getModuleResource(Path.parse(path));
    }

    public InputStream getResourceStream(String path) throws IOException
    {
        Resource r = getModuleResource(path);
        if (r != null && r.isFile())
            return r.getInputStream();
        return null;
    }

    @Override
    public String toString()
    {
        return getName() + " " + getVersion() + " " + super.toString();
    }


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
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No LabKey Server controller registered to handle request: " + url.getController());
            return;
        }

        ViewContext rootContext = new ViewContext(request, response, url);

        try
        {
            stackSize = HttpView.getStackSize();

            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Expires", "Sun, 01 Jan 2000 00:00:00 GMT");

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

    public Controller getController(HttpServletRequest request, String name)
    {
        Class cls = _controllerNameToClass.get(name);
        if (null == cls)
            return null;

        return getController(request, cls);
    }


    public Controller getController(@Nullable HttpServletRequest request, Class cls)
    {
        try
        {
            return (Controller)cls.newInstance();
        }
        catch (IllegalAccessException | InstantiationException x)
        {
            throw new RuntimeException(x);
        }
    }

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

    @NotNull
    protected Class[] getResourceClasses()
    {
        return new Class[] { this.getClass() };
    }

    @NotNull
    protected List<File> getResourceDirectories()
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
                    String moduleEnlistmentId = getEnlistmentId();

                    if (null != moduleEnlistmentId)
                    {
                        String serverEnlisthmentId = AppProps.getInstance().getEnlistmentId();
                        boolean useSource = (null != serverEnlisthmentId && serverEnlisthmentId.equals(moduleEnlistmentId));

                        // Server enlistment ID didn't work... try module enlistment ID
                        if (!useSource)
                        {
                            String moduleSourceEnlistmentId = ModuleLoader.getInstance().loadEnlistmentId(sourceDir);
                            useSource = (null != moduleSourceEnlistmentId && moduleSourceEnlistmentId.equals(moduleEnlistmentId));
                        }

                        if (useSource)
                            return getResourceDirectory(sourceDir);
                    }
                }
            }
        }

        File exploded = getExplodedPath();

        if (exploded != null && exploded.isDirectory())
            return getResourceDirectory(exploded);

        return Collections.emptyList();
    }


    private List<File> getResourceDirectory(File dir)
    {
        File resourcesDir = new File(dir, "resources");

        // If we have a "resources" directory then look for resources there (Java module layout)
        // If not, treat all top-level directories as resource directories (simple module layout)
        if (resourcesDir.isDirectory())
            return Collections.singletonList(FileUtil.getAbsoluteCaseSensitiveFile(resourcesDir));
        else
            return Collections.singletonList(FileUtil.getAbsoluteCaseSensitiveFile(dir));
    }


    @Override
    public @Nullable Collection<String> getJarFilenames()
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        File lib = new File(AppProps.getInstance().getProjectRoot(), "server/modules/" + getName() + "/lib");

        if (!lib.exists())
            return null;

        Set<String> filenames = new CaseInsensitiveTreeSet();

        filenames.addAll(Arrays.asList(lib.list(getJarFilenameFilter())));

        return filenames;
    }

    protected FilenameFilter getJarFilenameFilter()
    {
        return new FilenameFilter() {
            public boolean accept(File dir, String name)
            {
                return isRuntimeJar(name);
            }
        };
    }

    public static boolean isRuntimeJar(String name)
    {
        return name.endsWith(".jar") && !name.endsWith("javadoc.jar") && !name.endsWith("sources.jar");
    }

    protected ApplicationContext _applicationContext = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        if (null != _applicationContext)
            throw new IllegalStateException("already set");
        _applicationContext = applicationContext;
    }


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
    public void addDeferredUpgradeTask(Method task)
    {
        _deferredUpgradeTasks.add(task);
    }

    @Override
    public void runDeferredUpgradeTasks(ModuleContext context)
    {
        while (!_deferredUpgradeTasks.isEmpty())
        {
            Method task = _deferredUpgradeTasks.remove();
            try
            {
                ModuleLoader.getInstance().setStartingUpMessage("Running deferred upgrade for module '" + context.getName() + "': " + task.getName());
                task.invoke(getUpgradeCode(), context);
            }
            catch (ReflectiveOperationException e)
            {
                throw new UnexpectedException(e);
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
        Set<Module> modules = new HashSet<>();
        Module module;
        for(String m : getModuleDependenciesAsSet())
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

    public Map<String, ModuleProperty> getModuleProperties()
    {
        return _moduleProperties;
    }

    protected void addModuleProperty(ModuleProperty property)
    {
        _moduleProperties.put(property.getName(), property);
    }

    public @NotNull JSONObject getPageContextJson(ViewContext context)
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

    @NotNull
    public LinkedHashSet<ClientDependency> getClientDependencies(Container c)
    {
        return _clientDependencies;
    }

    @Override
    public boolean getRequireSitePermission()
    {
        return _requireSitePermission;
    }

    @Override
    public OlapSchemaInfo getOlapSchemaInfo()
    {
        return null;
    }

    @Override
    public DbSchema createModuleDbSchema(DbScope scope, String metaDataName, Map<String, String> metaDataTableNames)
    {
        return new DbSchema(metaDataName, DbSchemaType.Module, scope, metaDataTableNames);
    }
}
