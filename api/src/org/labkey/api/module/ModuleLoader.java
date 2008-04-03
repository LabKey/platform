/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
import org.apache.tomcat.dbcp.dbcp.BasicDataSource;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.*;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.*;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.naming.*;
import javax.servlet.Filter;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * User: migra
 * Date: Jul 13, 2005
 * Time: 10:27:45 AM
 */
public class ModuleLoader implements Filter
{
    private static ModuleLoader _instance = null;
    private static Logger _log = Logger.getLogger(ModuleLoader.class);
    private boolean upgradeComplete = false;
    private boolean _deferUsageReport = false;
    private User upgradeUser = null;
    private boolean express = false;
    private static Throwable _startupFailure = null;
    private static Map<String, Throwable> _moduleFailures = new HashMap<String, Throwable>();
    private static boolean _newInstall = false;
    private static final Map<String, Module> _pageFlowToModule = new HashMap<String, Module>();
    private static final Map<Package, String> _packageToPageFlowURL = new HashMap<Package, String>();
    private static final Map<String, Module> _schemaNameToModule = new HashMap<String, Module>();
    private static final Map<String, Module> _resourcePrefixToModule = new HashMap<String, Module>();
    private static final Map<Class, Class<? extends UrlProvider>> _urlProviderToImpl = new HashMap<Class, Class<? extends UrlProvider>>();
    private static CoreSchema _core = CoreSchema.getInstance();

