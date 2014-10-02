/*
 * Copyright (c) 2005-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ModuleResourceResolver;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Path;
import org.labkey.data.xml.TablesDocument;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.RandomAccess;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that wraps a data source and is shared amongst that data source's DbSchemas.
 *
 * Allows "nested" transactions, implemented via a reference-counting style approach. Each (potentially nested)
 * set of code should call ensureTransaction(). This will either start a new transaction, or join an existing one.
 * Once the outermost caller calls commit(), the WHOLE transaction will be committed at once.
 *
 * The most common usage scenario looks something like:
 *
 * DbScope scope = dbSchemaInstance.getScope();
 * try (DbScope.Transaction transaction = scope.ensureTransaction())
 * {
 *     // Do the real work
 *     transaction.commit();
 * }
 *
 * The DbScope.Transaction class implements AutoCloseable, so it will be cleaned up automatically by JDK 7's try {}
 * resource handling.
 *
 * User: migra
 * Date: Nov 16, 2005
 * Time: 10:20:54 AM
 */
public class DbScope
{
    private static final Logger LOG = Logger.getLogger(DbScope.class);
    private static final ConnectionMap _initializedConnections = newConnectionMap();
    private static final Map<String, DbScope> _scopes = new LinkedHashMap<>();
    private static final Map<Thread, Thread> _sharedConnections = new WeakHashMap<>();
    private static final Map<String, Throwable> _dataSourceFailures = new HashMap<>();
    // Cache for schema metadata XML files, shared across the whole server
    private static final ModuleResourceCache<TablesDocument> SCHEMA_XML_CACHE = ModuleResourceCaches.create(
            new Path(SchemaXmlCacheHandler.DIR_NAME), "Parsed schema XML metadata", new SchemaXmlCacheHandler());

    private static DbScope _labkeyScope = null;

    private final String _dsName;
    private final DataSource _dataSource;
    private final @Nullable String _databaseName;    // Possibly null, e.g., for SAS datasources
    private final String _URL;
    private final String _databaseProductName;
    private final String _databaseProductVersion;
    private final String _driverName;
    private final String _driverVersion;
    private final DbSchemaCache _schemaCache;
    private final SchemaTableInfoCache _tableCache;
    private final Map<Thread, TransactionImpl> _transaction = new WeakHashMap<>();

    private SqlDialect _dialect;

    // Used only for testing
    protected DbScope()
    {
        _dsName = null;
        _dataSource = null;
        _databaseName = null;
        _URL = null;
        _databaseProductName = null;
        _databaseProductVersion = null;
        _driverName = null;
        _driverVersion = null;
        _schemaCache = null;
        _tableCache = null;
    }


