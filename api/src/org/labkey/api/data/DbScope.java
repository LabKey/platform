/*
 * Copyright (c) 2005-2013 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheTimeChooser;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.MemTracker;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;

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
    private static final Logger _log = Logger.getLogger(DbScope.class);
    private static final ConnectionMap _initializedConnections = newConnectionMap();
    private static final Map<String, DbScope> _scopes = new LinkedHashMap<>();
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

    private static final Map<Thread, Thread> _sharedConnections = new WeakHashMap<>();

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
                assert MemTracker.remove(_dialect);
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

        t._closesToIgnore++;
        if (t.decrement())
        {
            Connection conn = t.getConnection();
            try
            {
                conn.commit();
                conn.setAutoCommit(true);
    //            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                conn.close();
                synchronized (_transaction)
                {
                    _transaction.remove(getEffectiveThread());
                }
                t.runCommitTasks();
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
        return getCurrentTransaction() != null;
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

        // This works for Tomcat JDBC Connection Pool
        if (conn instanceof PooledConnection)
        {
            try
            {
                return ((PooledConnection) conn).getConnection();
            }
            catch (SQLException e)
            {
                _log.error("Attempt to retrieve underlying connection failed", e);
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
                _log.warn("error releasing connection: " + e.getMessage(), e);
            }
        }
    }


    public void closeConnection()
    {
        TransactionImpl t = getCurrentTransactionImpl();
        if (null != t)
        {
            if (t._closesToIgnore == 0)
            {
                if (!t.decrement())
                {
                    t._aborted = true;
                }

                t.closeConnection();

                synchronized (_transaction)
                {
                    _transaction.remove(getEffectiveThread());
                }
            }
            else
            {
                t._closesToIgnore--;
            }
            if (t._closesToIgnore < 0)
            {
                throw new IllegalStateException("Popped too many closes from the stack");
            }
        }
    }


    public SqlDialect getSqlDialect()
    {
        return _dialect;
    }


    // Default value of HOUR allows regular updates to external schemas; LabKey scope overrides this with a much longer expiration
    protected long getCacheDefaultTimeToLive()
    {
        return CacheManager.HOUR;
    }


    protected @Nullable CacheTimeChooser<String> getTableCacheTimeChooser()
    {
        return null;
    }


    protected @Nullable CacheTimeChooser<String> getSchemaCacheTimeChooser()
    {
        return null;
    }


    public @NotNull DbSchema getSchema(String schemaName)
    {
        return _schemaCache.get(schemaName);
    }


    // Each scope holds the cache for all its tables.  This makes it easier to 1) configure that cache on a per-scope
    // basis and 2) invalidate schemas and their tables together
    public SchemaTableInfo getTable(DbSchema schema, String tableName)
    {
        return _tableCache.get(schema, tableName);
    }


    // Query the JDBC metadata for a list of all schemas in this database. Not used in the common case, but useful
    // for testing and as a last resort when a requested schema can't be found.
    public Collection<String> getSchemaNames() throws SQLException
    {
        Connection conn = null;

        try
        {
            conn = getConnection();
            DatabaseMetaData dbmd = conn.getMetaData();

            try (ResultSet rs = getSchemaNameResultSet(dbmd))
            {
                final Collection<String> schemaNames = new LinkedList<>();

                new ResultSetSelector(this, rs, null).forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet rs) throws SQLException
                    {
                        schemaNames.add(rs.getString(1).trim());
                    }
                });

                return schemaNames;
            }
        }
        finally
        {
            if (null != conn && !isTransactionActive())
                conn.close();
        }
    }


    private ResultSet getSchemaNameResultSet(DatabaseMetaData dbmd) throws SQLException
    {
        return getSqlDialect().treatCatalogsAsSchemas() ? dbmd.getCatalogs() : dbmd.getSchemas();
    }


    // External data source case: no schema.xml file to check, no isStale() check (for now)
    protected @NotNull DbSchema loadSchema(String schemaName) throws Exception
    {
        return DbSchema.createFromMetaData(schemaName, this);
    }


    // Invalidates schema and all its tables
    public void invalidateSchema(String schemaName)
    {
        _schemaCache.remove(schemaName);
        invalidateAllTables(schemaName);
    }


    // Invalidates all tables in this schema
    void invalidateAllTables(String schemaName)
    {
        _tableCache.removeAllTables(schemaName);
    }

    // Invalidates a single table in this schema
    public void invalidateTable(DbSchema schema, String table)
    {
        _tableCache.remove(schema, table);
    }


    /**
     * If a transaction is active, the task is run after it's committed. If not, it's run immediately and synchronously.
     *
     * The tasks are put into a LinkedHashSet, so they'll run in order, but we will avoid running identical tasks
     * multiple times. Make sure you have implemented hashCode() and equals() on your task if you want to only run it
     * once per transaction.
     */
    public void addCommitTask(Runnable task)
    {
        Transaction t = getCurrentTransaction();

        if (null == t)
        {
            task.run();
        }
        else
        {
            t.addCommitTask(task);
        }
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

            // Put labkey data source first, followed by all others in alphabetical order
            Set<String> dsNames = new LinkedHashSet<>();
            dsNames.add(labkeyDsName);

            for (String dsName : dataSources.keySet())
                if (!dsName.equals(labkeyDsName))
                    dsNames.add(dsName);

            for (String dsName : dsNames)
            {
                try
                {
                    DbScope scope = dsName.equals(labkeyDsName) ? new LabKeyScope(dsName, dataSources.get(dsName)) : new DbScope(dsName, dataSources.get(dsName));
                    scope.getSqlDialect().prepareNewDbScope(scope);
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
                    createDataBase(dialect, props.getUrl(), props.getUsername(), props.getPassword());
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


    public static void createDataBase(SqlDialect dialect, String url, String username, String password) throws ServletException
    {
        Connection conn = null;
        PreparedStatement stmt = null;

        String dbName = dialect.getDatabaseName(url);

        _log.info("Attempting to create database \"" + dbName + "\"");

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
            _log.error("Create database failed, SQL: " + createSql, e);
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


    public boolean isLabKeyScope()
    {
        return this == getLabkeyScope();
    }


    public boolean isModuleSchema(String name)
    {
        return false;
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
                        t.closeConnection();
                    }
                    catch (Exception x)
                    {
                        _log.error("Failure forcing connection close on " + scope, x);
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

    public interface Transaction extends AutoCloseable
    {
        public void addCommitTask(Runnable runnable);
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
        // A set so that we can coalesce identical tasks and avoid duplicating the effort
        private final Set<Runnable> _commitTasks = new LinkedHashSet<>();
        private List<List<Lock>> _locks = new ArrayList<>();
        private boolean _aborted = false;
        private int _closesToIgnore = 0;

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

        public void addCommitTask(Runnable task)
        {
            boolean added = _commitTasks.add(task);

            if (!added)
                _log.debug("Skipping duplicate runnable: " + task.toString());
        }

        public Connection getConnection()
        {
            return _conn;
        }

        private void clearCommitTasks()
        {
            _commitTasks.clear();
            closeCaches();
        }

        @Override
        public void close()
        {
            DbScope.this.closeConnection();
        }

        @Override
        public void commit()
        {
            DbScope.this.commitTransaction();
        }

        private void runCommitTasks()
        {
            while (!_commitTasks.isEmpty())
            {
                Iterator<Runnable> i = _commitTasks.iterator();
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
                _log.error("Failed to close connection", e);
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
                    SqlExecutor executor = new SqlExecutor(scope, conn);
                    executor.setLogLevel(Level.OFF);  // We're about to generate a lot of SQLExceptions
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


    // Test that a transaction in one scope doesn't affect other scopes. Start a transaction in the labkeyScope
    // and then SELECT 10 rows from a random table in a random schema in every other datasource.
    public static class MultiScopeTransactionTestCase extends Assert
    {
        @Test
        public void test() throws SQLException
        {
            List<TableInfo> tablesToTest = new LinkedList<>();

            for (DbScope scope : DbScope.getDbScopes())
            {
                SqlDialect dialect = scope.getSqlDialect();
                Collection<String> schemaNames = new LinkedList<>();

                for (String schemaName : scope.getSchemaNames())
                    if (!dialect.isSystemSchema(schemaName))
                        schemaNames.add(schemaName);

                if (schemaNames.isEmpty())
                    continue;

                DbSchema schema = scope.getSchema(pickRandomElement(schemaNames));
                Collection<String> tableNames = schema.getTableNames();
                List<TableInfo> tables = new ArrayList<>(tableNames.size());

                for (String name : tableNames)
                {
                    TableInfo table = schema.getTable(name);

                    if (null == table)
                        _log.error("Table is null: " + schema.getName() + "." + name);
                    else if (table.getTableType() != DatabaseTableType.NOT_IN_DB)
                        tables.add(table);
                }

                if (tables.isEmpty())
                    continue;

                tablesToTest.add(pickRandomElement(tables));
            }

            DbScope labkeyScope = DbScope.getLabkeyScope();

            try
            {
                labkeyScope.ensureTransaction();

                for (TableInfo table : tablesToTest)
                {
                    TableSelector selector = new TableSelector(table);
                    selector.setMaxRows(10);
                    selector.getMapCollection();
                }
            }
            finally
            {
                labkeyScope.closeConnection();
            }
        }


        private <E> E pickRandomElement(Collection<E> collection)
        {
            int size = collection.size();
            assert size > 0;

            Iterator<E> iter = collection.iterator();
            int i = new Random().nextInt(size);
            E element;

            do
            {
                element = iter.next();
            }
            while (i-- > 0);

            return element;
        }
    }
}
