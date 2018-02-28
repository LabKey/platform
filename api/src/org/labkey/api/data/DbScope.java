/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.TestContext;
import org.labkey.data.xml.TablesDocument;
import org.springframework.dao.DeadlockLoserDataAccessException;

import javax.servlet.ServletContext;
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
import java.sql.Driver;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

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
    private static final ModuleResourceCache<Map<String, TablesDocument>> SCHEMA_XML_CACHE =
        ModuleResourceCaches.create("Parsed schema XML metadata", new SchemaXmlCacheHandler(), ResourceRootProvider.getStandard(QueryService.MODULE_SCHEMAS_PATH));

    private static DbScope _labkeyScope = null;

    private final String _dsName;
    private final String _displayName;
    private final DataSource _dataSource;
    private final @Nullable String _databaseName;    // Possibly null, e.g., for SAS datasources
    private final String _URL;
    private final String _databaseProductName;
    private final String _databaseProductVersion;
    private final String _driverName;
    private final String _driverVersion;
    private final DbSchemaCache _schemaCache;
    private final SchemaTableInfoCache _tableCache;
    private final Map<Thread, List<TransactionImpl>> _transaction = new WeakHashMap<>();
    private final DataSourceProperties _props;

    private SqlDialect _dialect;

    public interface TransactionKind
    {
        /** A short description of what this transactions usage scenario is */
        @NotNull
        String getKind();

        /**
         * If true, any Locks acquired as part of initializing the DbScope.Transaction will not be released until the
         * outer-most layer of the transaction has completed (either by committing or closing the connection).
         */
        boolean isReleaseLocksOnFinalCommit();
    }


    /* marker interface */
    public interface ServerLock extends Lock
    {
        @Override
        void lock();

        @Override
        default void lockInterruptibly()
        {
            lock();
        }

        @Override
        default boolean tryLock()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        default boolean tryLock(long time, @NotNull TimeUnit unit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        default void unlock()
        {
            /* noop, release with commit/rollback */
        }

        @NotNull
        @Override
        default Condition newCondition()
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class ServerNoopLock implements ServerLock
    {
        @Override
        public void lock()
        {
            /* do nothing */
        }
    }


    public static final TransactionKind NORMAL_TRANSACTION_KIND = new TransactionKind()
    {
        @NotNull
        public String getKind()
        {
            return "NORMAL";
        }

        @Override
        public boolean isReleaseLocksOnFinalCommit()
        {
            return false;
        }
    };


    private static IllegalStateException createIllegalStateException(String message, @Nullable DbScope scope, @Nullable ConnectionWrapper conn)
    {
        StringBuilder sb = new StringBuilder();
        if (null != conn)
        {
            sb.append("SPID=").append(conn.getSPID()).append(" ");
        }
        if (null != scope)
        {
            sb.append("DbScope=").append(scope.getDisplayName()).append(" " );
        }
        sb.append(message);
        Throwable t = conn != null ? conn.getSuspiciousCloseStackTrace() : null;
        throw new IllegalStateException(sb.toString(), t);
    }


    public static final TransactionKind FINAL_COMMIT_UNLOCK_TRANSACTION_KIND = new TransactionKind()
    {
        @NotNull
        public String getKind()
        {
            return "NORMAL";
        }

        @Override
        public boolean isReleaseLocksOnFinalCommit()
        {
            return true;
        }
    };

    // Used only for testing
    protected DbScope()
    {
        _dsName = null;
        _displayName = null;
        _dataSource = null;
        _databaseName = null;
        _URL = null;
        _databaseProductName = null;
        _databaseProductVersion = null;
        _driverName = null;
        _driverVersion = null;
        _schemaCache = null;
        _tableCache = null;
        _props = null;
    }


    // Data source properties that administrators can specify in labkey.xml. To add support for a new property, simply
    // add a getter & setter to this bean, and then do something with the typed value in DbScope.
    public static class DataSourceProperties
    {
        private boolean _logQueries = false;
        private String _displayName = null;

        public DataSourceProperties()
        {
        }

        private static DataSourceProperties get(Map<String, String> map)
        {
            return map.isEmpty() ? new DataSourceProperties() : BeanObjectFactory.Registry.getFactory(DataSourceProperties.class).fromMap(map);
        }

        public boolean isLogQueries()
        {
            return _logQueries;
        }

        public void setLogQueries(boolean logQueries)
        {
            _logQueries = logQueries;
        }

        public @Nullable String getDisplayName()
        {
            return _displayName;
        }

        public void setDisplayName(String displayName)
        {
            _displayName = displayName;
        }
    }


    // Standard DbScope constructor. Attempt a (non-pooled) connection to the datasource to gather meta data properties.
    // We don't use DbSchema or normal pooled connections here because failed connections seem to get added into the pool.
    public DbScope(String dsName, DataSource dataSource, DataSourceProperties props) throws ServletException, SQLException
    {
        try (Connection conn = dataSource.getConnection())
        {
            DatabaseMetaData dbmd = conn.getMetaData();

            try
            {
                _dialect = SqlDialectManager.getFromMetaData(dbmd, true, isPrimaryDataSource(dsName));
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
            _displayName = null != props.getDisplayName() ? props.getDisplayName() : extractDisplayName(_dsName);
            _dataSource = dataSource;
            _databaseName = _dialect.getDatabaseName(_dsName, _dataSource);
            _URL = dbmd.getURL();
            _databaseProductName = dbmd.getDatabaseProductName();
            _databaseProductVersion = dbmd.getDatabaseProductVersion();
            _driverName = dbmd.getDriverName();
            _driverVersion = dbmd.getDriverVersion();
            _props = props;
            _schemaCache = new DbSchemaCache(this);
            _tableCache = new SchemaTableInfoCache(this);
        }
    }

    private static String extractDisplayName(String dsName)
    {
        if (dsName.endsWith("DataSource"))
            return dsName.substring(0, dsName.length() - 10);
        else
            return dsName;
    }


    public String toString()
    {
        return getDataSourceName();
    }


    public String getDataSourceName()
    {
        return _dsName;
    }

    public String getDisplayName()
    {
        return _displayName;
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

    public DataSourceProperties getProps()
    {
        return _props;
    }

    /**
     * Ensures that there is an active database transaction. If one is already in progress for this DbScope, it is
     * joined (and a counter is incremented) such that the outer-most commit() attempt actually performs the commit.
     *
     * The preferred usage pattern is:
     * <pre>
     *     try (DbScope.Transaction transaction = scope.ensureTransaction()) {
     *         // Do some database work
     *         transaction.commit();
     *     } // Transaction.close() automatically invoked by auto-closeable.
     * </pre>
     *
     * Note that if there are multiple exit points from inside of the try-block (such as return statements),
     * they should all call commit() first if the transaction should be persisted.
     *
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction.
     */
    public Transaction ensureTransaction(Lock... locks)
    {
        return ensureTransaction(NORMAL_TRANSACTION_KIND, locks);
    }

    /**
     * Ensures that there is an active database transaction. If one is already in progress for this DbScope, it is
     * joined (and a counter is incremented) such that the outer-most commit() attempt actually performs the commit.
     * The preferred usage pattern is:
     * <pre>
     *     try (DbScope.Transaction transaction = scope.ensureTransaction()) {
     *         // Do some database work
     *         transaction.commit();
     *     } // Transaction.close() automatically invoked by auto-closeable.
     * </pre>
     *
     * Note that if there are multiple exit points from inside of the try-block (such as return statements),
     * they should all call commit() first if the transaction should be persisted.
     *
     * @param transactionKind indication of the purpose of this usage. If it doesn't match an existing transaction's kind,
     *                        a new Connection is handed out and used until it is committed/rolled back.
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction.
     */
    public Transaction ensureTransaction(TransactionKind transactionKind, Lock... locks)
    {
        // Note: it's theoretically possible to get 3 or more transactions on the transaction stack, if we call this
        //       with isPipelineStatus (false, true, false) or (true, false, true). This should not be done because
        //       it could cause a deadlock. We could change getCurrentTransactionImpl to take the flag and look past
        //       the top of the stack, but that would require a *lot* of places knowing which they are looking for,
        //       which is not feasible.
        if (isTransactionActive())
        {
            TransactionImpl transaction = getCurrentTransactionImpl();
            assert null != transaction;
            if (transactionKind.getKind().equals(transaction.getTransactionKind().getKind()))
            {
                transaction.increment(transactionKind.isReleaseLocksOnFinalCommit(), Arrays.asList(locks));
                return transaction;
            }
        }
        return beginTransaction(transactionKind, locks);
    }


    /**
     * Starts a new transaction using a new Connection.
     * The preferred usage pattern is:
     * <pre>
     *     try (DbScope.Transaction transaction = scope.beginTransaction()) {
     *         // Do some database work
     *         transaction.commit();
     *     } // Transaction.close() automatically invoked by auto-closeable.
     * </pre>
     *
     * Note that if there are multiple exit points from inside of the try-block (such as return statements),
     * they should all call commit() first if the transaction should be persisted.
     *
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction (or closeConnection() on the scope).
     */
    public Transaction beginTransaction(Lock... locks)
    {
        return beginTransaction(NORMAL_TRANSACTION_KIND, locks);
    }

    /**
     * Starts a new transaction using a new Connection.
     * The preferred usage pattern is:
     * <pre>
     *     try (DbScope.Transaction transaction = scope.beginTransaction()) {
     *         // Do some database work
     *         transaction.commit();
     *     } // Transaction.close() automatically invoked by auto-closeable.
     * </pre>
     *
     * Note that if there are multiple exit points from inside of the try-block (such as return statements),
     * they should all call commit() first if the transaction should be persisted.
     *
     * @param transactionKind indication of the purpose of this usage. If it doesn't match an existing transaction's kind,
     *                        a new Connection is handed out and used until it is committed/rolled back.
     * @param locks locks which should be acquired AFTER a connection has been retrieved from the connection pool,
     *              which prevents Java/connection pool deadlocks by always taking the locks in the same order.
     *              Locks will be released when close() is called on the Transaction (or closeConnection() on the scope).
     */
    public Transaction beginTransaction(TransactionKind transactionKind, Lock... locks)
    {
        ConnectionWrapper conn = null;
        TransactionImpl result = null;

        // try/finally ensures that closeConnection() works even if setAutoCommit() throws 
        try
        {
            conn = _getConnection(null);
            // we expect connections coming from the cache to be at a low transaction isolation level
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
                List<Lock> serverLocks = Arrays.stream(locks)
                        .filter((l) -> l instanceof ServerLock)
                        .collect(Collectors.toList());
                List<Lock> memoryLocks;
                if (serverLocks.isEmpty())
                    memoryLocks = Arrays.asList(locks);
                else
                    memoryLocks = Arrays.stream(locks)
                        .filter((l) -> !(l instanceof ServerLock))
                        .collect(Collectors.toList());

                result = new TransactionImpl(conn, transactionKind, memoryLocks);
                int stackDepth;
                synchronized (_transaction)
                {
                    List<TransactionImpl> transactions = _transaction.computeIfAbsent(getEffectiveThread(), k -> new ArrayList<>());
                    transactions.add(result);
                    stackDepth = transactions.size();
                }
                serverLocks.forEach(Lock::lock);
                if (stackDepth > 2)
                    LOG.info("Transaction stack for thread '" + getEffectiveThread().getName() + "' is " + stackDepth);
            }
        }

        return result;
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
        Transaction tx = getCurrentTransaction();
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
            List<TransactionImpl> transactions = _transaction.get(getEffectiveThread());
            return transactions == null ? null : transactions.get(transactions.size() - 1);
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


    static Connection getDelegate(Connection conn)
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
        try (Connection conn = _dataSource.getConnection())
        {
            Connection delegate = getDelegate(conn);
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
                LOG.info("There is/are " + _transaction.size() + " thread(s) holding a transaction for the data source '" + toString() + "':");
                for (Map.Entry<Thread, List<TransactionImpl>> entry : _transaction.entrySet())
                {
                    Thread thread = entry.getKey();
                    LOG.info("\t'" + thread.getName() + "', State = " + thread.getState());
                    if (thread.getState() == Thread.State.TERMINATED || thread.getState() == Thread.State.NEW)
                    {
                        for (TransactionImpl transaction : entry.getValue())
                        {
                            for (StackTraceElement stackTraceElement : transaction._creation.getStackTrace())
                            {
                                LOG.info("\t\t" + stackTraceElement.toString());
                            }
                            LOG.info("");
                        }
                    }
                    LOG.info("");
                }
            }
        }
    }

    private static final int spidUnknown = -1;

    protected ConnectionWrapper _getConnection(@Nullable Logger log) throws SQLException
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

        if (!conn.getAutoCommit())
            throw new ConfigurationException("A database connection is in an unexpected state: auto-commit is false. This indicates a configuration problem with the datasource definition or the database connection pool.");

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
                _dialect.prepareConnection(conn);
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

    public SqlDialect getSqlDialect()
    {
        return _dialect;
    }

    @NotNull
    // Load meta data from database and overlay schema.xml, if DbSchemaType requires it
    protected DbSchema loadSchema(String schemaName, DbSchemaType type) throws SQLException, IOException
    {
        LOG.debug("Loading DbSchema \"" + getDisplayName() + "." + schemaName + "\" (" + type.name() + ")");

        DbSchema schema = DbSchema.createFromMetaData(this, schemaName, type);
        // Consider:  Logging the cases where loadSchema is called in a transaction, because this causes a JDBC call that is vulnerable to deadlocking
        // if (schema.getScope().isTransactionActive())
        //        LOG.info("Loading IN TRAN");
        if (type.applyXmlMetaData())
            applyMetaDataXML(schema, schemaName);

        return schema;
    }

    private void applyMetaDataXML(DbSchema schema, String schemaName) throws IOException
    {
        // First try the canonical schema name (which could differ in casing from the requested name)
        Resource resource = schema.getSchemaResource();

        if (null == resource)
        {
            String displayName = DbSchema.getDisplayName(schema.getScope(), schemaName);
            LOG.info("no schema metadata xml file found for schema \"" + displayName + "\"");

            DbSchemaType type = ModuleLoader.getInstance().getSchemaTypeForSchemaName(schemaName);

            if (null != type && !type.applyXmlMetaData())
                LOG.info("Shouldn't be loading metadata for " + type.name() + " schema \"" + displayName + "\"");
        }
        else
        {
            String filename = resource.getName();

            // I don't like this... should either improve Resolver (add getModule()?) or revise getResource() to take a Resource
            Resolver resolver = resource.getResolver();
            assert resolver instanceof ModuleResourceResolver;
            Module module = ((ModuleResourceResolver) resolver).getModule();

            TablesDocument tablesDoc = SCHEMA_XML_CACHE.getResourceMap(module).get(filename);

            if (null != tablesDoc)
                schema.setTablesDocument(tablesDoc);
        }
    }


    public static @NotNull List<String> getSchemaNames(Module module)
    {
        return SCHEMA_XML_CACHE.getResourceMap(module).keySet().stream()
            .map(filename -> filename.substring(0, filename.length() - ".xml".length()))
            .collect(Collectors.toCollection(ArrayList::new));
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


    // Each scope holds the cache for all its tables. This makes it easier to 1) configure that cache on a per-scope
    // basis and 2) invalidate schemas and their tables together
    public <OptionType extends SchemaTableOptions> SchemaTableInfo getTable(OptionType options)
    {
        return _tableCache.get(options);
    }

    // Collection of schema names in this scope, in no particular order.
    public Collection<String> getSchemaNames()
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

    // DbSchema holds a hard-coded list of table names, so we need to invalidate the DbSchema to update this list.
    // Note that all other TableInfos remain cached; this is simply invalidating the schema info and reloading the
    // meta data XML. If this is too heavyweight, we could instead cache and invalidate the list of table names separate
    // from the DbSchema.
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
    @NotNull
    public <T extends Runnable> T addCommitTask(T task, CommitTaskOption firstOption, CommitTaskOption... additionalOptions)
    {
        Transaction t = getCurrentTransaction();

        if (null == t)
        {
            // No active transaction, so run the task immediately
            task.run();
            return task;
        }
        else
        {
            return t.addCommitTask(task, firstOption, additionalOptions);
        }
    }


    public static void initializeScopes(String labkeyDsName, Map<String, DataSource> dataSources)
    {
        synchronized (_scopes)
        {
            if (!_scopes.isEmpty())
                throw new IllegalStateException("DbScopes are already initialized");

            if (!dataSources.containsKey(labkeyDsName))
                throw new IllegalStateException(labkeyDsName + " DataSource not found");

            // Find all the external data sources required by module schemas; we attempt to create these databases
            Set<String> moduleDataSources = ModuleLoader.getInstance().getAllModuleDataSourceNames();

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
                            ModuleLoader.getInstance().ensureDatabase(dsName);
                        }
                        catch (Throwable t)
                        {
                            // Database creation failed, but the data source may still be useable for external schemas
                            // (e.g., a MySQL data source), so continue on and attempt to initialize the scope
                            LOG.info("Failed to create database", t);
                            addDataSourceFailure(dsName, t);
                        }
                    }

                    Map<String, String> dsProperties = new HashMap<>();
                    ServletContext ctx = ModuleLoader.getServletContext();

                    IteratorUtils.asIterator(ctx.getInitParameterNames()).forEachRemaining(name -> {
                        if (name.startsWith(dsName + ":"))
                            dsProperties.put(name.substring(name.indexOf(':') + 1), ctx.getInitParameter(name));
                    });

                    DataSourceProperties dsPropertiesBean = DataSourceProperties.get(dsProperties);
                    if (dsName.equals(labkeyDsName) && dsPropertiesBean.isLogQueries())
                    {
                        LOG.warn("Ignoring unsupported parameter in " + AppProps.getInstance().getWebappConfigurationFilename() + " to log queries for LabKey DataSource \"" + labkeyDsName + "\"");
                        dsPropertiesBean.setLogQueries(false);
                    }
                    addScope(dsName, dataSources.get(dsName), dsPropertiesBean);
                }
                catch (Exception e)
                {
                    // Server can't start up if it can't connect to the labkey data source
                    if (dsName.equals(labkeyDsName))
                    {
                        // Rethrow a ConfigurationException -- it includes important details about the failure
                        if (e instanceof ConfigurationException)
                            throw (ConfigurationException)e;

                        throw new ConfigurationException("Cannot connect to DataSource \"" + labkeyDsName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ". Server cannot start.", e);
                    }

                    // Failure to connect with any other datasource results in an error message, but doesn't halt startup  
                    LOG.error("Cannot connect to DataSource \"" + dsName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ". This DataSource will not be available during this server session.", e);
                    addDataSourceFailure(dsName, e);
                }
            }

            _labkeyScope = _scopes.get(labkeyDsName);

            if (null == _labkeyScope)
                throw new ConfigurationException("Cannot connect to DataSource \"" + labkeyDsName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ". Server cannot start.");

            _labkeyScope.getSqlDialect().prepareNewLabKeyDatabase(_labkeyScope);
        }
    }


    public static void addScope(String dsName, DataSource dataSource, DataSourceProperties props) throws ServletException, SQLException
    {
        DbScope scope = new DbScope(dsName, dataSource, props);
        scope.getSqlDialect().prepare(scope);

        synchronized (_scopes)
        {
            _scopes.put(dsName, scope);
        }
    }

    /** @return true if this is the name of the primary database for LabKey Server (labkeyDataSource or cpasDataSource */
    public static boolean isPrimaryDataSource(String dsName)
    {
        return ModuleLoader.LABKEY_DATA_SOURCE.equalsIgnoreCase(dsName) || ModuleLoader.CPAS_DATA_SOURCE.equalsIgnoreCase(dsName);
    }

    // Ensure we can connect to the specified datasource. If the connection fails with a "database doesn't exist" exception
    // then attempt to create the database. Return true if the database existed, false if it was just created. Throw if some
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
                // Load and prepare the JDBC driver
                @SuppressWarnings("unchecked")
                Class<Driver> driverClass = (Class<Driver>)Class.forName(props.getDriverClassName());
                dialect.prepareDriver(driverClass);

                // Create non-pooled connection... don't want to pool a failed connection
                conn = DriverManager.getConnection(props.getUrl(), props.getUsername(), props.getPassword());
                LOG.debug("Successful connection to \"" + dsName + "\" at " + props.getUrl());
                return true;        // Database already exists
            }
            catch (SQLException e)
            {
                if (dialect.isNoDatabaseException(e))
                {
                    createDataBase(dialect, props.getUrl(), props.getUsername(), props.getPassword(), isPrimaryDataSource(dsName));
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


    public static void createDataBase(SqlDialect dialect, String url, String username, String password, boolean primaryDataSource) throws ServletException
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
            dialect = SqlDialectManager.getFromMetaData(conn.getMetaData(), false, primaryDataSource);
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


    public static DbScope getLabKeyScope()
    {
        synchronized (_scopes)
        {
            return _labkeyScope;
        }
    }


    public boolean isLabKeyScope()
    {
        return this == getLabKeyScope();
    }

    /** Gets a DbScope based on the data source name from the Tomcat deployment descriptor */
    @Nullable
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

    /** Shuts down any connections associated with DbScopes that have been handed out to the current thread */
    public static void closeAllConnectionsForCurrentThread()
    {
        Collection<DbScope> scopes;
        synchronized (_scopes)
        {
            scopes = new ArrayList<>(_scopes.values());
        }

        for (DbScope scope : scopes)
        {
            TransactionImpl t = scope.getCurrentTransactionImpl();
            while (t != null)
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
                // We may have nested concurrent transactions for a given scope, so be sure we close them all
                t = scope.getCurrentTransactionImpl();
            }
        }
    }

    /**
     * Shut down connections and clear all environments
     */
    public static void finishedWithThread()
    {
        closeAllConnectionsForCurrentThread();
        QueryService.get().clearEnvironment();
    }

    /**
     * Causes any connections associated with the current thread to be used by the passed-in thread. This allows
     * an async thread to participate in the same transaction as the original HTTP request processing thread, for
     * example
     * @param asyncThread the thread that should use the database connections of the current thread
     * @return an AutoCloseable that will stops sharing the database connection with the other thread
     */
    public static ConnectionSharingCloseable shareConnections(final Thread asyncThread)
    {
        synchronized (_sharedConnections)
        {
            if (_sharedConnections.containsKey(asyncThread))
            {
                throw new IllegalStateException("Thread '" + asyncThread.getName() + "' is already sharing the connections of thread '" + _sharedConnections.get(asyncThread) + "'");
            }
            _sharedConnections.put(asyncThread, Thread.currentThread());
        }

        return new ConnectionSharingCloseable(asyncThread);
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
        PRECOMMIT
        {
            @Override
            protected Set<Runnable> getRunnables(DbScope.TransactionImpl transaction)
            {
                return transaction._preCommitTasks;
            }
        },
        /** Run after the main transaction has been committed, separate from the transaction itself */
        POSTCOMMIT
        {
            @Override
            protected Set<Runnable> getRunnables(DbScope.TransactionImpl transaction)
            {
                return transaction._postCommitTasks;
            }
        },
        /** Run after the transaction has been completely rolled back and abandoned, but not if it was committed */
        POSTROLLBACK
        {
            @Override
            protected Set<Runnable> getRunnables(DbScope.TransactionImpl transaction)
            {
                return transaction._postRollbackTasks;
            }
        },
        /** Run immediately. Useful for cache-clearing tasks, which often want to fire right away, as well as after the commit */
        IMMEDIATE
        {
            @Override
            protected Set<Runnable> getRunnables(DbScope.TransactionImpl transaction)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Runnable> T add(TransactionImpl transaction, T task)
            {
                task.run();
                return task;
            }
        };

        protected abstract Set<Runnable> getRunnables(DbScope.TransactionImpl transaction);

        public void run(DbScope.TransactionImpl transaction)
        {
            // Copy to avoid ConcurrentModificationExceptions
            Set<Runnable> tasks = new HashSet<>(getRunnables(transaction));

            for (Runnable task : tasks)
            {
                task.run();
            }

            transaction.closeCaches();
        }

        public <T extends Runnable> T add(TransactionImpl transaction, T task)
        {
            T addedObj = task;
            boolean added = getRunnables(transaction).add(task);
            for(Runnable r : getRunnables(transaction))
            {
                if (r.equals(task))
                {
                    addedObj = (T) r;
                    break;
                }
            }
            if (!added)
            {
                LOG.debug("Skipping duplicate runnable: " + task.toString());
            }
            return addedObj;
        }
    }

    public interface Transaction extends AutoCloseable
    {
        /*
         * @return  the task that was inserted or the existing object (equal to the runnable passed in) that will be run instead
         */
        @NotNull
        <T extends Runnable> T addCommitTask(@NotNull T runnable, @NotNull CommitTaskOption firstOption, CommitTaskOption... additionalOptions);
        @NotNull
        Connection getConnection();
        void close();
        void commit();

        /**
         * Commit the current transaction, running pre and post commit tasks, but don't close the connection
         * or remove transaction from thread pool. Effectively starts new transaction with same pre and post commit tasks.
         */
        void commitAndKeepConnection();

        boolean isAborted();

        String getId();
    }

    /** A dummy object for swap-in usage when no transaction is actually desired */
    public static final DbScope.Transaction NO_OP_TRANSACTION = new DbScope.Transaction()
    {
        @Override
        @NotNull
        public <T extends Runnable> T addCommitTask(@NotNull T runnable, @NotNull DbScope.CommitTaskOption firstOption, DbScope.CommitTaskOption... additionalOptions)
        {
            runnable.run();
            return runnable;
        }

        @Override
        public String getId()
        {
            return "-";
        }

        @Override
        @NotNull
        public Connection getConnection()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close()
        {
        }

        @Override
        public void commit()
        {
        }

        @Override
        public void commitAndKeepConnection()
        {
        }

        @Override
        public boolean isAborted()
        {
            return false;
        }
    };


    private void popCurrentTransaction()
    {
        synchronized (_transaction)
        {
            List<TransactionImpl> transactions = _transaction.get(getEffectiveThread());
            transactions.remove(transactions.size() - 1);
            if (transactions.isEmpty())
            {
                _transaction.remove(getEffectiveThread());
            }
        }
    }

    public static class ConnectionSharingCloseable implements AutoCloseable
    {
        private final Thread _asyncThread;

        public ConnectionSharingCloseable(Thread asyncThread)
        {
            _asyncThread = asyncThread;
        }

        @Override
        public void close()
        {
            synchronized (_sharedConnections)
            {
                _sharedConnections.remove(_asyncThread);
            }
        }
    }

    // Represents a single database transaction. Holds onto the Connection, the temporary caches to use during that
    // transaction, and the tasks to run immediately after commit to update the shared caches with removals.
    protected class TransactionImpl implements Transaction
    {
        private final String id = GUID.makeGUID();
        @NotNull
        private final ConnectionWrapper _conn;
        private final Map<DatabaseCache<?>, StringKeyCache<?>> _caches = new HashMap<>(20);

        // Sets so that we can coalesce identical tasks and avoid duplicating the effort
        private final Set<Runnable> _preCommitTasks = new LinkedHashSet<>();
        private final Set<Runnable> _postCommitTasks = new LinkedHashSet<>();
        private final Set<Runnable> _postRollbackTasks = new LinkedHashSet<>();

        private List<List<Lock>> _locks = new ArrayList<>();
        private boolean _aborted = false;
        private int _closesToIgnore = 0;
        private Throwable _creation = new Throwable();
        private final TransactionKind _transactionKind;

        TransactionImpl(@NotNull ConnectionWrapper conn, TransactionKind transactionKind)
        {
            this(conn, transactionKind, Collections.emptyList());
        }

        TransactionImpl(@NotNull ConnectionWrapper conn, TransactionKind transactionKind, List<Lock> extraLocks)
        {
            _conn = conn;
            _transactionKind = transactionKind;
            increment(transactionKind.isReleaseLocksOnFinalCommit(), extraLocks);
        }

        <ValueType> StringKeyCache<ValueType> getCache(DatabaseCache<ValueType> cache)
        {
            return (StringKeyCache<ValueType>)_caches.get(cache);
        }

        <ValueType> void addCache(DatabaseCache<ValueType> cache, StringKeyCache<ValueType> map)
        {
            _caches.put(cache, map);
        }

        @Override
        @NotNull
        public <T extends Runnable> T addCommitTask(@NotNull T task, @NotNull DbScope.CommitTaskOption firstOption, CommitTaskOption... additionalOptions)
        {
            T result = firstOption.add(this, task);
            for (CommitTaskOption taskOption : additionalOptions)
            {
                result = taskOption.add(this, task);
            }

            return result;
        }

        @NotNull
        public Connection getConnection()
        {
            return _conn;
        }

        private void clearCommitTasks()
        {
            _preCommitTasks.clear();
            _postCommitTasks.clear();
            _postRollbackTasks.clear();
            closeCaches();
        }

        @Override
        public String getId()
        {
            return id;
        }

        @Override
        public void close()
        {
            if (_closesToIgnore == 0)
            {
                boolean locksEmpty = decrement();
                if (!locksEmpty)
                {
                    _conn.markAsSuspiciouslyClosed();
                    _aborted = true;
                }

                closeConnection();

                if (locksEmpty)
                {
                    // Don't pop until locks are empty, because other closes have yet to occur
                    // and we want to use _abort to ensure no one tries to commit this transaction
                    popCurrentTransaction();

                    if (_aborted)
                    {
                        // Run this now that we've been disassociated with a potentially trashed connection
                        CommitTaskOption.POSTROLLBACK.run(this);
                        clearCommitTasks();
                    }
                }
            }
            else
            {
                _closesToIgnore--;
            }
            if (_closesToIgnore < 0)
            {
                throw createIllegalStateException("Popped too many closes from the stack",DbScope.this,_conn);
            }
        }

        @Override
        public void commit()
        {
            if (_aborted)
            {
                throw createIllegalStateException("Transaction has already been rolled back",DbScope.this,_conn);
            }
            if (_closesToIgnore > 0)
            {
                _closesToIgnore = 0;
                closeConnection();
                throw createIllegalStateException("Missing expected call to close after prior commit",DbScope.this,_conn);
            }

            _closesToIgnore++;
            if (decrement())
            {
                try
                {
                    try (Connection conn = getConnection())
                    {
                        CommitTaskOption.PRECOMMIT.run(this);
                        conn.commit();
                        conn.setAutoCommit(true);
                    }

                    popCurrentTransaction();

                    CommitTaskOption.POSTCOMMIT.run(this);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
        }

        @Override
        public void commitAndKeepConnection()
        {
            try
            {
                CommitTaskOption.PRECOMMIT.run(this);
                getConnection().commit();
                _caches.clear();
                CommitTaskOption.POSTCOMMIT.run(this);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        @Override
        public boolean isAborted()
        {
            return _aborted;
        }

        private void closeCaches()
        {
            for (StringKeyCache<?> cache : _caches.values())
                cache.close();
            _caches.clear();
        }

        /** @return whether we've reached zero and should therefore commit if that's the request, or false if we should defer to a future call*/
        public boolean decrement()
        {
            if (_locks.isEmpty())
            {
                throw createIllegalStateException("No transactions remain, can't decrement!", DbScope.this, _conn);
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

        public void increment(boolean releaseOnFinalCommit, List<Lock> extraLocks)
        {
            for (Lock extraLock : extraLocks)
            {
                extraLock.lock();
            }

            // Check if we're inside a nested transaction, and we want to hold the lock until the outermost layer is complete
            if (!_locks.isEmpty() && releaseOnFinalCommit)
            {
                // Add the new locks to the outermost set of locks
                _locks.get(0).addAll(extraLocks);
                // Add an empty list to this layer of the transaction
                _locks.add(new ArrayList<>());
            }
            else
            {
                _locks.add(new ArrayList<>(extraLocks));
            }
        }

        public TransactionKind getTransactionKind()
        {
            return _transactionKind;
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
    @TestWhen(TestWhen.When.BVT)
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
        public void testLabKeyScope()
        {
            DbScope scope = getLabKeyScope();
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

    public static class GroupConcatTestCase extends Assert
    {
        @Test
        public void testGroupConcat()
        {
            for (DbScope scope : getDbScopes())
            {
                SqlDialect dialect = scope.getSqlDialect();
                if (!dialect.supportsGroupConcat())
                    continue;

                boolean caseInsensitiveCollation = dialect.isSqlServer();

                testGroupConcat(scope, dialect, false, false, "x Y z z y");
                testGroupConcat(scope, dialect, true, false, caseInsensitiveCollation ? "x Y z" : "x y Y z");
                testGroupConcat(scope, dialect, false, true, "x y Y z z");
                testGroupConcat(scope, dialect, true, true, caseInsensitiveCollation ? "x Y z" : "x y Y z");
            }
        }

        private void testGroupConcat(DbScope scope, SqlDialect dialect, boolean distinct, boolean sorted, String expected)
        {
            String delimiter = " ";

            SQLFragment query = new SQLFragment("SELECT\n");
            query.append("  COUNT(*) AS NumRows,\n");
            query.append("  ").append(dialect.getGroupConcat(new SQLFragment("StrVal"), distinct, sorted, dialect.getStringHandler().quoteStringLiteral(delimiter))).append(" AS StrVals\n");
            query.append("FROM (\n");
            query.append("  VALUES\n");
            query.append("  (1, 'x'),\n");
            query.append("  (2, 'Y'),\n");
            query.append("  (3, 'z'),\n");
            query.append("  (4, 'z'),\n");
            query.append("  (5, 'y')\n");
            query.append(") AS t (IntVal, StrVal)\n");

            Map<String, Object> map = new SqlSelector(scope, query).getMap();
            Number numRows = (Number)map.get("NumRows");
            String strVals = (String)map.get("StrVals");

            assertEquals(5, numRows.intValue());
            assertEquals(expected, strVals);
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
                Collection<String> tableNames = DbSchemaType.Provisioned == type ? DbSchema.loadTableMetaData(scope, randomSchemaName).keySet() : schema.getTableNames();
                TableInfo tinfo = pickRandomTable(schema, tableNames);

                if (null != tinfo)
                    tablesToTest.add(tinfo);
            }

            try (Transaction ignored = DbScope.getLabKeyScope().ensureTransaction())
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
            try (Transaction t = getLabKeyScope().ensureTransaction())
            {
                assertTrue(getLabKeyScope().isTransactionActive());
                t.commit();
                assertFalse(getLabKeyScope().isTransactionActive());
                t.close();
                t.close();
            }
        }

        @Test
        public void testLockReleasedCommitted()
        {
            ReentrantLock lock = new ReentrantLock();
            ReentrantLock lock2 = new ReentrantLock();
            try (Transaction t = getLabKeyScope().ensureTransaction(lock))
            {
                assertEquals("Lock should be singly held", 1, lock.getHoldCount());
                try (Transaction t2 = getLabKeyScope().ensureTransaction(lock, lock2))
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
                try (Transaction t = getLabKeyScope().ensureTransaction(lock))
                {
                    try
                    {
                        assertEquals("Lock should be singly held", 1, lock.getHoldCount());
                        try (Transaction t2 = getLabKeyScope().ensureTransaction(lock, lock2))
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
            try (Transaction t = getLabKeyScope().ensureTransaction())
            {
                Connection connection = t.getConnection();
                assertTrue(getLabKeyScope().isTransactionActive());
                try (Transaction t2 = getLabKeyScope().ensureTransaction())
                {
                    assertTrue(getLabKeyScope().isTransactionActive());
                    assertSame(connection, t2.getConnection());
                    try (Transaction t3 = getLabKeyScope().ensureTransaction())
                    {
                        assertTrue(getLabKeyScope().isTransactionActive());
                        assertSame(connection, t3.getConnection());
                        t3.commit();
                        assertTrue(getLabKeyScope().isTransactionActive());
                    }
                    assertSame(connection, t2.getConnection());
                    t2.commit();
                    assertTrue(getLabKeyScope().isTransactionActive());
                }
                t.commit();
                assertFalse(getLabKeyScope().isTransactionActive());
            }
            assertFalse(getLabKeyScope().isTransactionActive());
        }

        @Test(expected = IllegalStateException.class)
        public void testExtraCommit()
        {
            try (Transaction t = getLabKeyScope().ensureTransaction())
            {
                assertTrue(getLabKeyScope().isTransactionActive());
                t.commit();
                assertFalse(getLabKeyScope().isTransactionActive());
                // This call should cause an IllegalStateException, since we already committed the transaction
                t.commit();
            }
        }

        @Test(expected = IllegalStateException.class)
        public void testNestedFailureCondition()
        {
            try (Transaction t = getLabKeyScope().ensureTransaction())
            {
                assertTrue(getLabKeyScope().isTransactionActive());
                //noinspection EmptyTryBlock
                try (Transaction t2 = getLabKeyScope().ensureTransaction())
                {
                    // Intentionally miss a call to commit!
                }
                // Should be aborted because the inner transaction never called commit() before it was closed
                assertTrue(getLabKeyScope().isTransactionActive());
                assertTrue(getLabKeyScope().getCurrentTransactionImpl()._aborted);
                // This call should cause an IllegalStateException
                t.commit();
            }
        }

        @Test(expected = IllegalStateException.class)
        public void testNestedMissingClose()
        {
            try
            {
                try (Transaction t = getLabKeyScope().ensureTransaction())
                {
                    assertTrue(getLabKeyScope().isTransactionActive());
                    Transaction t2 = getLabKeyScope().ensureTransaction();
                    t2.commit();
                    // Intentionally don't call t2.close(), make sure we blow up with an IllegalStateException
                    t.commit();
                }
            }
            finally
            {
                assertFalse(getLabKeyScope().isTransactionActive());
            }
        }

        @Test(expected = IllegalStateException.class)
        public void testNestedMissingCommit()
        {
            try
            {
                try (Transaction t = getLabKeyScope().ensureTransaction())
                {
                    Connection c = t.getConnection();
                    assertTrue(getLabKeyScope().isTransactionActive());
                    try (Transaction t2 = getLabKeyScope().ensureTransaction())
                    {
                        // Intentionally don't call t2.commit();
                    }
                    try
                    {
                        c.prepareStatement("SELECT 1");
                        fail("Should have gotten a SQLException");
                    }
                    catch (SQLException e)
                    {
                        assertTrue("Wrong message", e.getMessage().contains("See nested exception for potentially suspect code"));
                        assertTrue("Wrong message", e.getCause().getMessage().contains("This connection may have been closed by a codepath that did not intend to leave it in this state"));
                    }
                    t.commit();
                }
            }
            finally
            {
                assertFalse(getLabKeyScope().isTransactionActive());
            }
        }

        @Test
        public void testMultipleTransactionKinds()
        {
            try (Transaction t = getLabKeyScope().ensureTransaction())
            {
                Connection connection = t.getConnection();
                assertTrue(getLabKeyScope().isTransactionActive());
                try (Transaction t2 = getLabKeyScope().ensureTransaction())
                {
                    assertSame(connection, t2.getConnection());
                    try (Transaction t3 = getLabKeyScope().ensureTransaction(new TransactionKind()
                    {
                        @NotNull
                        @Override
                        public String getKind()
                        {
                            return "PIPELINESTATUS";  // We can't really see PipelineStatus here, but just need something non-normal to test
                        }

                        @Override
                        public boolean isReleaseLocksOnFinalCommit()
                        {
                            return false;
                        }
                    }))
                    {
                        assertTrue(getLabKeyScope().isTransactionActive());
                        assertNotSame("Should have 2 connections", connection, t3.getConnection());
                        t3.commit();
                        assertTrue(getLabKeyScope().isTransactionActive());
                    }
                    assertSame(getLabKeyScope().getCurrentTransaction(), t2);
                    t2.commit();
                    assertTrue(getLabKeyScope().isTransactionActive());
                }
                t.commit();
                assertFalse(getLabKeyScope().isTransactionActive());
            }
            assertFalse(getLabKeyScope().isTransactionActive());
        }

        @Test
        public void testServerRowLock()
        {
            final User user  = TestContext.get().getUser();
            final Throwable[] bkgException = new Throwable[] {null};
            Throwable fgException = null;

            final Object notifier = new Object();

            Lock lockUser = new ServerPrimaryKeyLock(true, CoreSchema.getInstance().getTableInfoUsers(), user.getUserId());
            Lock lockHome = new ServerPrimaryKeyLock(true, CoreSchema.getInstance().getTableInfoContainers(), ContainerManager.getHomeContainer().getId());

            // let's try to intentionally cause a deadlock
            Thread bkg = new Thread()
            {
                @Override
                public void run()
                {
                    // lockHome should succeed fg has not locked this yet
                    try (Transaction txBg = CoreSchema.getInstance().getScope().ensureTransaction(lockHome))
                    {
                        synchronized (notifier)
                        {
                            notifier.notify();
                        }
                        // should block on fg thread, but we're not deadlocked yet
                        lockUser.lock();
                        txBg.commit();
                    }
                    catch (Throwable x)
                    {
                        bkgException[0] = x;
                    }
                }
            };


            // lockUser should succeed (bg has not even started yet)
            try (Transaction txFg = CoreSchema.getInstance().getScope().ensureTransaction(lockUser))
            {
                // wait for background to acquire 'home' lock
                synchronized (notifier)
                {
                    bkg.start();
                    // wait for bkg to acquire lockHome
                    notifier.wait(60*1000);
                }
                // try to acquire my second lock
                // this should cause a deadlock
                lockHome.lock();
                txFg.commit();
            }
            catch (Throwable x)
            {
                fgException = x;
            }
            finally
            {
                bkg.interrupt();
                try
                {
                    bkg.join();
                }
                catch (InterruptedException x)
                {
                }
            }

            assert bkgException[0] instanceof DeadlockLoserDataAccessException || fgException instanceof DeadlockLoserDataAccessException;
        }
    }
}
