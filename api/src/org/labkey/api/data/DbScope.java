/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.Cache;
import org.labkey.api.data.ConnectionWrapper.Closer;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialect.DataSourceProperties;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DeadlockPreventingException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.LoggerWriter;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SimpleLoggerWriter;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.data.xml.TablesDocument;
import org.springframework.dao.DeadlockLoserDataAccessException;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
    private static final Logger LOG = LogHelper.getLogger(DbScope.class, "Retrieving database connections and managing transactions");
    private static final ConnectionMap _initializedConnections = newConnectionMap();
    private static final Map<String, DbScopeLoader> _scopeLoaders = new LinkedHashMap<>();
    private static final Map<Thread, Thread> _sharedConnections = new WeakHashMap<>();
    private static final Map<String, Throwable> _dataSourceFailures = new ConcurrentHashMap<>();
    // Cache for schema metadata XML files, shared across the whole server
    private static final ModuleResourceCache<Map<String, TablesDocument>> SCHEMA_XML_CACHE =
        ModuleResourceCaches.create("Parsed schema XML metadata", new SchemaXmlCacheHandler(), ResourceRootProvider.getStandard(QueryService.MODULE_SCHEMAS_PATH));

    private static volatile DbScope _labkeyScope = null;

    private final DbScopeLoader _dbScopeLoader;
    private final @Nullable String _databaseName;    // Possibly null, e.g., for SAS datasources
    private final String _databaseProductName;
    private final String _databaseProductVersion;
    private final String _driverName;
    private final String _driverVersion;
    private final String _driverLocation;
    private final DbSchemaCache _schemaCache;
    private final SchemaTableInfoCache _tableCache;
    private final Map<Thread, List<TransactionImpl>> _transaction = new WeakHashMap<>();
    private final Map<Thread, ConnectionHolder> _threadConnections = new WeakHashMap<>();
    private final boolean _rds;
    private final String _escape; // LIKE escape character

    /**
     * Only useful for integration testing purposes to simulate a problem setting autoCommit on a connection and ensuring we
     * unwind properly. Added since we entered an infinite loop trying to unwind a partially setup
     * transaction previously.
     */
    private final static ReentrantLock AUTO_COMMIT_FAILURE_SIMULATOR = new ReentrantLock();

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
        default boolean isReleaseLocksOnFinalCommit() { return false; }
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
            lock();
            return true;
            // We don't really support tryLock() but pass through to lock() to help with our deadlock prevention
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


    public static final TransactionKind NORMAL_TRANSACTION_KIND = () -> "NORMAL";

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
        @Override
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

    // Used only for testing and internal marker
    protected DbScope()
    {
        _dbScopeLoader = null;
        _databaseName = null;
        _databaseProductName = null;
        _databaseProductVersion = null;
        _driverName = null;
        _driverVersion = null;
        _driverLocation = null;
        _schemaCache = null;
        _tableCache = null;
        _rds = false;
        _escape = null;
    }

    // Used only for testing
    public DbScope(String dsName, DataSource dataSource, LabKeyDataSourceProperties props) throws ServletException, SQLException
    {
        this(new DbScopeLoader(dsName, dataSource, props));
    }

    /**
     *  <p>Special LabKey-specific properties that administrators can add to labkey.xml and associate with a data source. To add support for a new property, simply
     *  add a getter & setter to this bean, and then do something with the typed value in DbScope.</p>
     *
     *  <p>Example usage of these properties:</p>
     *
     *  <p>{@code <Parameter name="hidraDataSource:LogQueries" value="true"/>}</p>
     */
    public static class LabKeyDataSourceProperties
    {
        private boolean _logQueries = false;
        private String _displayName = null;

        public LabKeyDataSourceProperties()
        {
        }

        private static LabKeyDataSourceProperties get(Map<String, String> map)
        {
            return map.isEmpty() ? new LabKeyDataSourceProperties() : BeanObjectFactory.Registry.getFactory(LabKeyDataSourceProperties.class).fromMap(map);
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
    public DbScope(DbScopeLoader loader) throws ServletException, SQLException
    {
        _dbScopeLoader = loader;

        try (Connection conn = getRawConnection(loader.getDsProps()))
        {
            DatabaseMetaData dbmd = conn.getMetaData();
            _databaseProductVersion = dbmd.getDatabaseProductVersion();

            try
            {
                _dialect = SqlDialectManager.getFromMetaData(dbmd, true, isPrimaryDataSource(getDataSourceName()));
                MemTracker.getInstance().remove(_dialect);
            }
            finally
            {
                Integer maxTotal = getDataSourceProperties().getMaxTotal();
                _databaseName = (null != _dialect ? _dialect.getDatabaseName(getDataSourceProperties()) : null);

                // Always log the attempt, even if DatabaseNotSupportedException, etc. occurs, to help with diagnosis
                LOG.info("Initializing DbScope with the following configuration:" +
                        "\n    DataSource Name:          " + getDbScopeLoader().getDsName() +
                        "\n    Server URL:               " + dbmd.getURL() +
                        "\n    Database Name:            " + _databaseName +
                        "\n    Database Product Name:    " + dbmd.getDatabaseProductName() +
                        "\n    Database Product Version: " + (null != _dialect ? _dialect.getProductVersion(_databaseProductVersion) : _databaseProductVersion) +
                        "\n    JDBC Driver Name:         " + dbmd.getDriverName() +
                        "\n    JDBC Driver Version:      " + dbmd.getDriverVersion() +
    (null != _dialect ? "\n    SQL Dialect:              " + _dialect.getClass().getSimpleName() : "") +
    (null != maxTotal ? "\n    Connection Pool Size:     " + maxTotal : ""));
            }

            _databaseProductName = dbmd.getDatabaseProductName();
            _driverName = dbmd.getDriverName();
            _driverVersion = dbmd.getDriverVersion();
            _driverLocation = determineDriverLocation();
            _schemaCache = new DbSchemaCache(this);
            _tableCache = new SchemaTableInfoCache(this);
            _rds = _dialect.isRds(this);
            _escape = dbmd.getSearchStringEscape();
        }
    }

    private String determineDriverLocation()
    {
        try
        {
            return getDelegateClass().getProtectionDomain().getCodeSource().getLocation().toString();
        }
        catch (Exception ignored)
        {
            return "UNKNOWN";
        }
    }

    public String toString()
    {
        return getDataSourceName();
    }

    private DbScopeLoader getDbScopeLoader()
    {
        return _dbScopeLoader;
    }

    public String getDataSourceName()
    {
        return getDbScopeLoader().getDsName();
    }

    public String getDisplayName()
    {
        return getDbScopeLoader().getDisplayName();
    }

    @JsonIgnore
    public DataSource getDataSource()
    {
        return getDbScopeLoader().getDataSource();
    }

    public @Nullable String getDatabaseName()
    {
        return _databaseName;
    }

    public String getDatabaseUrl()
    {
        try
        {
            return getDataSourceProperties().getUrl();
        }
        catch (ServletException e)
        {
            throw new RuntimeException("Unable to retrieve URL property", e);
        }
    }

    public String getDatabaseProductName()
    {
        return _databaseProductName;
    }

    public String getDatabaseProductVersion()
    {
        // Dialect may be able to provide more useful version information
        return _dialect.getProductVersion(_databaseProductVersion);
    }

    public String getDriverName()
    {
        return _driverName;
    }

    public String getDriverVersion()
    {
        return _driverVersion;
    }

    public String getDriverLocation()
    {
        return _driverLocation;
    }

    public LabKeyDataSourceProperties getLabKeyProps()
    {
        return getDbScopeLoader().getLabKeyProps();
    }

    @JsonIgnore // this contains password, don't show
    public DataSourceProperties getDataSourceProperties()
    {
        return getDbScopeLoader().getDsProps();
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

        boolean connectionSetupSuccessful = false;

        // try/finally ensures that closeConnection() works even if setAutoCommit() throws 
        try
        {
            conn = getPooledConnection(ConnectionType.Transaction, null);

            if (locks.length > 0 && locks[0] == AUTO_COMMIT_FAILURE_SIMULATOR)
            {
                throw new SQLException("For testing purposes - simulated autocommit setting failure");
            }

            LOG.debug("setAutoCommit(false)");
            conn.setAutoCommit(false);
            connectionSetupSuccessful = true;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (null != conn)
            {
                if (connectionSetupSuccessful)
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

                    boolean createdTransactionObject = false;
                    try
                    {
                        result = new TransactionImpl(conn, transactionKind, memoryLocks);
                        createdTransactionObject = true;

                        int stackDepth;
                        synchronized (_transaction)
                        {
                            List<TransactionImpl> transactions = _transaction.computeIfAbsent(getEffectiveThread(), k -> new ArrayList<>());
                            transactions.add(result);
                            stackDepth = transactions.size();
                        }
                        boolean serverLockSuccess = false;
                        try
                        {
                            serverLocks.forEach(Lock::lock);
                            serverLockSuccess = true;
                        }
                        finally
                        {
                            if (!serverLockSuccess)
                            {
                                // We're throwing an exception so the caller will never get the transaction object to
                                // be able to close it, so do it now
                                result.close();
                            }
                        }
                        if (stackDepth > 2)
                            LOG.info("Transaction stack for thread '" + getEffectiveThread().getName() + "' is " + stackDepth);
                    }
                    finally
                    {
                        if (!createdTransactionObject)
                        {
                            // We failed to create the Transaction object (perhaps because of problems acquiring
                            // the locks) - close the otherwise orphaned connection to avoid a leak
                            try { conn.close(); } catch (SQLException ignored) {}
                        }
                    }
                }
                else
                {
                    // Problem trying to set autocommit which resulted in a SQLException.
                    // Close the connection and bail out
                    try { conn.close(); } catch (SQLException ignored) {}
                }
            }
        }

        return result;
    }

    public interface RetryFn<ReturnType>
    {
        ReturnType exec(Transaction tx) throws DeadlockLoserDataAccessException, RuntimeSQLException;
    }

    /** Can be used to conveniently throw a typed exception out of executeWithRetry */
    public static class RetryPassthroughException extends RuntimeException
    {
        public RetryPassthroughException(@NotNull Exception x)
        {
            super(x);
        }

        public <T extends Throwable> void rethrow(Class<T> clazz) throws T
        {
            if (clazz.isAssignableFrom(getCause().getClass()))
                throw (T)getCause();
        }

        public <T extends Throwable> void throwRuntimeException() throws RuntimeException
        {
            throw UnexpectedException.wrap(getCause());
        }
    }

    /**
     * If there's a deadlock exception, retry two more times after a delay. Won't retry if we're already in a transaction
     * fn() should throw DeadlockLoserDataAccessException or RuntimeSQLException to get the retry behavior
     */
    public <ReturnType> ReturnType executeWithRetry(RetryFn<ReturnType> fn, Lock... extraLocks)
    {
        // don't retry if we're already in a transaction, it won't help
        ReturnType ret = null;
        int tries = isTransactionActive() ? 1 : 3;
        long delay = 100;
        RuntimeException lastException = null;
        for (var tri=0 ; tri < tries ; tri++ )
        {
            lastException = null;
            try (Transaction transaction = ensureTransaction(extraLocks))
            {
                ret = fn.exec(transaction);
                transaction.commit();
                break;
            }
            catch (DeadlockLoserDataAccessException dldae)
            {
                lastException = dldae;
                try { Thread.sleep((tri+1)*delay); } catch (InterruptedException ignored) {}
                LOG.info("Retrying operation after deadlock", new Throwable());
            }
            catch (RuntimeSQLException e)
            {
                lastException = e;
                if (!SqlDialect.isTransactionException(e))
                {
                    break;
                }
                try { Thread.sleep((tri+1)*delay); } catch (InterruptedException ignored) {}
                LOG.info("Retrying operation after deadlock", new Throwable());
            }
        }

        if (null != lastException)
            throw lastException;

        return ret;
    }


    private static Thread getEffectiveThread()
    {
        synchronized (_sharedConnections)
        {
            Thread result = _sharedConnections.get(Thread.currentThread());
            return Objects.requireNonNullElseGet(result, Thread::currentThread);
        }
    }


    public boolean isTransactionActive()
    {
        Transaction tx = getCurrentTransaction();
        return null != tx;
    }


    @JsonIgnore
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

    @JsonIgnore
    public Connection getConnection() throws SQLException
    {
        return getConnection(null);
    }

    public Connection getConnection(@Nullable Logger log) throws SQLException
    {
        Transaction t = getCurrentTransaction();
        final Connection conn;

        if (null == t)
            conn = getCurrentConnection(log);
        else
            conn = t.getConnection();

        return conn;
    }

    public enum ConnectionType
    {
        Pooled()
        {
            @Override
            void close(DbScope scope, Connection conn, Closer closer) throws SQLException
            {
                closer.close();
            }
        },
        Thread()
        {
            @Override
            void close(DbScope scope, Connection conn, Closer closer) throws SQLException
            {
                if (scope.getConnectionHolder().release(conn))
                {
                    closer.close();
                }
            }
        },
        Transaction()
        {
            @Override
            void close(DbScope scope, Connection conn, Closer closer) throws SQLException
            {
                closer.close();
            }
        };

        abstract void close(DbScope scope, Connection conn, Closer closer) throws SQLException;
    }

    private class ConnectionHolder
    {
        private int _refCount = 0;
        private Connection _conn;

        public ConnectionHolder()
        {
        }

        public synchronized Connection get(@Nullable Logger log) throws SQLException
        {
            if (0 == _refCount)
            {
                _conn = getPooledConnection(ConnectionType.Thread, log);
            }

            _refCount++;

            log(() -> 1 == _refCount ? "New connection [1]: " + _conn.toString() : "Existing connection [" + _refCount + "]: " + _conn.toString());
            log(() -> _refCount > 2 ? "ConnectionHolder RefCount: " + _refCount : null);

            return _conn;
        }

        public synchronized boolean release(Connection conn)
        {
            log(() -> 1 == _refCount ? "Releasing connection [1]: " + conn.toString() : "Attempting to decrease count of connection [" + _refCount + "]: " + conn.toString());

            if (_conn != conn)
                throw new IllegalStateException("Incorrect Connection: " + conn + " vs. " + _conn);

            if (_refCount <= 0)
                throw new IllegalStateException("Reference count is too low (" + _refCount + ") for " + _conn);

            _refCount--;

            if (0 == _refCount)
            {
                _conn = null;
            }

            return 0 == _refCount;
        }
    }

    private void log(Supplier<String> supplier)
    {
        if (LOG.isDebugEnabled())
        {
            String message = supplier.get();

            if (null != message)
                LOG.debug(message);
        }
    }

    private ConnectionHolder getConnectionHolder()
    {
        Thread thread = getEffectiveThread();

        // Synchronize just long enough to get a ConnectionHolder into the map
        synchronized (_threadConnections)
        {
            return _threadConnections.computeIfAbsent(thread, t->new ConnectionHolder());
        }
    }

    // Get the connection associated with this thread
    private Connection getCurrentConnection(@Nullable Logger log) throws SQLException
    {
        return getConnectionHolder().get(log);
    }

    /** Get a fresh connection directly from the pool... not part of the current transaction, not shared with the thread, etc. */
    @JsonIgnore
    public Connection getPooledConnection() throws SQLException
    {
        return getPooledConnection(ConnectionType.Pooled, null);
    }

    /** Create a new connection that completely bypasses the connection pool. */
    @JsonIgnore
    public Connection getUnpooledConnection() throws SQLException
    {
        try
        {
            Connection raw = getRawConnection(getDbScopeLoader().getDsProps());
            return new SimpleConnectionWrapper(raw, this);
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

    @JsonIgnore
    public Class getDelegateClass()
    {
        try (Connection conn = getDataSource().getConnection())
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
        logCurrentConnectionState(LOG);
    }

    public void logCurrentConnectionState(LoggerWriter log)
    {
        synchronized (_transaction)
        {
            log.info("Data source " + this +
                    ". Max connections: " + getDbScopeLoader().getDsProps().getMaxTotal() +
                    ", active: " + getDbScopeLoader().getDsProps().getNumActive() +
                    ", idle: " + getDbScopeLoader().getDsProps().getNumIdle() +
                    ", maxWaitMillis: " + getDbScopeLoader().getDsProps().getMaxWaitMillis());

            if (_transaction.isEmpty())
            {
                log.info("There are no threads holding connections for the data source '" + this + "'");
            }
            else
            {
                log.info("There is/are " + _transaction.size() + " thread(s) holding a transaction for the data source '" + this + "':");
                for (Map.Entry<Thread, List<TransactionImpl>> entry : _transaction.entrySet())
                {
                    Thread thread = entry.getKey();
                    log.info("\t'" + thread.getName() + "', State = " + thread.getState());
                    if (thread.getState() == Thread.State.TERMINATED || thread.getState() == Thread.State.NEW)
                    {
                        for (TransactionImpl transaction : entry.getValue())
                        {
                            for (StackTraceElement stackTraceElement : transaction._creation.getStackTrace())
                            {
                                log.info("\t\t" + stackTraceElement.toString());
                            }
                            log.info("");
                        }
                    }
                    log.info("");
                }
            }
        }
    }

    public void logCurrentConnectionState(@NotNull Logger log)
    {
        logCurrentConnectionState(new SimpleLoggerWriter(log));
    }

    private static final int spidUnknown = -1;

    protected ConnectionWrapper getPooledConnection(ConnectionType type, @Nullable Logger log) throws SQLException
    {
        Connection conn;

        try
        {
            conn = getDataSource().getConnection();
        }
        catch (SQLException e)
        {
            String message = "Can't create a database connection for data source " + getDbScopeLoader().getDsName();
            try
            {
                message += " to URL " + getDbScopeLoader().getDsProps().getUrl();
            }
            catch (ServletException ignored) {}
            throw new ConfigurationException(message, e);
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

        return new ConnectionWrapper(conn, this, spid, type, log);
    }

    /** Identical to conn.close() and try-with-resources on Connection, except it logs SQLException instead of throwing **/
    public void releaseConnection(Connection conn)
    {
        try
        {
            conn.close();
        }
        catch (SQLException e)
        {
            LOG.warn("Error releasing connection", e);
        }
    }

    @JsonIgnore
    public SqlDialect getSqlDialect()
    {
        return _dialect;
    }

    @NotNull
    // Load meta data from database and overlay schema.xml, if DbSchemaType requires it
    protected DbSchema loadSchema(String schemaName, DbSchemaType type) throws SQLException
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

    private void applyMetaDataXML(DbSchema schema, String schemaName)
    {
        TablesDocument tablesDoc = getSchemaXml(schema);

        if (null == tablesDoc)
        {
            String displayName = DbSchema.getDisplayName(schema.getScope(), schemaName);
            LOG.info("no schema metadata xml file found for schema \"" + displayName + "\"");

            DbSchemaType type = ModuleLoader.getInstance().getSchemaType(schema.getScope(), schemaName);

            if (null != type && !type.applyXmlMetaData())
                LOG.info("Shouldn't be loading metadata for " + type.name() + " schema \"" + displayName + "\"");
        }
        else
        {
            schema.setTablesDocument(tablesDoc);
        }
    }

    public static @Nullable TablesDocument getSchemaXml(DbSchema schema)
    {
        String filename = schema.getResourcePrefix() + ".xml";
        return SCHEMA_XML_CACHE.getResourceMap(schema.getModule()).get(filename);
    }

    // Return an unmodifiable, sorted list of schema names in this module
    public static @NotNull List<String> getSchemaNames(Module module)
    {
        // Don't use the cache until startup is complete. The cache registers file listeners with module references,
        // and that ends up "leaking" modules if we haven't yet pruned them based on supported database, etc.
        return getSchemaNames(module, ModuleLoader.getInstance().isStartupComplete());
    }

    private static List<String> getSchemaNames(Module module, boolean useCache)
    {
        return (useCache ? SCHEMA_XML_CACHE.getResourceMap(module).keySet() : SchemaXmlCacheHandler.getFilenames(module)).stream()
            .map(filename -> filename.substring(0, filename.length() - ".xml".length()))
            .sorted()
            .collect(Collectors.toUnmodifiableList());
    }

    // Verify that the two ways for determining schema names yield identical results
    public static class SchemaNameTestCase extends Assert
    {
        @Test
        public void testSchemaNames()
        {
            ModuleLoader.getInstance().getModules()
                .forEach(m->assertEquals(getSchemaNames(m, true), getSchemaNames(m, false)));
        }
    }

    @JsonIgnore
    public @NotNull DbSchema getSchema(String schemaName, DbSchemaType type)
    {
        return _schemaCache.get(schemaName, type);
    }


    // Get the special "labkey" schema created in each module data source
    @JsonIgnore
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
        QueryService.get().updateLastModified();
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
        QueryService.get().updateLastModified();
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

    // Enumerate each jdbc DataSource in labkey.xml and initialize them
    public static void initializeDataSources()
    {
        verifyTomcatLibJars();

        LOG.debug("Ensuring that all databases specified by data sources in webapp configuration xml are present");

        Map<String, DataSource> dataSources = new TreeMap<>(String::compareTo);

        String labkeyDsName;

        try
        {
            // Ensure that the labkeyDataSource (or cpasDataSource, for old installations) exists in
            // labkey.xml / cpas.xml and create the associated database if it doesn't already exist.
            labkeyDsName = ensureDatabase(ModuleLoader.LABKEY_DATA_SOURCE, ModuleLoader.CPAS_DATA_SOURCE);

            InitialContext ctx = new InitialContext();
            Context envCtx = (Context) ctx.lookup("java:comp/env");
            NamingEnumeration<Binding> iter = envCtx.listBindings("jdbc");

            while (iter.hasMore())
            {
                try
                {
                    Binding o = iter.next();
                    String dsName = o.getName();
                    DataSource ds = validate((DataSource) o.getObject(), dsName);
                    dataSources.put(dsName, ds);
                }
                catch (NamingException e)
                {
                    LOG.error("DataSources are not properly configured in " + AppProps.getInstance().getWebappConfigurationFilename() + ".", e);
                }
            }
        }
        catch (Exception e)
        {
            throw new ConfigurationException("DataSources are not properly configured in " + AppProps.getInstance().getWebappConfigurationFilename() + ".", e);
        }

        initializeScopes(labkeyDsName, dataSources);
    }

    // Reject data sources configured with the Tomcat JDBC connection pool, #42125
    private static DataSource validate(DataSource dataSource, String dsName) throws ServletException
    {
        String dataSourceClassName = dataSource.getClass().getName();
        if (!dataSourceClassName.equals("org.apache.tomcat.dbcp.dbcp2.BasicDataSource"))
        {
            String message;
            if (dataSourceClassName.equals("org.apache.tomcat.jdbc.pool.DataSource"))
            {
                message = "Tomcat JDBC connection pool is not supported;";
            }
            else
            {
                message = "Unknown DataSource implementation, \"" + dataSourceClassName + "\";";
            }

            throw new ServletException(message + " LabKey only supports the Commons DBCP connection pool. Please remove the \"factory\" attribute from the \"" + dsName + "\" DataSource definition.");
        }

        return dataSource;
    }

    private static final List<Predicate<String>> TOMCAT_LIB_PREDICATES = new CopyOnWriteArrayList<>();

    /**
     * Register a {@code Predicate<String>} that identifies filenames that aren't allowed in the {@code <tomcat>/lib}
     * directory. This is used to warn administrators about old, conflicting JDBC drivers that need to be removed from
     * Tomcat.
     * @param predicate a {@code Predicate<String>} whose {@code test(String filename)} method returns true if filename
     *        is not allowed in the {@code <tomcat>/lib} directory
     */
    public static void registerForbiddenTomcatFilenamePredicate(Predicate<String> predicate)
    {
        TOMCAT_LIB_PREDICATES.add(predicate);
    }

    // Verify that old JDBC drivers are not present in <tomcat>/lib -- they are now provided by the modules that manage them
    private static void verifyTomcatLibJars()
    {
        File lib = ModuleLoader.getTomcatLib();

        if (null != lib)
        {
            Predicate<String> aggregatePredicate = TOMCAT_LIB_PREDICATES.stream().reduce(x->false, Predicate::or);
            String[] existing = lib.list((dir, name) ->
                aggregatePredicate.test(name)
            );

            // Don't fail if we can't get a listing for the directory
            if (existing != null && existing.length > 0)
            {
                String path = FileUtil.getAbsoluteCaseSensitiveFile(lib).getAbsolutePath();
                throw new ConfigurationException("You must delete the following files from " + path + ": " + Arrays.toString(existing));
            }
        }
    }

    // For each name, look for a matching data source in labkey.xml. If found, attempt a connection and
    // create the database if it doesn't already exist, report any errors and return the name.
    private static String ensureDatabase(@NotNull String primaryName, String... alternativeNames) throws NamingException, ServletException
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
                dataSource = validate((DataSource)envCtx.lookup("jdbc/" + dsName), dsName);
                break;
            }
            catch (NameNotFoundException e)
            {
                // Name not found is fine (for now); keep looping through alternative names
            }
            catch (NamingException e)
            {
                throw new ConfigurationException("Failed to load DataSource \"" + dsName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() +
                    ". This could be caused by an attempt to use the Tomcat JDBC connection pool, which is not supported. Please remove the \"factory\" attribute from this DataSource definition.", e);
            }
        }

        if (null == dataSource)
            throw new ConfigurationException("You must have a DataSource named \"" + primaryName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ".");

        ensureDataBase(dsName, dataSource);

        return dsName;
    }

    private static void initializeScopes(String labkeyDsName, Map<String, DataSource> dataSources)
    {
        synchronized (_scopeLoaders)
        {
            if (!_scopeLoaders.isEmpty())
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
                // Attempt to create databases in data sources required by modules
                if (moduleDataSources.contains(dsName))
                {
                    try
                    {
                        ensureDatabase(dsName);
                    }
                    catch (Throwable t)
                    {
                        // Database creation failed, but the data source may still be usable for external schemas
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

                LabKeyDataSourceProperties dsPropertiesBean = LabKeyDataSourceProperties.get(dsProperties);
                if (dsName.equals(labkeyDsName) && dsPropertiesBean.isLogQueries())
                {
                    LOG.warn("Ignoring unsupported parameter in " + AppProps.getInstance().getWebappConfigurationFilename() + " to log queries for LabKey DataSource \"" + labkeyDsName + "\"");
                    dsPropertiesBean.setLogQueries(false);
                }
                addScope(dsName, dataSources.get(dsName), dsPropertiesBean);
            }

            _labkeyScope = getDbScope(labkeyDsName);

            if (null == _labkeyScope)
                throw new ConfigurationException("Cannot connect to DataSource \"" + labkeyDsName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ". Server cannot start.");

            _labkeyScope.getSqlDialect().prepareNewLabKeyDatabase(_labkeyScope);
        }
    }

    public static void addScope(String dsName, DataSource dataSource, LabKeyDataSourceProperties props)
    {
        DbScopeLoader loader = new DbScopeLoader(dsName, dataSource, props);

        synchronized (_scopeLoaders)
        {
            _scopeLoaders.put(dsName, loader);
        }
    }

    /** @return true if this is the name of the primary database for LabKey Server (labkeyDataSource or cpasDataSource */
    public static boolean isPrimaryDataSource(String dsName)
    {
        return ModuleLoader.LABKEY_DATA_SOURCE.equalsIgnoreCase(dsName) || ModuleLoader.CPAS_DATA_SOURCE.equalsIgnoreCase(dsName);
    }

    public boolean isRds()
    {
        return _rds;
    }

    public String getDatabaseSearchStringEscape()
    {
        return _escape;
    }

    // Ensure we can connect to the specified datasource. If the connection fails with a "database doesn't exist" exception
    // then attempt to create the database. Return true if the database existed, false if it was just created. Throw if some
    // other exception occurs (e.g., connection fails repeatedly with something other than "database doesn't exist" or database
    // can't be created.)
    public static boolean ensureDataBase(String dsName, DataSource ds) throws ServletException
    {
        Connection conn = null;
        DataSourceProperties props = new DataSourceProperties(dsName, ds);

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
                conn = getRawConnection(props);
                LOG.debug("Successful connection to \"" + dsName + "\" at " + props.getUrl());
                return true;        // Database already exists
            }
            catch (SQLException e)
            {
                if (dialect.isNoDatabaseException(e))
                {
                    createDataBase(dialect, props, isPrimaryDataSource(dsName));
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

    // Establish a direct data source connection that bypasses the connection pool
    private static Connection getRawConnection(DataSourceProperties props) throws ServletException, SQLException
    {
        return getRawConnection(props.getUrl(), props);
    }

    // Establish a direct connection to the specified URL using the data source's driver and credentials. This bypasses the connection pool.
    private static Connection getRawConnection(String url, DataSourceProperties props) throws ServletException, SQLException
    {
        Driver driver;
        Properties info;
        try
        {
            @SuppressWarnings("unchecked")
            Class<Driver> driverClass = (Class<Driver>)Class.forName(props.getDriverClassName());
            driver = driverClass.getConstructor().newInstance();
            info = new Properties();
            if (props.getUsername() != null)
            {
                info.put("user", props.getUsername());
            }
            if (props.getPassword() != null)
            {
                info.put("password", props.getPassword());
            }
        }
        catch (Exception e)
        {
            throw new ServletException("Unable to retrieve data source properties", e);
        }

        return driver.connect(url, info);
    }

    private static void createDataBase(SqlDialect dialect, DataSourceProperties props, boolean primaryDataSource) throws ServletException
    {
        String url = props.getUrl();
        String dbName = dialect.getDatabaseName(url);

        LOG.info("Attempting to create database \"" + dbName + "\"");

        String masterUrl = StringUtils.replace(url, dbName, dialect.getMasterDataBaseName());
        String createSql = "(undefined)";

        try (Connection conn = getRawConnection(masterUrl, props))
        {
            // Get version-specific dialect; don't log version warnings.
            dialect = SqlDialectManager.getFromMetaData(conn.getMetaData(), false, primaryDataSource);
            createSql = dialect.getCreateDatabaseSql(dbName);

            try (PreparedStatement stmt = conn.prepareStatement(createSql))
            {
                stmt.execute();
            }
        }
        catch (SQLException e)
        {
            LOG.error("Create database failed, SQL: " + createSql, e);
            dialect.handleCreateDatabaseException(e);
        }

        LOG.info("Database \"" + dbName + "\" created");
    }


    // Store the initial failure message for each data source
    static void addDataSourceFailure(String dsName, Throwable t)
    {
        if (!_dataSourceFailures.containsKey(dsName))
            //noinspection ThrowableResultOfMethodCallIgnored
            _dataSourceFailures.put(dsName, t);
    }


    @JsonIgnore
    public static @Nullable Throwable getDataSourceFailure(String dsName)
    {
        return _dataSourceFailures.get(dsName);
    }


    public static DbScope getLabKeyScope()
    {
        return _labkeyScope;
    }


    public boolean isLabKeyScope()
    {
        return this == getLabKeyScope();
    }

    /** Gets a DbScope based on the data source name from the Tomcat deployment descriptor */
    @Nullable
    public static DbScope getDbScope(String dsName)
    {
        DbScopeLoader loader;

        synchronized (_scopeLoaders)
        {
            loader = _scopeLoaders.get(dsName);
        }

        return null != loader ? loader.get() : null;
    }

    private static @NotNull Collection<DbScopeLoader> getLoaders()
    {
        synchronized (_scopeLoaders)
        {
            return new LinkedList<>(_scopeLoaders.values());
        }
    }

    /**
     * Ensures that initialization has been attempted on all DbScopes and returns those that were successfully initialized
     * @return A collection of DbScopes
     */
    public static @NotNull Collection<DbScope> getDbScopes()
    {
        return getLoaders().stream()
            .map(DbScopeLoader::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Some DbScopes shouldn't be exercised by junit tests (e.g., an external data source connected to LabKey Server via
     * the PostgreSQL wire protocol)
     * @return A collection of DbScopes that are suitable for testing
     */
    public static @NotNull Collection<DbScope> getDbScopesToTest()
    {
        return getDbScopes().stream()
            .filter(scope->scope.getSqlDialect().shouldTest())
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all DbScopes that have been successfully initialized, ignoring those that haven't been initialized yet
     * @return A collection of initialized DbScopes
     */
    public static @NotNull Collection<DbScope> getInitializedDbScopes()
    {
        return getLoaders().stream()
            .map(DbScopeLoader::getIfPresent)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
    }

    /** Shuts down any connections associated with DbScopes that have been handed out to the current thread */
    public static void closeAllConnectionsForCurrentThread()
    {
        for (DbScope scope : getInitializedDbScopes())
        {
            TransactionImpl t = scope.getCurrentTransactionImpl();
            int count = 0;
            while (t != null)
            {
                if (count++ > 100)
                {
                    // Avoid getting into an infinite loop if someone's messed up the transaction stack
                    LOG.error("Aborting trying to close connections after processing " + count + " transaction objects");
                    break;
                }
                try
                {
                    LOG.warn("Forcing close of still-pending transaction object. Current stack is ", new Throwable());
                    LOG.warn("Forcing close of still-pending transaction object started at ", t._creation);
                    t.close();
                }
                catch (Exception x)
                {
                    LOG.error("Failed to force the still-pending transaction object closed on DB scope " + scope, x);
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
        ConnectionWrapper.dumpLeaksForThread(Thread.currentThread());
        closeAllConnectionsForCurrentThread();
        QueryService.get().clearEnvironment();
    }

    /**
     * Causes any connections associated with the current thread to be used by the passed-in thread. This allows
     * an async thread to participate in the same transaction as the original HTTP request processing thread, for
     * example
     * @param asyncThread the thread that should use the database connections of the current thread
     * @return an AutoCloseable that will stop sharing the database connection with the other thread
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
            @Override
            public synchronized Integer get(Connection c)
            {
                return m.get(c);
            }

            @Override
            public synchronized Integer put(Connection c, Integer spid)
            {
                return m.put(c,spid);
            }
        };
    }


    /** weak identity hash map, could just subclass WeakHashMap but eq() is static (and not overridable) */
    private static class _WeakestLinkMap<K, V>
    {
        private final ReferenceQueue<K> _q = new ReferenceQueue<>();

        private int _max = 1000;

        LinkedHashMap<_IdentityWrapper, V> _map = new LinkedHashMap<>()
        {
            @Override
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
            protected Map<Runnable, Runnable> getRunnables(TransactionImpl transaction)
            {
                return transaction._preCommitTasks;
            }
        },
        /** Run after the main transaction has been committed, separate from the transaction itself */
        POSTCOMMIT
        {
            @Override
            protected Map<Runnable, Runnable> getRunnables(TransactionImpl transaction)
            {
                return transaction._postCommitTasks;
            }
        },
        /** Run after the transaction has been completely rolled back and abandoned, but not if it was committed */
        POSTROLLBACK
        {
            @Override
            protected Map<Runnable, Runnable> getRunnables(TransactionImpl transaction)
            {
                return transaction._postRollbackTasks;
            }
        },
        /** Run immediately. Useful for cache-clearing tasks, which often want to fire right away, as well as after the commit */
        IMMEDIATE
        {
            @Override
            protected Map<Runnable, Runnable> getRunnables(TransactionImpl transaction)
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

        protected abstract Map<Runnable, Runnable> getRunnables(TransactionImpl transaction);

        public void run(TransactionImpl transaction)
        {
            // Copy to avoid ConcurrentModificationExceptions, need to retain original order from LinkedHashMap
            List<Runnable> tasks = new ArrayList<>(getRunnables(transaction).keySet());

            for (Runnable task : tasks)
            {
                task.run();
            }

            transaction.closeCaches();
        }

        public <T extends Runnable> T add(TransactionImpl transaction, T task)
        {
            Map<Runnable, Runnable> runnables = getRunnables(transaction);
            @SuppressWarnings("unchecked")
            T existing = (T)runnables.putIfAbsent(task, task);
            if (existing != null)
            {
                LOG.debug("Skipping duplicate runnable: " + task.toString());
            }
            return existing == null ? task : existing;
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
        @Override
        void close();
        void commit();

        /**
         * Commit the current transaction, running pre and post commit tasks, but don't close the connection
         * or remove transaction from thread pool. Effectively starts new transaction with same pre and post commit tasks.
         */
        void commitAndKeepConnection();

        boolean isAborted();

        String getId();

        @Nullable
        Long getAuditId();

        void setAuditId(Long auditId);
    }

    /** A dummy object for swap-in usage when no transaction is actually desired */
    public static final Transaction NO_OP_TRANSACTION = new Transaction()
    {
        @Override
        @NotNull
        public <T extends Runnable> T addCommitTask(@NotNull T runnable, @NotNull CommitTaskOption firstOption, CommitTaskOption... additionalOptions)
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

        @Override @Nullable
        public Long getAuditId()
        {
            return null;
        }

        @Override
        public void setAuditId(Long auditId)
        {

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

    /**
     * Represents a single database transaction. Holds onto the Connection, the temporary caches to use during that
     * transaction, and the tasks to run immediately after commit to update the shared caches with removals.
     */
    protected class TransactionImpl implements Transaction
    {
        private final String id = GUID.makeGUID();
        @NotNull
        private final ConnectionWrapper _conn;
        private final Map<DatabaseCache<?, ?>, Cache<?, ?>> _caches = new HashMap<>(20);

        // Maps so that we can coalesce identical tasks and avoid duplicating the effort, and efficiently find the
        // originally added task
        private final Map<Runnable, Runnable> _preCommitTasks = new LinkedHashMap<>();
        private final Map<Runnable, Runnable> _postCommitTasks = new LinkedHashMap<>();
        private final Map<Runnable, Runnable> _postRollbackTasks = new LinkedHashMap<>();

        private final List<List<Lock>> _locks = new ArrayList<>();
        private final Throwable _creation = new Throwable();
        private final TransactionKind _transactionKind;

        private boolean _aborted = false;
        private int _closesToIgnore = 0;
        private Long _auditId;

        private int _lockTimeout = 5;
        private TimeUnit _lockTimeoutUnit = TimeUnit.MINUTES;

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

        @Override @Nullable
        public Long getAuditId()
        {
            return _auditId;
        }

        @Override
        public void setAuditId(Long auditId)
        {
            _auditId = auditId;
        }

        <K, V> Cache<K, V> getCache(DatabaseCache<K, V> cache)
        {
            return (Cache<K, V>)_caches.get(cache);
        }

        <K, V> void addCache(DatabaseCache<K, V> cache, Cache<K, V> map)
        {
            _caches.put(cache, map);
        }

        @Override
        @NotNull
        public <T extends Runnable> T addCommitTask(@NotNull T task, @NotNull CommitTaskOption firstOption, CommitTaskOption... additionalOptions)
        {
            T result = firstOption.add(this, task);
            for (CommitTaskOption taskOption : additionalOptions)
            {
                result = taskOption.add(this, task);
            }

            return result;
        }

        @Override
        @NotNull
        public ConnectionWrapper getConnection()
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
                boolean locksEmpty = isOutermostTransaction();
                decrement();
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
                    }
                }

                clearCommitTasks();
            }
            else
            {
                _closesToIgnore--;
            }
            if (_closesToIgnore < 0)
            {
                throw createIllegalStateException("Popped too many closes from the stack", DbScope.this,_conn);
            }
        }

        @Override
        public void commit()
        {
            if (_aborted)
            {
                throw createIllegalStateException("Transaction has already been rolled back", DbScope.this, _conn);
            }
            if (_closesToIgnore > 0)
            {
                _closesToIgnore = 0;
                closeConnection();
                clearCommitTasks();
                throw createIllegalStateException("Missing expected call to close after prior commit", DbScope.this, _conn);
            }

            _closesToIgnore++;
            try
            {
                if (isOutermostTransaction())
                {
                    try
                    {
                        // Can't use try-with-resources here because we need to bypass ConnectionWrapper.close() transaction handling
                        ConnectionWrapper conn = null;

                        try
                        {
                            conn = getConnection();
                            CommitTaskOption.PRECOMMIT.run(this);
                            conn.commit();
                            conn.setAutoCommit(true);
                            LOG.debug("setAutoCommit(true)");
                        }
                        finally
                        {
                            if (null != conn)
                                conn.internalClose();
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
            finally // do transaction/lock bookkeeping no matter what else has happened.
            {
                decrement();
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

        public void setLockTimeout(int timeout, TimeUnit units)
        {
            _lockTimeout = timeout;
            _lockTimeoutUnit = units;
        }

        @Override
        public boolean isAborted()
        {
            return _aborted;
        }

        private void closeCaches()
        {
            for (Cache<?, ?> cache : _caches.values())
                cache.close();
            _caches.clear();
        }

        private boolean isOutermostTransaction()
        {
            if (_locks.isEmpty())
            {
                throw createIllegalStateException("No transactions remain, can't decrement!", DbScope.this, _conn);
            }
            return _locks.size() == 1;
        }

        /** Remove current transaction nesting and unlock any locks */
        private void decrement()
        {
            List<Lock> locks = _locks.remove(_locks.size() - 1);
            for (Lock lock : locks)
            {
                // Release all the locks
                lock.unlock();
            }
        }

        /** Closes the underlying DB connection but doesn't pop the stack on tracked transaction */
        private void closeConnection()
        {
            _aborted = true;
            ConnectionWrapper conn = getConnection();

            try
            {
                conn.internalClose();
            }
            catch (SQLException e)
            {
                LOG.error("Failed to close connection", e);
            }
        }

        public void increment(boolean releaseOnFinalCommit, List<Lock> extraLocks)
        {
            List<Lock> locksToUnlock = new ArrayList<>();
            boolean successLocking = false;
            try
            {
                for (Lock extraLock : extraLocks)
                {
                    // Clear the interrupted status of this thread so a previous, lingering interrupt won't prevent us
                    // from acquiring a new lock - perhaps we should clear this at the start of every HTTP request
                    // or background job that's using a thread pool?
                    //noinspection ResultOfMethodCallIgnored
                    Thread.interrupted();
                    try
                    {
                        boolean locked = extraLock.tryLock(_lockTimeout, _lockTimeoutUnit);
                        if (!locked)
                        {
                            throw new DeadlockPreventingException("Failed to acquire lock within timeout: " + extraLock);
                        }
                        locksToUnlock.add(extraLock);
                    }
                    catch (InterruptedException e)
                    {
                        throw new DeadlockPreventingException("Failed to acquire lock: " + extraLock, e);
                    }
                }
                successLocking = true;

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
            finally
            {
                if (!successLocking)
                {
                    for (Lock lock : locksToUnlock)
                    {
                        lock.unlock();
                    }
                }
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
            for (DbScope scope : getDbScopesToTest())
            {
                SqlDialect dialect = scope.getSqlDialect();

                try (Connection conn = scope.getConnection())
                {
                    SqlExecutor executor = new SqlExecutor(scope, conn).setLogLevel(Level.OFF);  // We're about to generate a lot of SQLExceptions
                    dialect.testDialectKeywords(executor);
                    dialect.testKeywordCandidates(executor);
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
            for (DbScope scope : getDbScopesToTest())
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

            for (DbScope scope : getDbScopesToTest())
            {
                SqlDialect dialect = scope.getSqlDialect();
                List<String> schemaNames = new ArrayList<>();

                for (String schemaName : scope.getSchemaNames())
                    if (!dialect.isSystemSchema(schemaName))
                        schemaNames.add(schemaName);

                if (schemaNames.isEmpty())
                    continue;

                String randomSchemaName = pickRandomElement(schemaNames);

                // Calling getSchema() with type Unknown will use Module type by default... we want Bare by default here
                DbSchemaType type = ModuleLoader.getInstance().getSchemaType(scope, randomSchemaName);
                if (null == type)
                    type = DbSchemaType.Bare;

                DbSchema schema = scope.getSchema(randomSchemaName, type);

                // For performance reasons, we don't bother loading the table list in Provisioned schemas, so we need special handling to get them here
                Collection<String> tableNames = DbSchemaType.Provisioned == type ? DbSchema.loadTableMetaData(scope, randomSchemaName).keySet() : schema.getTableNames();
                TableInfo tinfo = pickRandomTable(schema, tableNames);

                if (null != tinfo)
                    tablesToTest.add(tinfo);
            }

            try (Transaction ignored = getLabKeyScope().ensureTransaction())
            {
                // LabKey scope should have an active transaction, and all other scopes should not
                for (DbScope scope : getDbScopesToTest())
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
                //noinspection RedundantExplicitClose
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
        public void testCloseAllConnections()
        {
            Transaction t = getLabKeyScope().ensureTransaction();
            // Intentionally don't call t.close(), make sure it unwinds correctly
            assertTrue(getLabKeyScope().isTransactionActive());
            closeAllConnectionsForCurrentThread();
            assertFalse(getLabKeyScope().isTransactionActive());
        }

        @Test
        public void testAutoCommitFailure()
        {
            try (Transaction ignored = getLabKeyScope().ensureTransaction(AUTO_COMMIT_FAILURE_SIMULATOR))
            {
                fail("Shouldn't have gotten here, expected RuntimeSQLException");
            }
            catch (RuntimeSQLException e)
            {
                assertTrue("Bad message: " + e.getMessage(), e.getMessage().contains("simulated"));
            }
            assertFalse(getLabKeyScope().isTransactionActive());
            closeAllConnectionsForCurrentThread();
        }

        @Test
        public void testLockReleasedException()
        {
            ReentrantLock lock = new ReentrantLock();
            ReentrantLock lock2 = new ReentrantLock();
            try
            {
                try (Transaction ignored = getLabKeyScope().ensureTransaction(lock))
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

        @Test
        public void testLockTimeout()
        {
            ReentrantLock lock1 = new ReentrantLock();
            ReentrantLock lock2 = new ReentrantLock();
            Pair<Throwable, Throwable> throwables = attemptToDeadlock(lock1, lock2, (x) -> ((TransactionImpl)x).setLockTimeout(5, TimeUnit.SECONDS));

            assertTrue(throwables.first instanceof DeadlockPreventingException || throwables.second instanceof DeadlockPreventingException);
            assertFalse("Lock 1 is still locked", lock1.isLocked());
            assertFalse("Lock 2 is still locked", lock2.isLocked());
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
                    try (Transaction ignored = getLabKeyScope().ensureTransaction())
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
            final User user = TestContext.get().getUser();

            Lock lockUser = new ServerPrimaryKeyLock(true, CoreSchema.getInstance().getTableInfoUsersData(), user.getUserId());
            Lock lockHome = new ServerPrimaryKeyLock(true, CoreSchema.getInstance().getTableInfoContainers(), ContainerManager.getHomeContainer().getId());

            Pair<Throwable, Throwable> throwables = attemptToDeadlock(lockUser, lockHome, (x) -> {});

            assertTrue(throwables.first instanceof DeadlockLoserDataAccessException || throwables.second instanceof DeadlockLoserDataAccessException );
        }

        /**
         * @return foreground and background thread exceptions
         */
        private Pair<Throwable, Throwable> attemptToDeadlock(Lock lock1, Lock lock2, @NotNull Consumer<Transaction> transactionModifier)
        {
            final Object notifier = new Object();
            final Pair<Throwable, Throwable> result = new Pair<>(null, null);

            // let's try to intentionally cause a deadlock
            Thread bkg = new Thread(() -> {
                // lock2 should succeed fg has not locked this yet

                // Use an outer transaction on both threads so that we can customize the timeout to keep the test running quickly
                try (Transaction outerTx = CoreSchema.getInstance().getScope().ensureTransaction())
                {
                    transactionModifier.accept(outerTx);

                    try (Transaction txBg = CoreSchema.getInstance().getScope().ensureTransaction(lock2))
                    {
                        synchronized (notifier)
                        {
                            notifier.notify();
                        }
                        // should block on fg thread, but we're not deadlocked yet
                        try (Transaction inner = CoreSchema.getInstance().getScope().ensureTransaction(lock1))
                        {
                            inner.commit();
                        }
                        txBg.commit();
                    }
                    catch (Throwable x)
                    {
                        result.second = x;
                    }
                }
            });


            // lock1 should succeed (bg has not even started yet)
            try (Transaction outerTx = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                transactionModifier.accept(outerTx);

                try (Transaction txFg = CoreSchema.getInstance().getScope().ensureTransaction(lock1))
                {
                    // wait for background to acquire first locks
                    synchronized (notifier)
                    {
                        bkg.start();
                        // wait for bkg to acquire lock2
                        notifier.wait(60 * 1000);
                    }
                    // try to acquire my second lock
                    // this should cause a deadlock
                    try (Transaction inner = CoreSchema.getInstance().getScope().ensureTransaction(lock2))
                    {
                        inner.commit();
                    }
                    txFg.commit();
                }
                catch (InterruptedException x)
                {
                    throw new RuntimeException(x);
                }
                catch (Throwable x)
                {
                    result.first = x;
                }
                finally
                {
                    bkg.interrupt();
                    try
                    {
                        bkg.join();
                    }
                    catch (InterruptedException ignored)
                    {
                    }
                }
            }
            return result;
        }

         @Test
        public void testLockException()
        {
            // test ServerLock failures

            Lock failServerLock = (ServerLock) () -> { throw new DeadlockLoserDataAccessException("test",null); };

            try (Transaction txFg = CoreSchema.getInstance().getScope().ensureTransaction(failServerLock))
            {
                fail("shouldn't get here");
                txFg.commit();
            }
            catch (Exception x)
            {
                assertTrue(x instanceof DeadlockLoserDataAccessException);
            }
            new TableSelector(CoreSchema.getInstance().getTableInfoUsers(), TableSelector.ALL_COLUMNS).getRowCount();


            try (Transaction txFg = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                try (Transaction txInner = CoreSchema.getInstance().getScope().ensureTransaction(failServerLock))
                {
                    fail("shouldn't get here");
                    txFg.commit();
                }
                fail("shouldn't get here");
                txFg.commit();
            }
            catch (Exception x)
            {
                assertTrue(x instanceof DeadlockLoserDataAccessException);
            }
            new TableSelector(CoreSchema.getInstance().getTableInfoUsers(), TableSelector.ALL_COLUMNS).getRowCount();

            // test _non_ ServerLock failures

            Lock failLock = new Lock()
            {
                @Override public void lock() { throw new NullPointerException(); }
                @Override public void lockInterruptibly() throws InterruptedException { }
                @Override public boolean tryLock() { return false; }
                @Override public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException { throw new NullPointerException(); }
                @Override public void unlock() { }
                @NotNull @Override public Condition newCondition() { return null; }
            };

            try (Transaction txFg = CoreSchema.getInstance().getScope().ensureTransaction(failLock))
            {
                fail("shouldn't get here");
                txFg.commit();
            }
            catch (Exception x)
            {
                assertTrue(x instanceof NullPointerException);
            }
            new TableSelector(CoreSchema.getInstance().getTableInfoUsers(), TableSelector.ALL_COLUMNS).getRowCount();


            try (Transaction txFg = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                try (Transaction ignored = CoreSchema.getInstance().getScope().ensureTransaction(failLock))
                {
                    fail("shouldn't get here");
                    txFg.commit();
                }
                fail("shouldn't get here");
                txFg.commit();
            }
            catch (Exception x)
            {
                assertTrue(x instanceof NullPointerException);
            }
            new TableSelector(CoreSchema.getInstance().getTableInfoUsers(), TableSelector.ALL_COLUMNS).getRowCount();
        }


        @Test
        public void testTryWithResources() throws SQLException
        {
            try (Transaction t = getLabKeyScope().ensureTransaction())
            {
                Connection c = t.getConnection();
                assertFalse(c.isClosed());

                try (Transaction t2 = getLabKeyScope().ensureTransaction())
                {
                    try (Connection c2 = t2.getConnection())
                    {
                        assertSame(c, c2);
                        c2.getMetaData().getDatabaseProductName();
                    }
                    assertFalse(c.isClosed());
                    t2.commit();
                    assertFalse(c.isClosed());
                }
                t.commit();
                assertTrue(c.isClosed());
            }
        }

        @Test
        public void testRetryException()
        {
            var r = new RetryPassthroughException(new IllegalArgumentException());
            try
            {
                r.rethrow(IllegalArgumentException.class);
                fail("should have thrown");
            }
            catch (IllegalArgumentException x)
            {
                // expected!
            }
        }
    }
}