    // Attempt a (non-pooled) connection to a datasource.  We don't use DbSchema or normal pooled connections here
    // because failed connections seem to get added into the pool.
    protected DbScope(String dsName, DataSource dataSource) throws ServletException, SQLException
    {
        try (Connection conn = dataSource.getConnection())
        {
            DatabaseMetaData dbmd = conn.getMetaData();

            try
            {
                _dialect = SqlDialectManager.getFromMetaData(dbmd, true);
                MemTracker.getInstance().remove(_dialect);
            }
            finally
            {
                // Always log the attempt, even if DatabaseNotSupportedException, etc. occurs, to help with diagnosis
                LOG.info("Initializing DbScope with the following configuration:" +
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
            _schemaCache = new DbSchemaCache(this);
            _tableCache = new SchemaTableInfoCache(this);
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

    public @Nullable String getDatabaseName()
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


    /**
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction (or closeConnection() on the scope).
     */
    public Transaction ensureTransaction(Lock... locks)
    {
        return ensureTransaction(Connection.TRANSACTION_READ_COMMITTED, locks);
    }


    /**
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction (or closeConnection() on the scope).
     */
    public Transaction ensureTransaction(int isolationLevel, Lock... locks)
    {
        if (isTransactionActive())
        {
            TransactionImpl transaction = getCurrentTransactionImpl();
            assert null != transaction;
            transaction.increment(locks);
            Connection conn = transaction.getConnection();
//            if (conn.getTransactionIsolation() < isolationLevel)
//                conn.setTransactionIsolation(isolationLevel);
            return transaction;
        }
        else
        {
            return beginTransaction(isolationLevel, locks);
        }
    }


    /**
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction (or closeConnection() on the scope).
     */
    public Transaction beginTransaction(Lock... locks)
    {
        return beginTransaction(Connection.TRANSACTION_READ_COMMITTED, locks);
    }

    /**
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction (or closeConnection() on the scope).
     */
    public Transaction beginTransaction(int isolationLevel, Lock... locks)
    {
        if (isTransactionActive())
            throw new IllegalStateException("Existing transaction");

        Connection conn = null;
        TransactionImpl result = null;

        // try/finally ensures that closeConnection() works even if setAutoCommit() throws 
        try
        {
            conn = _getConnection(null);
            // we expect connetions coming from the cache to be at a low transaction isolation level
            // if not then we probably didn't reset after a previous commit/abort
            //assert Connection.TRANSACTION_READ_COMMITTED >= conn.getTransactionIsolation();
            //conn.setTransactionIsolation(isolationLevel);
            conn.setAutoCommit(false);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (null != conn)
            {
                // Acquire the requested locks BEFORE entering the synchronized block for mapping the transaction
                // to the current thread
                result = new TransactionImpl(conn, locks);
                synchronized (_transaction)
                {
                    _transaction.put(getEffectiveThread(), result);
                }
            }
        }

        return result;
    }

    /** Use DbScope.Transaction.commit() instead */
    @Deprecated
    public void commitTransaction()
    {
        TransactionImpl t = getCurrentTransactionImpl();
        if (t == null)
        {
            throw new IllegalStateException("No transaction is associated with this thread");
        }
        if (t._aborted)
        {
            throw new IllegalStateException("Transaction has already been rolled back");
        }
        if (t._closesToIgnore > 0)
        {
            t._closesToIgnore = 0;
            closeConnection();
            throw new IllegalStateException("Missing expected call to close after prior commit");
        }

        t._closesToIgnore++;
        if (t.decrement())
        {
            Connection conn = t.getConnection();
            try
            {
                t.runCommitTasks(CommitTaskOption.PRECOMMIT);
                conn.commit();
                conn.setAutoCommit(true);
    //            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.close();
                synchronized (_transaction)
                {
                    _transaction.remove(getEffectiveThread());
                }
                t.runCommitTasks(CommitTaskOption.POSTCOMMIT);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }


    private Thread getEffectiveThread()
    {
        synchronized (_sharedConnections)
        {
            Thread result = _sharedConnections.get(Thread.currentThread());
            if (result == null)
            {
                return Thread.currentThread();
            }
            return result;
        }
    }


    public boolean isTransactionActive()
    {
        DbScope.Transaction tx = getCurrentTransaction();
        return null != tx;
    }


    public @Nullable Transaction getCurrentTransaction()
    {
        return getCurrentTransactionImpl();
    }

    /* package */ @Nullable TransactionImpl getCurrentTransactionImpl()
    {
        synchronized (_transaction)
        {
            return _transaction.get(getEffectiveThread());
        }
    }

    public Connection getConnection() throws SQLException
    {
        return getConnection(null);
    }

    public Connection getConnection(@Nullable Logger log) throws SQLException
    {
        Transaction t = getCurrentTransaction();

        if (null == t)
            return _getConnection(log);
        else
            return t.getConnection();
    }


    // Get a fresh connection directly from the pool... not part of the current transaction, etc.
    public Connection getPooledConnection() throws SQLException
    {
        return _getConnection(null);
    }


    public Connection getUnpooledConnection() throws SQLException
    {
        SqlDialect.DataSourceProperties props = new SqlDialect.DataSourceProperties(_dsName, _dataSource);

        try
        {
            return DriverManager.getConnection(_URL, props.getUsername(), props.getPassword());
        }
        catch (ServletException e)
        {
            throw new RuntimeException(e);
        }
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
                LOG.info("Could not find class DelegatingConnection", x);
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

        // This works for Tomcat JDBC Connection Pool
        if (conn instanceof PooledConnection)
        {
            try
            {
                return ((PooledConnection) conn).getConnection();
            }
            catch (SQLException e)
            {
                LOG.error("Attempt to retrieve underlying connection failed", e);
            }
        }

        // This approach is required for Commons DBCP (default Tomcat connection pool)
        ensureDelegation(conn);

        if (isDelegating && classDelegatingConnection.isAssignableFrom(conn.getClass()))
        {
            try
            {
                delegate = (Connection)methodGetInnermostDelegate.invoke(conn);
            }
            catch (Exception x)
            {
                LOG.error("Unexpected error", x);
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

    /**
     * Write to the standard log file information about all threads (active or dead) that appear to be holding onto
     * database connections, having started via beginning a transaction.
     */
    public void logCurrentConnectionState()
    {
        synchronized (_transaction)
        {
            if (_transaction.isEmpty())
            {
                LOG.info("There are no threads holding connections for the data source '" + toString() + "'");
            }
            else
            {
                if (_transaction.size() == 1)
                {
                    LOG.info("There is one thread holding a database connections for the data source '" + toString() + "':");
                }
                else
                {
                    LOG.info("There are " + _transaction.size() + " threads holding a database connections for the data source '" + toString() + "':");
                    for (Map.Entry<Thread, TransactionImpl> entry : _transaction.entrySet())
                    {
                        Thread thread = entry.getKey();
                        LOG.info("\t'" + thread.getName() + "', State = " + thread.getState());
                        if (thread.getState() == Thread.State.TERMINATED || thread.getState() == Thread.State.NEW)
                        {
                            for (StackTraceElement stackTraceElement : entry.getValue()._creation.getStackTrace())
                            {
                                LOG.info("\t\t" + stackTraceElement.toString());
                            }
                        }
                    }
                }
            }
        }
    }

    private static final int spidUnknown = -1;

    protected Connection _getConnection(@Nullable Logger log) throws SQLException
    {
        Connection conn;

        try
        {
            conn = _dataSource.getConnection();
        }
        catch (SQLException e)
        {
            throw new ConfigurationException("Can't create a database connection to " + _dataSource.toString(), e);
        }

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

        return new ConnectionWrapper(conn, this, spid, log);
    }


    public void releaseConnection(Connection conn)
    {
        Transaction t = getCurrentTransaction();

        if (null != t)
        {
            assert t.getConnection() == conn : "Attempting to close a different connection from the one associated with this thread: " + conn + " vs " + t.getConnection(); //Should release same conn we handed out
        }
        else
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                LOG.warn("error releasing connection: " + e.getMessage(), e);
            }
        }
    }

    /** Use DbScope.Transaction.close() instead. Or better yet, use a try-with-resources to auto-close the Transaction object */
    @Deprecated
    public void closeConnection()
    {
        TransactionImpl t = getCurrentTransactionImpl();
        if (null != t)
        {
            t.close();
        }
    }


    public SqlDialect getSqlDialect()
    {
        return _dialect;
    }


    @NotNull
    // Load meta data from database and overlay schema.xml, if DbSchemaType requires it
    protected DbSchema loadSchema(String schemaName, DbSchemaType type) throws SQLException, IOException, XmlException
    {
        LOG.info("Loading DbSchema \"" + getDisplayName() + "." + schemaName + "\" (" + type.name() + ")");

        DbSchema schema = DbSchema.createFromMetaData(this, schemaName, type);
        // Consider:  Logging the cases where loadSchema is called in a transaction, because this causes a JDBC call that is vulnerable to deadlocking
        // if (schema.getScope().isTransactionActive())
        //        LOG.info("Loading IN TRAN");
        if (type.applyXmlMetaData())
            applyMetaDataXML(schema, schemaName);

        return schema;
    }

    private void applyMetaDataXML(DbSchema schema, String schemaName) throws IOException, XmlException
    {
        // Use the canonical schema name, not the requested name (which could differ in casing)
        Resource resource = schema.getSchemaResource();

        if (null == resource)
        {
            String lowerName = schemaName.toLowerCase();

            if (!lowerName.equals(schema.getName()))
                resource = schema.getSchemaResource(lowerName);
        }

        if (null == resource)
        {
            LOG.info("no schema metadata xml file found for schema '" + schemaName + "'");

            DbSchemaType type = ModuleLoader.getInstance().getSchemaTypeForSchemaName(schemaName);

            if (null != type && !type.applyXmlMetaData())
                LOG.info("Shouldn't be loading metadata for " + type.name() + " schema '" + schemaName + "'");
        }
        else
        {
            String filename = resource.getName();

            // I don't like this... should either improve Resolver (add getModule()?) or revise getResource() to take a Resource
            Resolver resolver = resource.getResolver();
            assert resolver instanceof ModuleResourceResolver;
            Module module = ((ModuleResourceResolver) resolver).getModule();

            TablesDocument tablesDoc = SCHEMA_XML_CACHE.getResource(ModuleResourceCache.createCacheKey(module, filename));

            if (null != tablesDoc)
                schema.setTablesDocument(tablesDoc);
        }
    }


    public @NotNull DbSchema getSchema(String schemaName, DbSchemaType type)
    {
        return _schemaCache.get(schemaName, type);
    }


    // Get the special "labkey" schema created in each module data source
    public @NotNull DbSchema getLabKeySchema()
    {
        return getSchema("labkey", DbSchemaType.Module);
    }


    // Each scope holds the cache for all its tables.  This makes it easier to 1) configure that cache on a per-scope
    // basis and 2) invalidate schemas and their tables together
    public <OptionType extends SchemaTableOptions> SchemaTableInfo getTable(OptionType options)
    {
        return _tableCache.get(options);
    }

    // Collection of schema names in this scope, in no particular order.
    public Collection<String> getSchemaNames() throws SQLException
    {
        return SchemaNameCache.get().getSchemaNameMap(this).values();
    }


    /** Invalidates this schema and all its associated tables */
    public void invalidateSchema(DbSchema schema)
    {
        invalidateSchema(schema.getName(), schema.getType());
    }


    /** Invalidates this schema and all its associated tables */
    public void invalidateSchema(String schemaName, DbSchemaType type)
    {
        _schemaCache.remove(schemaName, type);
        invalidateAllTables(schemaName, type);
    }


    // Invalidates all tables in the table cache. Careful: callers probably need to invalidate the schema as well (it holds a list of table names)
    void invalidateAllTables(String schemaName, DbSchemaType type)
    {
        _tableCache.removeAllTables(schemaName, type);
    }

    // Invalidates a single table in this schema
    // to avoid calls to getSchema in transactions, use the string-based invalidateTable method below
    @Deprecated
    public void invalidateTable(DbSchema schema, @NotNull String table)
    {
        _tableCache.remove(schema, table);

        // DbSchema holds a hard-coded list of table names; we also need to invalidate the DbSchema to update this list.
        // Note that all other TableInfos remain cached; this is simply invalidating the schema info and reloading the
        // meta data XML. If this is too heavyweight, we could instead cache and invalidate the list of table names separate
        // from the DbSchema.
        _schemaCache.remove(schema.getName(), schema.getType());
    }

    public void invalidateTable(String schemaName, String tableName, DbSchemaType type)
    {
        _tableCache.remove(schemaName, tableName, type);

        _schemaCache.remove(schemaName, type);
    }


    /**
     * If a transaction is active, the task is run after it's committed. If not, it's run immediately and synchronously.
     *
     * The tasks are put into a LinkedHashSet, so they'll run in order, but we will avoid running identical tasks
     * multiple times. Make sure you have implemented hashCode() and equals() on your task if you want to only run it
     * once per transaction.
     *
     * @return  the task that was inserted or the existing class that will be run instead
     */
    public <T extends Runnable> T addCommitTask(T task, CommitTaskOption taskOption)
    {
        Transaction t = getCurrentTransaction();

        if (null == t)
        {
            task.run();
            return task;
        }
        else
        {
            return t.addCommitTask(task, taskOption);
        }
    }


    public static void initializeScopes(String labkeyDsName, Map<String, DataSource> dataSources) throws ServletException
    {
        synchronized (_scopes)
        {
            if (!_scopes.isEmpty())
                throw new IllegalStateException("DbScopes are already initialized");

            if (!dataSources.containsKey(labkeyDsName))
                throw new IllegalStateException(labkeyDsName + " DataSource not found");

            // Find all the external data sources required by module schemas; we attempt to create these databases
            Set<String> moduleDataSources = ModuleLoader.getInstance().getAllModuleDataSources();

            // Make sorted collection of data sources names, but with labkey data source first
            Set<String> dsNames = new LinkedHashSet<>();
            dsNames.add(labkeyDsName);

            for (String dsName : dataSources.keySet())
                if (!dsName.equals(labkeyDsName))
                    dsNames.add(dsName);

            for (String dsName : dsNames)
            {
                try
                {
                    // Attempt to create databases in data sources required by modules
                    if (moduleDataSources.contains(dsName))
                    {
                        try
                        {
                            ModuleLoader.getInstance().ensureDatabase(new String[]{dsName});
                        }
                        catch (Throwable t)
                        {
                            // Database creation failed, but the data source may still be useable for external schemas
                            // (e.g., a MySQL data source), so continue on and attempt to initialize the scope
                            LOG.info("Failed to create database", t);
                            addDataSourceFailure(dsName, t);
                        }
                    }

                    DbScope scope = new DbScope(dsName, dataSources.get(dsName));
                    scope.getSqlDialect().prepare(scope);
                    _scopes.put(dsName, scope);
                }
                catch (Exception e)
                {
                    // Server can't start up if it can't connect to the labkey datasource
                    if (dsName.equals(labkeyDsName))
                    {
                        // Rethrow a ConfigurationException -- it includes important details about the failure
                        if (e instanceof ConfigurationException)
                            throw (ConfigurationException)e;

                        throw new ConfigurationException("Cannot connect to DataSource \"" + labkeyDsName + "\" defined in labkey.xml.  Server cannot start.", e);
                    }

                    // Failure to connect with any other datasource results in an error message, but doesn't halt startup  
                    LOG.error("Cannot connect to DataSource \"" + dsName + "\" defined in labkey.xml.  This DataSource will not be available during this server session.", e);
                    addDataSourceFailure(dsName, e);
                }
            }

            _labkeyScope = _scopes.get(labkeyDsName);

            if (null == _labkeyScope)
                throw new ConfigurationException("Cannot connect to DataSource \"" + labkeyDsName + "\" defined in labkey.xml.  Server cannot start.");

            _labkeyScope.getSqlDialect().prepareNewLabKeyDatabase(_labkeyScope);
        }
    }


    // Ensure we can connect to the specified datasource.  If the connection fails with a "database doesn't exist" exception
    // then attempt to create the database.  Return true if the database existed, false if it was just created.  Throw if some
    // other exception occurs (e.g., connection fails repeatedly with something other than "database doesn't exist" or database
    // can't be created.)
    public static boolean ensureDataBase(String dsName, DataSource ds) throws ServletException
    {
        Connection conn = null;
        SqlDialect.DataSourceProperties props = new SqlDialect.DataSourceProperties(dsName, ds);

        // Need the dialect to:
        // 1) determine whether an exception is "no database" or something else and
        // 2) get the name of the "master" database
        //
        // Only way to get the right dialect is to look up based on the driver class name.
        SqlDialect dialect = SqlDialectManager.getFromDriverClassname(dsName, props.getDriverClassName());

        if (!dialect.isPostgreSQL() && !dialect.isSqlServer())
            throw new ConfigurationException("Can't use data source \"" + dsName + "\" for module schemas; " + dialect.getProductName() + " does not support module schemas");

        SQLException lastException = null;

        // Attempt a connection three times before giving up
        for (int i = 0; i < 3; i++)
        {
            if (i > 0)
            {
                LOG.error("Retrying connection to \"" + dsName + "\" at " + props.getUrl() + " in 10 seconds");

                try
                {
                    Thread.sleep(10000);  // Wait 10 seconds before trying again
                }
                catch (InterruptedException e)
                {
                    LOG.error("ensureDataBase", e);
                }
            }

            try
            {
                // Load the JDBC driver
                Class.forName(props.getDriverClassName());
                // Create non-pooled connection... don't want to pool a failed connection
                conn = DriverManager.getConnection(props.getUrl(), props.getUsername(), props.getPassword());
                LOG.debug("Successful connection to \"" + dsName + "\" at " + props.getUrl());
                return true;        // Database already exists
            }
            catch (SQLException e)
            {
                if (dialect.isNoDatabaseException(e))
                {
                    createDataBase(dialect, props.getUrl(), props.getUsername(), props.getPassword());
                    return false;   // Successfully created database
                }
                else
                {
                    LOG.error("Connection to \"" + dsName + "\" at " + props.getUrl() + " failed with the following error:");
                    LOG.error("Message: " + e.getMessage() + " SQLState: " + e.getSQLState() + " ErrorCode: " + e.getErrorCode(), e);
                    lastException = e;
                }
            }
            catch (Exception e)
            {
                LOG.error("ensureDataBase", e);
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
                    LOG.error("Error closing connection", x);
                }
            }
        }

        LOG.error("Attempted to connect three times... giving up.", lastException);
        throw new ConfigurationException("Can't connect to data source \"" + dsName + "\".", "Make sure that your LabKey Server configuration file includes the correct user name, password, url, port, etc. for your database and that the database server is running.", lastException);
    }


    public static void createDataBase(SqlDialect dialect, String url, String username, String password) throws ServletException
    {
        Connection conn = null;
        PreparedStatement stmt = null;

        String dbName = dialect.getDatabaseName(url);

        LOG.info("Attempting to create database \"" + dbName + "\"");

        String masterUrl = StringUtils.replace(url, dbName, dialect.getMasterDataBaseName());
        String createSql = "(undefined)";

        try
        {
            conn = DriverManager.getConnection(masterUrl, username, password);
            // Get version-specific dialect; don't log version warnings.
            dialect = SqlDialectManager.getFromMetaData(conn.getMetaData(), false);
            createSql = dialect.getCreateDatabaseSql(dbName);
            stmt = conn.prepareStatement(createSql);
            stmt.execute();
        }
        catch (SQLException e)
        {
            LOG.error("Create database failed, SQL: " + createSql, e);
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
                LOG.error("", x);
            }
            try
            {
                if (null != stmt) stmt.close();
            }
            catch (Exception x)
            {
                LOG.error("", x);
            }
        }

        LOG.info("Database \"" + dbName + "\" created");
    }


    // Store the initial failure message for each data source
    private static void addDataSourceFailure(String dsName, Throwable t)
    {
        if (!_dataSourceFailures.containsKey(dsName))
            //noinspection ThrowableResultOfMethodCallIgnored
            _dataSourceFailures.put(dsName, t);
    }


    public static @Nullable Throwable getDataSourceFailure(String dsName)
    {
        return _dataSourceFailures.get(dsName);
    }


    public static DbScope getLabkeyScope()
    {
        synchronized (_scopes)
        {
            return _labkeyScope;
        }
    }


    public boolean isLabKeyScope()
    {
        return this == getLabkeyScope();
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
            return new ArrayList<>(_scopes.values());
        }
    }

    public static void closeAllConnections()
    {
        synchronized (_scopes)
        {
            for (DbScope scope : _scopes.values())
            {
                TransactionImpl t = scope.getCurrentTransactionImpl();
                if (t != null)
                {
                    try
                    {
                        LOG.warn("Forcing close of transaction started at ", t._creation);
                        t.closeConnection();
                    }
                    catch (Exception x)
                    {
                        LOG.error("Failure forcing connection close on " + scope, x);
                    }
                }
            }
        }
    }

    /**
     * Causes any connections associated with the current thread to be used by the passed-in thread. This allows
     * an async thread to participate in the same transaction as the original HTTP request processing thread, for
     * example
     * @param asyncThread the thread that should use the database connections of the current thread
     */
    public static void shareConnections(Thread asyncThread)
    {
        synchronized (_sharedConnections)
        {
            if (_sharedConnections.containsKey(asyncThread))
            {
                throw new IllegalStateException("Thread '" + asyncThread.getName() + "' is already sharing the connections of thread '" + _sharedConnections.get(asyncThread) + "'");
            }
            _sharedConnections.put(asyncThread, Thread.currentThread());
        }
    }

    /**
     * Stops sharing any connections associated with the current thread with the passed-in thread.
     * @param asyncThread the thread that should stop using the database connections of the current thread
     */
    public static void stopSharingConnections(Thread asyncThread)
    {
        synchronized (_sharedConnections)
        {
            _sharedConnections.remove(asyncThread);
        }
    }


    interface ConnectionMap
    {
        Integer get(Connection c);
        Integer put(Connection c, Integer spid);
    }


    static ConnectionMap newConnectionMap()
    {
        final _WeakestLinkMap<Connection, Integer> m = new _WeakestLinkMap<>();

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

        final ReferenceQueue<K> _q = new ReferenceQueue<>();
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
        _WeakestLinkMap<String,Integer> m = new _WeakestLinkMap<>(10);
        //noinspection MismatchedQueryAndUpdateOfCollection
        Set<String> save = new HashSet<>();
        
        for (int i = 0 ; i < 100000; i++)
        {
            if (i % 1000 == 0) System.gc();
            String s = "" + i;
            if (i % 3 == 0)
                save.add(s);
            m.put(s,i);
        }
    }

    public enum CommitTaskOption
    {
        /** Run inside of the same transaction, immediately before committing it */
        PRECOMMIT,
        /** Run after the main transaction has been committed, separate from the transaction itself */
        POSTCOMMIT
    }

    public interface Transaction extends AutoCloseable
    {
        /*
         * @return  the task that was inserted or the existing class that will be run instead
         */
        public <T extends Runnable> T addCommitTask(T runnable, CommitTaskOption taskOption);
        public Connection getConnection();
        public void close();
        public void commit();
    }

    // Represents a single database transaction.  Holds onto the Connection, the temporary caches to use during that
    // transaction, and the tasks to run immediately after commit to update the shared caches with removals.
    protected class TransactionImpl implements Transaction
    {
        private final Connection _conn;
        private final Map<DatabaseCache<?>, StringKeyCache<?>> _caches = new HashMap<>(20);

        // Sets so that we can coalesce identical tasks and avoid duplicating the effort
        private final Set<Runnable> _preCommitTasks = new LinkedHashSet<>();
        private final Set<Runnable> _postCommitTasks = new LinkedHashSet<>();

        private List<List<Lock>> _locks = new ArrayList<>();
        private boolean _aborted = false;
        private int _closesToIgnore = 0;
        private Throwable _creation = new Throwable();

        TransactionImpl(Connection conn, Lock... extraLocks)
        {
            _conn = conn;
            increment(extraLocks);
        }

        <ValueType> StringKeyCache<ValueType> getCache(DatabaseCache<ValueType> cache)
        {
            return (StringKeyCache<ValueType>)_caches.get(cache);
        }

        <ValueType> void addCache(DatabaseCache<ValueType> cache, StringKeyCache<ValueType> map)
        {
            _caches.put(cache, map);
        }

        public <T extends Runnable> T addCommitTask(T task, CommitTaskOption taskOption)
        {
            boolean added;
            T addedObj = task;
            switch (taskOption)
            {
                case PRECOMMIT:
                    added = _preCommitTasks.add(task);
                    for(Runnable r : _preCommitTasks)
                        if( r.equals(task))
                        {
                            addedObj = (T) r;
                            break;
                        }
                    break;
                case POSTCOMMIT:
                    added = _postCommitTasks.add(task);
                    for(Runnable r : _postCommitTasks)
                        if( r.equals(task))
                        {
                            addedObj = (T) r;
                            break;
                        }
                    break;
                default:
                    throw new IllegalArgumentException("Option '" + taskOption + "' handled by addCommitTask");
            }
            if (!added)
            {
                LOG.debug("Skipping duplicate runnable: " + task.toString());
            }
            return addedObj;
        }

        public Connection getConnection()
        {
            return _conn;
        }

        private void clearCommitTasks()
        {
            _preCommitTasks.clear();
            _postCommitTasks.clear();
            closeCaches();
        }

        @Override
        public void close()
        {
            if (_closesToIgnore == 0)
            {
                if (!decrement())
                {
                    _aborted = true;
                }

                closeConnection();

                synchronized (_transaction)
                {
                    _transaction.remove(getEffectiveThread());
                }
            }
            else
            {
                _closesToIgnore--;
            }
            if (_closesToIgnore < 0)
            {
                throw new IllegalStateException("Popped too many closes from the stack");
            }
        }

        @Override
        public void commit()
        {
            DbScope.this.commitTransaction();
        }

        private void runCommitTasks(CommitTaskOption taskOption)
        {
            Set<Runnable> tasks = (taskOption == CommitTaskOption.PRECOMMIT ? _preCommitTasks : _postCommitTasks);

            while (!tasks.isEmpty())
            {
                Iterator<Runnable> i = tasks.iterator();
                i.next().run();
                i.remove();
            }

            closeCaches();
        }

        private void closeCaches()
        {
            for (StringKeyCache<?> cache : _caches.values())
                cache.close();
        }

        /** @return whether we've reached zero and should therefore commit if that's the request, or false if we should defer to a future call*/
        public boolean decrement()
        {
            if (_locks.isEmpty())
            {
                throw new IllegalStateException("No transactions remain, can't decrement!");
            }
            List<Lock> locks= _locks.remove(_locks.size() - 1);
            for (Lock lock : locks)
            {
                // Release all the locks
                lock.unlock();
            }

            return _locks.isEmpty();
        }

        private void closeConnection()
        {
            _aborted = true;
            Connection conn = getConnection();

            try
            {
//                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.close();
            }
            catch (SQLException e)
            {
                LOG.error("Failed to close connection", e);
            }

            closeCaches();
            clearCommitTasks();
        }

        public void increment(Lock... extraLocks)
        {
            for (Lock extraLock : extraLocks)
            {
                extraLock.lock();
            }
            _locks.add(Arrays.asList(extraLocks));
        }
    }

    /**
     * Represents options for retrieving schemas from the cache
     */
    public static class SchemaTableOptions
    {
        private final DbSchema _schema;
        private final String _tableName;

        public SchemaTableOptions(DbSchema schema, String tableName)
        {
            _schema = schema;
            _tableName = tableName;
        }

        public DbSchema getSchema()
        {
            return _schema;
        }

        public String getTableName()
        {
            return _tableName;
        }

        public void afterLoadTable(SchemaTableInfo ti)
        {
        }
    }

    // Test dialects that are in-use; only for tests that require connecting to the database.
    public static class DialectTestCase extends Assert
    {
        @Test
        public void testAllScopes() throws SQLException, IOException
        {
            for (DbScope scope : getDbScopes())
            {
                SqlDialect dialect = scope.getSqlDialect();
                Connection conn = null;

                try
                {
                    conn = scope.getConnection();
                    SqlExecutor executor = new SqlExecutor(scope, conn).setLogLevel(Level.OFF);  // We're about to generate a lot of SQLExceptions
                    dialect.testDialectKeywords(executor);
                    dialect.testKeywordCandidates(executor);
                }
                finally
                {
                    if (null != conn)
                        scope.releaseConnection(conn);
                }
            }
        }

        @Test
        public void testLabKeyScope() throws SQLException
        {
            DbScope scope = getLabkeyScope();
            SqlDialect dialect = scope.getSqlDialect();

            testDateDiff(scope, dialect, "2/1/2000", "1/1/2000", Calendar.DATE, 31);
            testDateDiff(scope, dialect, "1/1/2001", "1/1/2000", Calendar.DATE, 366);

            testDateDiff(scope, dialect, "2/1/2000", "1/1/2000", Calendar.MONTH, 1);
            testDateDiff(scope, dialect, "2/1/2000", "1/31/2000", Calendar.MONTH, 1);
            testDateDiff(scope, dialect, "1/1/2000", "1/1/2000", Calendar.MONTH, 0);
            testDateDiff(scope, dialect, "1/31/2000", "1/1/2000", Calendar.MONTH, 0);
            testDateDiff(scope, dialect, "12/31/2000", "1/1/2000", Calendar.MONTH, 11);
            testDateDiff(scope, dialect, "1/1/2001", "1/1/2000", Calendar.MONTH, 12);
            testDateDiff(scope, dialect, "1/31/2001", "1/1/2000", Calendar.MONTH, 12);

            testDateDiff(scope, dialect, "1/1/2000", "12/31/2000", Calendar.YEAR, 0);
            testDateDiff(scope, dialect, "1/1/2001", "1/1/2000", Calendar.YEAR, 1);

        }

        private void testDateDiff(DbScope scope, SqlDialect dialect, String date1, String date2, int part, int expected)
        {
            SQLFragment sql = new SQLFragment("SELECT (");
            sql.append(dialect.getDateDiff(part, "CAST('" + date1 + "' AS " + dialect.getDefaultDateTimeDataType() + ")", "CAST('" + date2 + "' AS " + dialect.getDefaultDateTimeDataType() + ")"));
            sql.append(") AS Diff");

            int actual = new SqlSelector(scope, sql).getObject(Integer.class);
            assertEquals(expected, actual);
        }
    }


    public static class TransactionTestCase extends Assert
    {
        @Test
        public void testMultiScopeTransaction() throws SQLException
        {
            // Test that a transaction in one scope doesn't affect other scopes. Start a transaction in the labkeyScope
            // and then SELECT 10 rows from a random table in a random schema in every datasource.
            List<TableInfo> tablesToTest = new LinkedList<>();

            for (DbScope scope : DbScope.getDbScopes())
            {
                SqlDialect dialect = scope.getSqlDialect();
                List<String> schemaNames = new ArrayList<>();

                for (String schemaName : scope.getSchemaNames())
                    if (!dialect.isSystemSchema(schemaName))
                        schemaNames.add(schemaName);

                if (schemaNames.isEmpty())
                    continue;

                String randomSchemaName = pickRandomElement(schemaNames);
                String qualifiedName = DbSchema.getDisplayName(scope, randomSchemaName);

                // Calling getSchema() with type Unknown will use Module type by default... we want Bare by default here
                DbSchemaType type = ModuleLoader.getInstance().getSchemaTypeForSchemaName(qualifiedName);
                if (null == type)
                    type = DbSchemaType.Bare;

                DbSchema schema = scope.getSchema(randomSchemaName, type);

                // For performance reasons, we don't bother loading the table list in Provisioned schemas, so we need special handling to get them here
                Collection<String> tableNames = DbSchemaType.Provisioned == type ? DbSchema.loadTableNames(scope, randomSchemaName).keySet() : schema.getTableNames();
                TableInfo tinfo = pickRandomTable(schema, tableNames);

                if (null != tinfo)
                    tablesToTest.add(tinfo);
            }

            try (Transaction ignored = DbScope.getLabkeyScope().ensureTransaction())
            {
                // LabKey scope should have an active transaction, and all other scopes should not
                for (DbScope scope : DbScope.getDbScopes())
                    Assert.assertEquals(scope.isLabKeyScope(), scope.isTransactionActive());

                for (TableInfo table : tablesToTest)
                {
                    TableSelector selector = new TableSelector(table);
                    selector.setMaxRows(10);
                    selector.getMapCollection();
                }
            }
        }


        private @Nullable TableInfo pickRandomTable(DbSchema schema, Collection<String> tableNames)
        {
            List<String> names = new ArrayList<>(tableNames);

            // Randomize the collection and then return the first legitimate table. Previously, we built a list of
            // good TableInfos and then picked one at random... but building all those TableInfos could be expensive
            // in the Provisioned case.
            Collections.shuffle(names);

            for (String name : names)
            {
                TableInfo table = schema.getTable(name);

                if (null == table)
                    LOG.error("Table is null: " + schema.getName() + "." + name);
                else if (table.getTableType() != DatabaseTableType.NOT_IN_DB)
                    return table;
            }

            return null;
        }


        private <E> E pickRandomElement(List<E> list)
        {
            int size = list.size();
            assert size > 0;

            int i = new Random().nextInt(size);
            E element;

            if (list instanceof RandomAccess)
            {
                return list.get(i);
            }
            else
            {
                Iterator<E> iter = list.iterator();
                do
                {
                    element = iter.next();
                }
                while (i-- > 0);
            }

            return element;
        }


        @Test(expected = IllegalStateException.class)
        public void testExtraCloseException()
        {
            try (Transaction t = getLabkeyScope().ensureTransaction())
            {
                assertTrue(getLabkeyScope().isTransactionActive());
                t.commit();
                assertFalse(getLabkeyScope().isTransactionActive());
                t.close();
                t.close();
            }
        }

        @Test
        public void testLockReleasedCommitted()
        {
            ReentrantLock lock = new ReentrantLock();
            ReentrantLock lock2 = new ReentrantLock();
            try (Transaction t = getLabkeyScope().ensureTransaction(lock))
            {
                assertEquals("Lock should be singly held", 1, lock.getHoldCount());
                try (Transaction t2 = getLabkeyScope().ensureTransaction(lock, lock2))
                {
                    assertEquals("Lock should be doubly held", 2, lock.getHoldCount());
                    assertEquals("Lock should be singly held", 1, lock2.getHoldCount());
                    t2.commit();
                }
                assertEquals("Lock should be singly held", 1, lock.getHoldCount());
                assertEquals("Lock should be released", 0, lock2.getHoldCount());
                t.commit();
            }
            assertEquals("Lock should be released", 0, lock.getHoldCount());
            assertEquals("Lock should be released", 0, lock2.getHoldCount());
        }

        @Test
        public void testLockReleasedException()
        {
            ReentrantLock lock = new ReentrantLock();
            ReentrantLock lock2 = new ReentrantLock();
            try
            {
                try (Transaction t = getLabkeyScope().ensureTransaction(lock))
                {
                    try
                    {
                        assertEquals("Lock should be singly held", 1, lock.getHoldCount());
                        try (Transaction t2 = getLabkeyScope().ensureTransaction(lock, lock2))
                        {
                            assertEquals("Lock should be doubly held", 2, lock.getHoldCount());
                            assertEquals("Lock should be singly held", 1, lock2.getHoldCount());
                            // no commit!
                            throw new RuntimeException();
                        }
                    }
                    catch (RuntimeException e)
                    {
                        assertEquals("Lock should be singly held", 1, lock.getHoldCount());
                        assertEquals("Lock should be released", 0, lock2.getHoldCount());
                        throw e;
                    }
                }
            }
            catch (RuntimeException ignored) {}
            assertEquals("Lock should be released", 0, lock.getHoldCount());
            assertEquals("Lock should be released", 0, lock2.getHoldCount());
        }

        @Test
        public void testNested()
        {
            // Create three nested transactions and make sure we don't really commit until the outermost one is complete
            try (Transaction t = getLabkeyScope().ensureTransaction())
            {
                Connection connection = t.getConnection();
                assertTrue(getLabkeyScope().isTransactionActive());
                try (Transaction t2 = getLabkeyScope().ensureTransaction())
                {
                    assertTrue(getLabkeyScope().isTransactionActive());
                    assertSame(connection, t2.getConnection());
                    try (Transaction t3 = getLabkeyScope().ensureTransaction())
                    {
                        assertTrue(getLabkeyScope().isTransactionActive());
                        assertSame(connection, t3.getConnection());
                        t3.commit();
                        assertTrue(getLabkeyScope().isTransactionActive());
                    }
                    assertSame(connection, t2.getConnection());
                    t2.commit();
                    assertTrue(getLabkeyScope().isTransactionActive());
                }
                t.commit();
                assertFalse(getLabkeyScope().isTransactionActive());
            }
            assertFalse(getLabkeyScope().isTransactionActive());
        }

        @Test(expected = IllegalStateException.class)
        public void testExtraCommit()
        {
            try (Transaction t = getLabkeyScope().ensureTransaction())
            {
                assertTrue(getLabkeyScope().isTransactionActive());
                t.commit();
                assertFalse(getLabkeyScope().isTransactionActive());
                // This call should cause an IllegalStateException, since we already committed the transaction
                t.commit();
            }
        }

        @Test(expected = IllegalStateException.class)
        public void testStandaloneCommitException()
        {
            getLabkeyScope().commitTransaction();
        }

        @Test(expected = IllegalStateException.class)
        public void testNestedFailureCondition()
        {
            try (Transaction t = getLabkeyScope().ensureTransaction())
            {
                assertTrue(getLabkeyScope().isTransactionActive());
                //noinspection EmptyTryBlock
                try (Transaction t2 = getLabkeyScope().ensureTransaction())
                {
                    // Intentionally miss a call to commit!
                }
                // Should already be rolled back because the inner transaction never called commit() before it was closed
                assertFalse(getLabkeyScope().isTransactionActive());
                // This call should cause an IllegalStateException
                t.commit();
            }
        }

        @Test(expected = IllegalStateException.class)
        public void testNestedMissingClose()
        {
            try
            {
                try (Transaction t = getLabkeyScope().ensureTransaction())
                {
                    assertTrue(getLabkeyScope().isTransactionActive());
                    Transaction t2 = getLabkeyScope().ensureTransaction();
                    t2.commit();
                    // Intentionally don't call t2.close(), make sure we blow up with an IllegalStateException
                    t.commit();
                }
            }
            finally
            {
                assertFalse(getLabkeyScope().isTransactionActive());
            }
        }
    }
}
