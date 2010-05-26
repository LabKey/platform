/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.collections.TTLCacheMap;
import org.labkey.api.util.Path;
import org.labkey.data.xml.TablesDocument;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;

/**
 * Class that wraps a datasource and is shared amongst instances of the
 * DbSchema that need to share the datasource.
 * User: migra
 * Date: Nov 16, 2005
 * Time: 10:20:54 AM
 */
public class DbScope
{
    private static final Logger _log = Logger.getLogger(DbScope.class);
    private static final ConnectionMap _initializedConnections = newConnectionMap();
    private static final Map<String, DbScope> _scopes = new LinkedHashMap<String, DbScope>();
    private static DbScope _labkeyScope = null;

    private String _dsName;
    private DataSource _dataSource;
    private SqlDialect _dialect;
    private String _databaseName;          // Possibly null, e.g., for SAS datasources
    private String _URL;
    private String _databaseProductName;
    private String _databaseProductVersion;
    private String _driverName;
    private String _driverVersion;

    private final HashMap<String, DbSchema> _loadedSchemas = new HashMap<String, DbSchema>();

    private static final ThreadLocal<Transaction> _transaction = new ThreadLocal<Transaction>();


    protected DbScope()
    {
    }


    private DbScope(String dsName, DataSource dataSource) throws ServletException, SQLException
    {
        Connection conn = null;

        try
        {
            conn = dataSource.getConnection();
            DatabaseMetaData dbmd = conn.getMetaData();

            try
            {
                _dialect = SqlDialect.getFromMetaData(dbmd);
            }
            finally
            {
                // Always log the attempt, even if DatabaseNotSupportedException, etc. occurs, to help with diagnosis      
                _log.info("Initializing DbScope with the following configuration:" +
                                            "\n    DataSource Name:          " + dsName +
                                            "\n    Server URL:               " + dbmd.getURL() +
                                            "\n    Database Product Name:    " + dbmd.getDatabaseProductName() +
                                            "\n    Database Product Version: " + dbmd.getDatabaseProductVersion() +
                                            "\n    JDBC Driver Name:         " + dbmd.getDriverName() +
                                            "\n    JDBC Driver Version:      " + dbmd.getDriverVersion() +
                        (null != _dialect ? "\n    SQL Dialect:              " + _dialect.getClass().getSimpleName() : ""));
            }

            _dsName = dsName;
            _dataSource = dataSource;
            _databaseName = _dialect.getDatabaseName(_dsName, _dataSource);
            _URL = dbmd.getURL();
            _databaseProductName = dbmd.getDatabaseProductName();
            _databaseProductVersion = dbmd.getDatabaseProductVersion();
            _driverName = dbmd.getDriverName();
            _driverVersion = dbmd.getDriverVersion();
        }
        finally
        {
            if (null != conn)
            {
                try
                {
                    conn.close();
                }
                catch (SQLException e)
                {
                    // ignore
                }
            }
        }
    }


    public String toString()
    {
        return getDataSourceName();
    }


    public String getDataSourceName()
    {
        return _dsName;
    }

    // Strip off "DataSource" to create friendly name.  TODO: Add UI to allow site admin to add friendly name to each data source.
    public String getDisplayName()
    {
        if (_dsName.endsWith("DataSource"))
            return _dsName.substring(0, _dsName.length() - 10);
        else
            return _dsName;
    }

    public DataSource getDataSource()
    {
        return _dataSource;
    }

    public String getDatabaseName()
    {
        return _databaseName;
    }

    public String getURL()
    {
        return _URL;
    }

    public String getDatabaseProductName()
    {
        return _databaseProductName;
    }

    public String getDatabaseProductVersion()
    {
        return _databaseProductVersion;
    }

    public String getDriverName()
    {
        return _driverName;
    }

    public String getDriverVersion()
    {
        return _driverVersion;
    }

    public Connection beginTransaction() throws SQLException
    {
        if (isTransactionActive())
            throw new IllegalStateException("Existing transaction");

        Connection conn = _getConnection(null);
        conn.setAutoCommit(false);
        _transaction.set(new Transaction(conn));
        return conn;
    }