    private boolean _startupComplete = false;
    private static final Object _startupLock = new Object();

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
    private SortedMap<String,FolderType> _folderTypes = new TreeMap<String,FolderType>(new FolderTypeComparator());
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
        assert null == _instance : "Should be only one instance of module manager";
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
            _startupFailure = t;
            _log.error("Failure ocurred during ModuleLoader init.", t);
        }
    }


    ServletContext _servletContext = null;
    
    public static ServletContext getServletContext()
    {
        return _instance._servletContext;
    }

    private void doInit(ServletContext servletCtx) throws Exception
    {
        _servletContext = servletCtx;

        _log.debug("ModuleLoader init");

        // Start up a thread that lets us hit a breakpoint in the debugger, even if
        // all the real working threads are hung. This lets us invoke methods in the debugger,
        // gain easier access to statics, etc.
        new BreakpointThread().start();

        verifyJavaVersion();

        // Register BeanUtils converters
        ConvertHelper.registerHelpers();

        List<Module> moduleList = new ArrayList<Module>();

        File webappRoot = new File(servletCtx.getRealPath(".")).getCanonicalFile();

        Set<File> unclaimedFiles = listCurrentFiles(webappRoot);

        removeAPIFiles(unclaimedFiles, webappRoot);

        extractModules(servletCtx, moduleList, unclaimedFiles);

        File webinfDir = new File(webappRoot, "WEB-INF");
        File webinfLibDir = new File(webinfDir, "lib");
        // Clean up any old files that might be from modules that are no
        // longer part of this installation
        for (File unclaimedFile : unclaimedFiles)
        {
            if (!unclaimedFile.getParentFile().equals(webinfLibDir))
            {
                FileUtil.deleteDir(unclaimedFile);
            }
        }

        ModuleDependencySorter sorter = new ModuleDependencySorter();
        moduleList = sorter.sortModulesByDependencies(moduleList);

        _modules = Collections.unmodifiableList(moduleList);

        for (Module module : _modules)
            moduleMap.put(module.getName(), module);

        ensureDataBases();

        if (getTableInfoModules().getTableType() == TableInfo.TABLE_TYPE_NOT_IN_DB)
            _newInstall = true;

        upgradeCoreModule();

        ModuleContext[] contexts = Table.select(getTableInfoModules(), Table.ALL_COLUMNS, null, null, ModuleContext.class);

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

        _log.info("LabKey Server startup is complete");
    }

    private void verifyJavaVersion() throws ServletException
    {
        String javaVersion = System.getProperties().getProperty("java.specification.version");

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

    private Module createModule(String moduleClassName, ClassLoader classLoader, Map<String, String> metaData)
    {
        if (moduleClassName == null || moduleClassName.length() == 0)
            return null;
        try
        {
            Class<Module> clazz = (Class<Module>)classLoader.loadClass(moduleClassName);

            Module result = clazz.newInstance();
            result.setMetaData(metaData);
            return result;
        }
        catch (ClassNotFoundException e)
        {
            _log.error("Unable to instantiate module " + moduleClassName, e);
            _moduleFailures.put(moduleClassName, e);
        }
        catch (IllegalAccessException e)
        {
            _log.error("Unable to instantiate module " + moduleClassName, e);
            _moduleFailures.put(moduleClassName, e);
        }
        catch (InstantiationException e)
        {
            _log.error("Unable to instantiate module " + moduleClassName, e);
            _moduleFailures.put(moduleClassName, e);
        }
        catch (Throwable t)
        {
            _log.error("Unable to instantiate module "+ moduleClassName, t);
            _moduleFailures.put(moduleClassName, t);
        }
        return null;
    }

    private void extractModules(ServletContext context, List<Module> moduleList, Set<File> unclaimedFiles) throws ServletException, IllegalAccessException, InstantiationException, ClassNotFoundException
    {
        File deploymentFile;
        try
        {
            deploymentFile = copyWSDD(context.getRealPath("/WEB-INF"));
        }
        catch (IOException e)
        {
            throw new ServletException(e);
        }
        unclaimedFiles.remove(deploymentFile);

        File webappContentDir;
        File webInfJspDir;
        File webInfClassesDir;
        try
        {
            webappContentDir = new File(context.getRealPath("")).getCanonicalFile();
            webInfJspDir = new File(context.getRealPath("WEB-INF/jsp")).getCanonicalFile();
            webInfClassesDir = new File(context.getRealPath("WEB-INF/classes")).getCanonicalFile();
        }
        catch (IOException e)
        {
            throw new ServletException(e);
        }

        Set<File> moduleFiles;
        try
        {
            ClassLoader webappClassLoader = getClass().getClassLoader();
            Method m = webappClassLoader.getClass().getMethod("getModuleFiles");
            moduleFiles = (Set<File>)m.invoke(webappClassLoader);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException("Could not find getModuleFiles() method - you probably need to copy labkeyBootstrap.jar into $CATALINA_HOME/server/lib and/or edit your labkey.xml to include <Loader loaderClass=\"org.labkey.bootstrap.LabkeyServerBootstrapClassLoader\" />", e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }

        for (File moduleFile : moduleFiles)
        {
            JarFile jarFile = null;
            try
            {
                jarFile = new JarFile(moduleFile);
                JarEntry moduleEntry = jarFile.getJarEntry("META-INF/MANIFEST.MF");
                if (moduleEntry != null)
                {
                    Properties props = new Properties();
                    InputStream in = null;
                    try
                    {
                        in = jarFile.getInputStream(moduleEntry);
                        // we cannot use "props.load(in);" as long paths get wrapped and we will get wrong value
                        Manifest manifest=new Manifest(in);
                        for(Map.Entry<Object, Object> entry : manifest.getMainAttributes().entrySet()) {
                            props.put(entry.getKey().toString(),entry.getValue().toString());
                        }
                    }
                    finally
                    {
                        if (in != null) { try { in.close(); } catch (IOException e) {} }
                    }

                    String moduleClassName = props.getProperty("ModuleClass");
                    String buildPath = props.getProperty("BuildPath");

                    if (null != buildPath)
                    {
                        buildPath = buildPath.replaceAll("\\\\\\\\","\\\\");
                        // Would be nice to avoid this, if we could get ANT to store an absolute path in module.properties
                        String absolutePath = new File(buildPath).getAbsolutePath();
                        props.setProperty("BuildPath", absolutePath);
                    }

                    Map<String, String> propsMap = new HashMap(props);

                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements())
                    {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName().toLowerCase();
                        if (!name.startsWith("meta-inf/"))
                        {
                            extractEntry(jarFile, entry, entry.getName(), webappContentDir, unclaimedFiles);
                        }
                        else if (name.startsWith("meta-inf/_pageflow/"))
                        {
                            extractEntry(jarFile, entry, entry.getName().substring("meta-inf/".length()), webInfClassesDir, unclaimedFiles);
                        }
                        else if (name.equals("meta-inf/deploy.wsdd"))
                        {
                            appendWebService(jarFile, entry, deploymentFile);
                        }
                        else if (name.startsWith("meta-inf/jsp/") && name.endsWith("_jsp.jar"))
                        {
                            extractEntry(jarFile, entry, entry.getName().substring("meta-inf/jsp/".length()), webInfJspDir, unclaimedFiles);
                        }
                    }

                    Module module = createModule(moduleClassName, getClass().getClassLoader(), propsMap);
                    if (module == null)
                    {
                        _log.error("Unable to determine module information for file: " + moduleFile);
                    }
                    else
                    {
                        moduleList.add(module);
                    }

                }
            }
            catch (IOException e)
            {
                _log.error("Could not extract module data", e);
                throw new ServletException(e);
            }
            finally
            {
                if (jarFile != null) { try { jarFile.close(); } catch (IOException e) {} }
            }
        }
    }

    private File copyWSDD(String realPath) throws IOException
    {
        FileInputStream fIn = null;
        FileOutputStream fOut = null;
        File originalFile = new File(realPath, "server-config-original.wsdd");
        File copyFile = new File(realPath, "server-config.wsdd");
        try
        {
            fIn = new FileInputStream(originalFile);
            fOut = new FileOutputStream(copyFile);
            byte[] b = new byte[4096];
            int i;
            while ((i = fIn.read(b)) != -1)
            {
                fOut.write(b, 0, i);
            }
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
            if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
        }
        return copyFile;
    }

    private void appendWebService(JarFile jarFile, JarEntry entry, File deploymentFile) throws ServletException
    {
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder moduleBuilder = factory.newDocumentBuilder();
            Document moduleDocument = moduleBuilder.parse(jarFile.getInputStream(entry));
            NodeList moduleDeploymentNodes = moduleDocument.getElementsByTagName("deployment");

            if (moduleDeploymentNodes.getLength() == 1)
            {
                Document serverDocument = moduleBuilder.parse(deploymentFile);
                NodeList serverDeploymentNodes = serverDocument.getElementsByTagName("deployment");
                if (serverDeploymentNodes.getLength() == 1)
                {
                    Element serverDeploymentNode = (Element)serverDeploymentNodes.item(0);

                    Element deploymentNode = (Element)moduleDeploymentNodes.item(0);
                    NodeList nodesToCopy = deploymentNode.getElementsByTagName("service");
                    for (int i = 0; i < nodesToCopy.getLength(); i++)
                    {
                        Node n = nodesToCopy.item(i);
                        Node newNode = serverDocument.importNode(n, true);
                        serverDeploymentNode.appendChild(newNode);
                    }
                }

                Source source = new DOMSource(serverDocument);

                FileOutputStream fOut = null;
                try
                {
                    fOut = new FileOutputStream(deploymentFile);
                    Result result = new StreamResult(fOut);

                    // Write the DOM document to the file
                    Transformer xformer = TransformerFactory.newInstance().newTransformer();
                    xformer.transform(source, result);
                }
                finally
                {
                    if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
                }
            }
        }
        catch (TransformerException e)
        {
            throw new ServletException(e);
        }
        catch (IOException e)
        {
            throw new ServletException(e);
        }
        catch (ParserConfigurationException e)
        {
            throw new ServletException(e);
        }
        catch (SAXException e)
        {
            throw new ServletException(e);
        }
    }

    private void extractEntry(JarFile jarFile, JarEntry entry, String directory, File destinationDirectory, Set<File> unclaimedFiles) throws IOException
    {
        InputStream in = jarFile.getInputStream(entry);
        File targetFile = new File(destinationDirectory, directory);
        unclaimedFiles.remove(targetFile);

        if (targetFile.exists() && targetFile.isDirectory() != entry.isDirectory())
        {
            FileUtil.deleteDir(targetFile);
        }

        if (!targetFile.exists() ||
            entry.getTime() == -1 ||
            entry.getTime() > (targetFile.lastModified() + 2000) ||
            entry.getTime() < (targetFile.lastModified() - 2000) ||
            entry.getSize() == -1 ||
            entry.getSize() != targetFile.length())
        {
            if (entry.isDirectory())
            {
                targetFile.mkdirs();
            }
            else
            {
                BufferedInputStream bIn = null;
                BufferedOutputStream bOut = null;
                try
                {
                    File targetFileParent = targetFile.getParentFile();
                    if (!targetFileParent.isDirectory() && !targetFileParent.mkdirs())
                    {
                        throw new IOException("Unable to create directory " + targetFileParent);
                    }

                    bIn = new BufferedInputStream(in);

                    FileOutputStream fOut = new FileOutputStream(targetFile);
                    bOut = new BufferedOutputStream(fOut);

                    byte[] bytes = new byte[4096];
                    int i;
                    while ((i = bIn.read(bytes)) != -1)
                    {
                        bOut.write(bytes, 0, i);
                    }
                }
                finally
                {
                    if (bIn != null) { try { bIn.close(); } catch (IOException e) {} }
                    if (bOut != null) { try { bOut.close(); } catch (IOException e) {} }
                }
                if (entry.getTime() != -1)
                {
                    targetFile.setLastModified(entry.getTime());
                }
            }
        }
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
                BasicDataSource ds = (BasicDataSource) o.getObject();
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
    private boolean ensureDataBase(BasicDataSource ds, String dsName) throws ServletException
    {
        Connection conn = null;

        // Need the dialect to:
        // 1) determine whether an exception is "no database" or something else and
        // 2) get the name of the "master" database
        //
        // Only way to get the right dialect is to look up based on the driver class name.
        SqlDialect dialect = SqlDialect.getFromDriverClassName(ds.getDriverClassName());

        SQLException lastException = null;

        // Attempt a connection three times before giving up
        for (int i = 0; i < 3; i++)
        {
            if (i > 0)
            {
                _log.error("Retrying connection to \"" + dsName + "\" at " + ds.getUrl() + " in 10 seconds");

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
                Class.forName(ds.getDriverClassName());
                // Create non-pooled connection... don't want to pool a failed connection
                conn = DriverManager.getConnection(ds.getUrl(), ds.getUsername(), ds.getPassword());
                _log.debug("Successful connection to \"" + dsName + "\" at " + ds.getUrl());
                return true;        // Database already exists
            }
            catch (SQLException e)
            {
                if (dialect.isNoDatabaseException(e))
                {
                    createDataBase(ds, dialect);
                    return false;   // Successfully created database
                }
                else
                {
                    _log.error("Connection to \"" + dsName + "\" at " + ds.getUrl() + " failed with the following error:");
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

        _log.error("Attempted to connect three times... giving up.");
        throw(new ServletException("Can't connect to datasource \"" + dsName + "\", be sure that webapp configuration xml is configured correctly for your database and that the database server is running.", lastException));
    }


    private void createDataBase(BasicDataSource ds, SqlDialect dialect) throws ServletException
    {
        Connection conn = null;
        PreparedStatement stmt = null;

        String dbName = SqlDialect.getDatabaseName(ds);

        _log.info("Attempting to create database \"" + dbName + "\"");

        String masterUrl = StringUtils.replace(ds.getUrl(), dbName, dialect.getMasterDataBaseName());

        try
        {
            conn = DriverManager.getConnection(masterUrl, ds.getUsername(), ds.getPassword());
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
    private void upgradeCoreModule()
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
            coreContext = Table.selectObject(getTableInfoModules(), "Core", ModuleContext.class);

        // Does the core module need to be upgraded?
        if (coreContext.getInstalledVersion() == coreModule.getVersion())
            return;

        if (coreContext.getInstalledVersion() == 0.0)
        {
            _log.debug("Initializing core module to " + coreModule.getFormattedVersion());
            coreModule.bootstrap();
        }
        else
            _log.debug("Upgrading core module from " + ModuleContext.formatVersion(coreContext.getInstalledVersion()) + " to " + coreModule.getFormattedVersion());

        coreModule.versionUpdate(coreContext, null);
        coreContext.upgradeComplete(coreModule.getVersion());
    }


    public Throwable getStartupFailure()
    {
        return _startupFailure;
    }

    public Map<String, Throwable> getModuleFailures()
    {
        if (_moduleFailures.size() == 0)
        {
            return Collections.emptyMap();
        }
        else
        {
            return new HashMap(_moduleFailures);
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
        HttpServletResponse originalResponse = (HttpServletResponse) servletResponse;
        HttpServletResponse response = new SafeFlushResponseWrapper(originalResponse);
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        // TODO: get rid of getStartupFailureURL, startupFailure action, startupErrorRequest

        if (getStartupFailure() != null)
        {
            ExceptionUtil.handleException(request, response, getStartupFailure(), null, true);
            return;
        }

        User user = (User)request.getUserPrincipal();
        if (isAdminOnlyMode())
        {
            if (user.isGuest() && !isAdminURL(request))
            {
                String current = request.getRequestURL().toString();
                ActionURL currentUrl = (null == current ? null : new ActionURL(current));
                ActionURL redirect = AuthenticationManager.getLoginURL(currentUrl);
                response.sendRedirect(redirect.toString());
                return;
            }
            else if (!user.isAdministrator() && !isAdminURL(request))
            {
                int stackSize = HttpView.getStackSize();
                try
                {
                    ActionURL url = new ActionURL("admin", "maintenance", "");
                    response.sendRedirect(url.toString());
                    return;
                }
                catch (Exception x)
                {
                    throw new ServletException(x);
                }
                finally
                {
                    HttpView.resetStackSize(stackSize);
                }
            }
        }

        if (isUpgradeRequired())
        {
            _deferUsageReport = true;
            //Let admin pages through. Otherwise go back to upgrade URL
            if (isAdminURL(request))
                filterChain.doFilter(servletRequest, servletResponse);
            else
                response.sendRedirect(getMainUpgradeURL());

            return;
        }

        ensureStartupComplete();

        filterChain.doFilter(servletRequest, response);

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

    public String getMainUpgradeURL()
    {
        return ActionURL.toPathString("admin", "moduleStatus", "");
    }

    private String getStartupFailureURL()
    {
        return ActionURL.toPathString("admin", "startupFailure", "");
    }

    public boolean isStartupComplete()
    {
        return _startupComplete;
    }

    private void ensureStartupComplete()
    {
        synchronized (_startupLock)
        {
            if (_startupComplete)
                return;

            if (isUpgradeRequired())
                throw new IllegalStateException("Can't start modules before upgrade is complete");

            for (Module m : _modules)
            {
                try
                {
                    m.startup(getModuleContext(m));
                }
                catch (Throwable x)
                {
                    if (_startupFailure == null)
                        _startupFailure = x;
                    _log.error("Failure starting module: " + m.getName(), x);
                }
            }

            ContextListener.moduleStartuComplete(_servletContext);

            _startupComplete = true;
        }
    }

    private boolean isAdminURL(HttpServletRequest request)
    {
        String uri = request.getRequestURI().toLowerCase();
        return uri.matches(".*admin.*") || uri.matches(".*login.*") || uri.matches(".*\\Qstylesheet.view\\E");
    }

    ModuleContext saveModuleContext(ModuleContext context)
    {
        try
        {
            ModuleContext stored = Table.selectObject(getTableInfoModules(), context.getName(), ModuleContext.class);
            if (null == stored)
                context = Table.insert(null, getTableInfoModules(), context);
            else
                context = Table.update(null, getTableInfoModules(), context, context.getName(), null);
        }
        catch (SQLException x)
        {
            _log.error("Couldn't save module context.", x);
        }
        return context;
    }


    /**
     * Set the current user to the user upgrading. if another
     * user is already upgrading that User will be returned.
     * <p/>
     * UpgradeUser must be set before attempting to upgrade modules
     * <p/>
     * Clients should check to makes sure that the current user
     * equals the returned user. If not then someone has already started upgrading.
     *
     * @param user  Current user who wants to start upgrade
     * @param force Force current user to take over upgrade even if someone else started it
     * @return user who is actually upgrading. If force==false may not be requested user
     */
    synchronized public User setUpgradeUser(User user, boolean force)
    {
        if (null == upgradeUser || force)
            upgradeUser = user;

        return upgradeUser;
    }

    public User getUpgradeUser()
    {
        return upgradeUser;
    }

    public boolean isUpgradeRequired()
    {
        if (upgradeComplete)
            return false;

        for (Module m : _modules)
        {
            ModuleContext ctx = getModuleContext(m);
            if (ctx.getInstalledVersion() != m.getVersion())
                return true;
        }

        upgradeComplete = true;
        upgradeUser = null;
        return false;
    }

    // Did this server start up with no modules installed?  If so, it's a new install.  This lets us tailor the
    // module upgrade UI to "install" or "upgrade," as appropriate.
    public boolean isNewInstall()
    {
        return _newInstall;
    }

    public boolean isUpgrading()
    {
        return !upgradeComplete && null != upgradeUser;
    }

    /**
     * Get the next module that requires upgrading.
     * <p/>
     * Must call setUpgradeUser before doing this.
     *
     * @return Next upgradeable module. Or null if no module to upgrade
     */
    public Module getNextUpgrade()
    {
        if (upgradeUser == null)
            throw new IllegalStateException("Must set upgrade user before starting upgrade");

        for (Module m : _modules)
        {
            ModuleContext ctx = getModuleContext(m);
            if (ctx.getInstalledVersion() != m.getVersion())
                return m;
        }

        upgradeComplete = true;
        upgradeUser = null;
        return null;
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

    public boolean getExpress()
    {
        return express;
    }

    public void setExpress(boolean express)
    {
        this.express = express;
    }

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
                for (Map.Entry<String, Class> entry : module.getPageFlowNameToClass().entrySet())
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
        return _folderTypes.get(name);
    }

    synchronized public void registerFolderType(FolderType folderType)
    {
        _folderTypes.put(folderType.getName(), folderType);
    }

    public Collection<FolderType> getFolderTypes()
    {
        return _folderTypes.values();
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
}