    public void commitTransaction() throws SQLException
    {
        Transaction t = _transaction.get();
        assert null != t;
        Connection conn = t.getConnection();
        conn.commit();
        conn.setAutoCommit(true);
        conn.close();
        _transaction.remove();
        t.runCommitTasks();
    }


    public void rollbackTransaction()
    {
        Transaction t = _transaction.get();
        assert null != t;
        Connection conn = t.getConnection();
        try
        {
            conn.rollback();
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        try
        {
            conn.setAutoCommit(true);
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        try
        {
            conn.close();
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        _transaction.remove();
        t.clearCommitTasks();
    }


    public boolean isTransactionActive()
    {
        return getCurrentTransaction() != null;
    }


    public Transaction getCurrentTransaction()
    {
        return _transaction.get();
    }


    public Connection getConnection() throws SQLException
    {
        return getConnection(null);
    }

    public Connection getConnection(Logger log) throws SQLException
    {
        Transaction t = _transaction.get();

        if (null == t)
            return _getConnection(log);
        else
            return t.getConnection();
    }


    static Class classDelegatingConnection = null;
    static Method methodGetInnermostDelegate = null;
    static boolean isDelegating = false;
    static boolean isDelegationInitialized;
    private static final Object delegationLock = new Object();


    private static void ensureDelegation(Connection conn)
    {
        synchronized (delegationLock)
        {
            if (isDelegationInitialized)
                return;

            try
            {
                classDelegatingConnection = conn.getClass();
                methodGetInnermostDelegate = classDelegatingConnection.getMethod("getInnermostDelegate");

                while (true)
                {
                    try
                    {
                        // Test the method to make sure we can access it
                        Connection test = (Connection)methodGetInnermostDelegate.invoke(conn);
                        isDelegating = true;
                        return;
                    }
                    catch (Exception e)
                    {
                        // Probably an IllegalAccessViolation -- ignore
                    }

                    // Try the superclass
                    classDelegatingConnection = classDelegatingConnection.getSuperclass();
                    methodGetInnermostDelegate = classDelegatingConnection.getMethod("getInnermostDelegate");
                }
            }
            catch (Exception x)
            {
                _log.info("Could not find class DelegatingConnection", x);
            }
            finally
            {
                isDelegationInitialized = true;
            }
        }
    }


    private static Connection getDelegate(Connection conn)
    {
        Connection delegate = null;

        ensureDelegation(conn);

        if (isDelegating && classDelegatingConnection.isAssignableFrom(conn.getClass()))
        {
            try
            {
                delegate = (Connection)methodGetInnermostDelegate.invoke(conn);
            }
            catch (Exception x)
            {
                _log.error("Unexpected error", x);
            }
        }
        if (null == delegate)
            delegate = conn;
        return delegate;
    }

    
    public Class getDelegateClass()
    {
        try
        {
            Connection conn = _dataSource.getConnection();
            Connection delegate = getDelegate(conn);
            conn.close();
            return delegate.getClass();
        }
        catch (Exception x)
        {
            return null;
        }
    }


    Integer spidUnknown = -1;

    protected Connection _getConnection(Logger log) throws SQLException
    {
        Connection conn = _dataSource.getConnection();

        //
        // Handle one time per-connection setup
        // relies on pool implementation reusing same connection/wrapper instances
        //

        Connection delegate = getDelegate(conn);
        Integer spid = _initializedConnections.get(delegate);

        if (null == spid)
        {
            if (null != _dialect)
            {
                _dialect.initializeConnection(conn);
                spid = _dialect.getSPID(delegate);
            }

            _initializedConnections.put(delegate, spid == null ? spidUnknown : spid);
        }

        return new ConnectionWrapper(conn, _dialect, spid, log);
    }


    public void releaseConnection(Connection conn) throws SQLException
    {
        Transaction t = getCurrentTransaction();

        if (null != t)
            assert t.getConnection() == conn; //Should release same conn we handed out
        else
            conn.close();
    }


    public void closeConnection()
    {
        Transaction t = _transaction.get();
        if (null != t)
        {
            Connection conn = t.getConnection();

            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                _log.error("Failed to close connection", e);
            }

            _transaction.remove();
        }
    }


    public SqlDialect getSqlDialect()
    {
        return _dialect;
    }


    public DbSchema getSchema(String schemaName)
    {
        // synchronized ensures one thread at a time.  This assert detects same-thread re-entrancy (e.g., the schema
        // load process directly or indirectly causing another call to this method.)
        assert !Thread.holdsLock(_loadedSchemas) : "Schema load re-entrancy detected";

        synchronized (_loadedSchemas)
        {
            DbSchema schema = _loadedSchemas.get(schemaName);

            if (null != schema && !AppProps.getInstance().isDevMode())
                return schema;

            InputStream xmlStream = null;

            try
            {
                Resource resource;

                if (null == schema)
                {
                    resource = DbSchema.getSchemaResource(schemaName);
                }
                else
                {
                    if (AppProps.getInstance().isDevMode() && schema.isStale())
                    {
                        resource = schema.getResource();
                    }
                    else
                    {
                        return schema;
                    }
                }

                schema = DbSchema.createFromMetaData(schemaName, this);

                if (null != schema)
                {
                    if (resource == null)
                        resource = new DbSchemaResource(schema);

                    schema.setResource(resource);
                    xmlStream = resource.getInputStream();
                    if (null != xmlStream)
                    {
                        TablesDocument tablesDoc = TablesDocument.Factory.parse(xmlStream);
                        schema.loadXml(tablesDoc, true);
                    }

                    _loadedSchemas.put(schema.getName(), schema);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);  // Changed from "return null" to "throw runtimeexception" so admin is made aware of the cause of the problem
            }
            finally
            {
                try
                {
                    if (null != xmlStream) xmlStream.close();
                }
                catch (Exception x)
                {
                    _log.error("DbSchema.get()", x);
                }
            }

            return schema;
        }
    }


    public void invalidateSchema(String schemaName)
    {
        synchronized (_loadedSchemas)
        {
            _loadedSchemas.remove(schemaName);
        }
    }


    // Invalidates all incomplete schemas in this scope (see below for details).
    public void invalidateIncompleteSchemas()
    {
        synchronized (_loadedSchemas)
        {
            List<String> schemaNames = new ArrayList<String>(_loadedSchemas.keySet());

            for (String schemaName : schemaNames)
            {
                Module module = ModuleLoader.getInstance().getModuleForSchemaName(schemaName);

                // We only care about schemas associated with a module (not external schemas)
                if (null != module)
                {
                    ModuleContext context = ModuleLoader.getInstance().getModuleContext(module);

                    if (!context.isInstallComplete())
                        invalidateSchema(schemaName);
                }
            }
        }
    }


    // Invalidates all incomplete schemas in all scopes.  Once a module is done with its upgrade then all database
    // schemas it owns are upgraded.  This clears out only schemas of modules that are not upgraded, so we don't, for
    // example, reload core, prop, etc. after they're complete.
    public static void invalidateAllIncompleteSchemas()
    {
        for (DbScope scope : getDbScopes())
            scope.invalidateIncompleteSchemas();
    }


    public void addCommitTask(Runnable task)
    {
        Transaction t = _transaction.get();

        if (null == t)
            throw new IllegalStateException("Must be inside a transaction");

        t.addCommitTask(task);
    }


    public static void initializeScopes(Map<String, DataSource> dataSources) throws ServletException
    {
        synchronized (_scopes)
        {
            if (!_scopes.isEmpty())
                throw new IllegalStateException("DbScopes are already initialized");

            String labkeyDsName = null;

            if (dataSources.containsKey("labkeyDataSource"))
                labkeyDsName = "labkeyDataSource";
            else if (dataSources.containsKey("cpasDataSource"))
                labkeyDsName = "cpasDataSource";

            if (null == labkeyDsName)
                throw new ConfigurationException("You must have a DataSource named \"labkeyDataSource\" defined in labkey.xml.");
            else
                ensureDataBase(labkeyDsName, dataSources.get(labkeyDsName));

            // Put labkey data source first, followed by all others in alphabetical order
            Set<String> dsNames = new LinkedHashSet<String>();
            dsNames.add(labkeyDsName);

            for (String dsName : dataSources.keySet())
                if (!dsName.equals(labkeyDsName))
                    dsNames.add(dsName);

            for (String dsName : dsNames)
            {
                try
                {
                    DbScope scope = new DbScope(dsName, dataSources.get(dsName));
                    scope.getSqlDialect().prepareNewDbScope(scope);
                    _scopes.put(dsName, scope);
                }
                catch (ConfigurationException ce)
                {
                    // Rethrow a ConfigurationException -- it includes important details about the failure  
                    throw ce;
                }
                catch (Exception e)
                {
                    if (dsName.equals(labkeyDsName))
                        throw new ConfigurationException("Cannot connect to DataSource \"" + labkeyDsName + "\" defined in labkey.xml.  Server cannot start.", e);
                    else
                        _log.error("Cannot connect to DataSource \"" + dsName + "\" defined in labkey.xml.  This DataSource will not be available during this server session.", e);
                }
            }

            _labkeyScope = _scopes.get(labkeyDsName);

            if (null == _labkeyScope)
                throw new ConfigurationException("Cannot connect to DataSource \"" + labkeyDsName + "\" defined in labkey.xml.  Server cannot start.");
        }
    }


    // Ensure we can connect to the specified datasource.  If the connection fails with a "database doesn't exist" exception
    // then attempt to create the database.  Return true if the database existed, false if it was just created.  Throw if some
    // other exception occurs (connection fails repeatedly with something other than "database doesn't exist" or database can't
    // be created.
    private static boolean ensureDataBase(String dsName, DataSource ds) throws ServletException
    {
        Connection conn = null;
        SqlDialect.DataSourceProperties props = new SqlDialect.DataSourceProperties(dsName, ds);

        // Need the dialect to:
        // 1) determine whether an exception is "no database" or something else and
        // 2) get the name of the "master" database
        //
        // Only way to get the right dialect is to look up based on the driver class name.
        SqlDialect dialect = SqlDialect.getFromDataSourceProperties(props);

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
        throw new ConfigurationException("Can't connect to data source \"" + dsName + "\".", "Make sure that your LabKey Server configuration file includes the correct user name, password, url, port, etc. for your database and that the database server is running.", lastException);
    }


    private static void createDataBase(SqlDialect.DataSourceProperties props, SqlDialect dialect) throws ServletException
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


    public static DbScope getLabkeyScope()
    {
        synchronized (_scopes)
        {
            return _labkeyScope;
        }
    }


    public static DbScope getDbScope(String dsName)
    {
        synchronized (_scopes)
        {
            return _scopes.get(dsName);
        }
    }

    public static Collection<DbScope> getDbScopes()
    {
        synchronized (_scopes)
        {
            return new ArrayList<DbScope>(_scopes.values());
        }
    }

    public static void rollbackAllTransactions()
    {
        synchronized (_scopes)
        {
            for (DbScope scope : _scopes.values())
            {
                if (scope.isTransactionActive())
                {
                    try
                    {
                        scope.rollbackTransaction();
                    }
                    catch (Exception x)
                    {
                        _log.error("Rollback All Transactions", x);
                    }
                }
            }
        }
    }


    interface ConnectionMap
    {
        Integer get(Connection c);
        Integer put(Connection c, Integer spid);
    }


    static ConnectionMap newConnectionMap()
    {
//        final HashMap<Connection,Integer> m = new HashMap<Connection,Integer>();
//        final HashMap<Connection,Integer> m = new WeakHashMap<Connection,Integer>();
        final _WeakestLinkMap<Connection,Integer> m = new _WeakestLinkMap<Connection,Integer>();
        return new ConnectionMap() {
            public synchronized Integer get(Connection c)
            {
                return m.get(c);
            }

            public synchronized Integer put(Connection c, Integer spid)
            {
                return m.put(c,spid);
            }
        };
    }


    /** weak identity hash map, could just subclass WeakHashMap but eq() is static (and not overridable) */
    private static class _WeakestLinkMap<K, V>
    {
        int _max = 1000;
        final ReferenceQueue<K> _q = new ReferenceQueue<K>();

        LinkedHashMap<_IdentityWrapper, V> _map = new LinkedHashMap<_IdentityWrapper, V>()
        {
            protected boolean removeEldestEntry(Map.Entry<_IdentityWrapper, V> eldest)
            {
                _purge();
                return size() > _max;
            }
        };

        private class _IdentityWrapper extends WeakReference<K>
        {
            int _hash;

            _IdentityWrapper(K o)
            {
                super(o, _q);
                _hash = System.identityHashCode(get());
            }

            public int hashCode()
            {
                return _hash;
            }

            public boolean equals(Object obj)
            {
                return obj instanceof Reference && get() == ((Reference)obj).get();
            }
        }

        _WeakestLinkMap()
        {
        }

        _WeakestLinkMap(int max)
        {
            _max = max;
        }

        public V put(K key, V value)
        {
            return _map.put(new _IdentityWrapper(key), value);
        }

        public V get(K key)
        {
            return _map.get(new _IdentityWrapper(key));
        }

        private void _purge()
        {
            _IdentityWrapper w;
            while (null != (w = (_IdentityWrapper)_q.poll()))
            {
                _map.remove(w);
            }
        }
    }

    public static void test()
    {
        _WeakestLinkMap<String,Integer> m = new _WeakestLinkMap<String,Integer>(10);
        //noinspection MismatchedQueryAndUpdateOfCollection
        Set<String> save = new HashSet<String>();
        
        for (int i = 0 ; i < 100000; i++)
        {
            if (i % 1000 == 0) System.gc();
            String s = "" + i;
            if (i % 3 == 0)
                save.add(s);
            m.put(s,i);
        }
    }

    // Represents a single database transaction.  Holds onto the Connection, the temporary caches to use during that
    // transaction, and the tasks to run immediately after commit to update the shared cache with removals.
    static class Transaction
    {
        private final Connection _conn;
        private final Map<DatabaseCache<?>, TTLCacheMap<String, ?>> _caches = new HashMap<DatabaseCache<?>, TTLCacheMap<String, ?>>(20);
        private final LinkedList<Runnable> _commitTasks = new LinkedList<Runnable>();

        Transaction(Connection conn)
        {
            _conn = conn;
        }

        <ValueType> TTLCacheMap<String, ValueType> getCache(DatabaseCache<ValueType> cache)
        {
            return (TTLCacheMap<String, ValueType>)_caches.get(cache);
        }

        <ValueType> void addCache(DatabaseCache<ValueType> cache, TTLCacheMap<String, ValueType> map)
        {
            _caches.put(cache, map);
        }

        void addCommitTask(Runnable task)
        {
            _commitTasks.add(task);
        }

        private Connection getConnection()
        {
            return _conn;
        }

        private void clearCommitTasks()
        {
            _commitTasks.clear();
        }

        private void runCommitTasks()
        {
            while (!_commitTasks.isEmpty())
                _commitTasks.removeFirst().run();
        }
    }

    private class DbSchemaResource extends AbstractResource
    {
        private DbSchema schema;

        protected DbSchemaResource(DbSchema schema)
        {
            // CONSIDER: create a ResourceResolver based on DbScope
            super(new Path(schema.getName()), null);
            this.schema = schema;
        }

        @Override
        public Resource parent()
        {
            return null;
        }

        @Override
        public boolean exists()
        {
            // UNDONE: The DbSchemaResource could check if the schema exists
            // in the source database.  For now the DbSchemaResource always exists.
            return true;
        }

        @Override
        public long getVersionStamp()
        {
            // UNDONE: The DbSchemaResource could check if the schema is modified
            // in the source database.  For now the DbSchemaResource is always up to date.
            return 0L;
        }
    }
}
