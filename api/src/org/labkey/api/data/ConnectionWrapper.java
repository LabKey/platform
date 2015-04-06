/*
 * Copyright (c) 2005-2015 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewServlet;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.text.DateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public class ConnectionWrapper implements java.sql.Connection
{
    private final Connection _connection;
    private final DbScope _scope;
    private final Integer _spid;
    private final java.util.Date _allocationTime = new java.util.Date();
    private final String _allocatingThreadName;

    private final static Logger LOG = Logger.getLogger(ConnectionWrapper.class);

    private static final Map<ConnectionWrapper, Pair<Thread,Throwable>> _openConnections = Collections.synchronizedMap(new IdentityHashMap<ConnectionWrapper, Pair<Thread, Throwable>>());

    private static final Set<ConnectionWrapper> _loggedLeaks = new HashSet<>();

    /**
     * Mapping from SPID (database process id) to the name of the most recent Java thread to use the connection
     * Useful for checking out database deadlock problems when debugging.
     */
    private static final Map<Integer, String> _mostRecentSPIDThreads = new HashMap<>();

    /**
     * String representation of the map, useful because it's a pain to look through the
     * map if you're paused in the debugger due to JVM restrictions on invoking methods when paused.
     */
    @SuppressWarnings({"UNUSED_SYMBOL", "FieldCanBeLocal", "UnusedDeclaration"})
    private static String _mostRecentSPIDUsageString = "";
    private static Logger _logDefault = Logger.getLogger(ConnectionWrapper.class);
    private static boolean _explicitLogger = _logDefault.getLevel() != null || _logDefault.getParent() != null  && _logDefault.getParent().getName().equals("org.labkey.api.data");

    private final @NotNull Logger _log;

    /** Remember the first SQLException that happened on this connection, as it may have leave the connection in a bad state */
    private SQLException _originalSqlException;

    /** Remember code that closed the Transaction but didn't commit - it may be faulty if it doesn't signal the failure back to the caller */
    private Throwable _suspiciousCloseStackTrace;

    public ConnectionWrapper(Connection conn, DbScope scope, Integer spid, @Nullable Logger log) throws SQLException
    {
        _connection = conn;
        _scope = scope;
        _spid = spid;

        synchronized (_mostRecentSPIDThreads)
        {
            _mostRecentSPIDThreads.put(_spid, Thread.currentThread().getName());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, String> entry : _mostRecentSPIDThreads.entrySet())
            {
                sb.append("SPID: ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
            }
            _mostRecentSPIDUsageString = sb.toString();
        }

        MemTracker.getInstance().put(this);
        _allocatingThreadName = Thread.currentThread().getName();

        _openConnections.put(this,  new Pair<>(Thread.currentThread(), new Throwable()));

        _log = log != null ? log : getConnectionLogger();
    }


    /** this is a best guess logger, pass one in to be predictable */
    static Logger getConnectionLogger()
    {
        if (_explicitLogger)
            return _logDefault;
        StackTraceElement[] stes = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : stes)
        {
            String className = ste.getClassName();
            if (className.equals("org.labkey.api.view.ViewServlet") || className.equals("org.labkey.api.action.SpringActionController"))
                break;
            if (className.endsWith("Controller") && !className.startsWith("org.labkey.api.view"))
                return Logger.getLogger(className);
        }
        return _logDefault;
    }


    public @NotNull Logger getLogger()
    {
        return _log;
    }


    public DbScope getScope()
    {
        return _scope;
    }


    public static boolean dumpOpenConnections()
    {
        for (Pair<Thread, Throwable> p : _openConnections.values())
        {
            String thread = p.first.getName();
            Throwable t = p.second;
            System.err.println("Connection opened on thread " + thread);
            t.printStackTrace(System.err);
        }

        return true;
    }

    public static void dumpLeaksForThread(Thread t)
    {
        synchronized(_openConnections)
        {
            for (Map.Entry<ConnectionWrapper, Pair<Thread, Throwable>> entry : _openConnections.entrySet())
            {
                if (entry.getValue().getKey() == t)
                {
                    Throwable throwable = entry.getValue().second;
                    if (!_loggedLeaks.contains(entry.getKey()))
                    {
                        _logDefault.error("Probable connection leak, connection was acquired at: ", throwable);
                        _loggedLeaks.add(entry.getKey());
                    }
                }
            }
        }
    }

    public static Set<Integer> getSPIDsForThread(Thread t)
    {
        Set<Integer> result = new HashSet<>();
        synchronized(_openConnections)
        {
            for (Map.Entry<ConnectionWrapper, Pair<Thread, Throwable>> entry : _openConnections.entrySet())
            {
                if (entry.getValue().getKey() == t)
                {
                    result.add(entry.getKey()._spid);
                }
            }
        }
        return result;
    }

    public Integer getSPID()
    {
        return _spid;
    }

    public Statement createStatement()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.createStatement());
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public PreparedStatement prepareStatement(String sql)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareStatement(sql), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public CallableStatement prepareCall(String sql)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareCall(sql), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public String nativeSQL(String sql)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.nativeSQL(sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void setAutoCommit(boolean autoCommit)
    throws SQLException
    {
        checkForSuspiciousClose();
        _log.debug("setAutoCommit(" + (autoCommit?"TRUE)":"FALSE)"));
        try
        {
            _connection.setAutoCommit(autoCommit);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public boolean getAutoCommit()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.getAutoCommit();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void commit()
    throws SQLException
    {
        checkForSuspiciousClose();
        _log.debug("commit()");
        try
        {
            _connection.commit();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void rollback()
    throws SQLException
    {
        checkForSuspiciousClose();
        _log.debug("rollback()");
        try
        {
            _connection.rollback();
        }
        catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw e;
        }
    }

    public void close()
    throws SQLException
    {
        _openConnections.remove(this);
        _loggedLeaks.remove(this);
        // The Tomcat connection pool violates the API for close() - it throws an exception
        // if it's already been closed instead of doing a no-op
        if (null != _connection && !isClosed())
        {
            try
            {
                _connection.close();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }
    }

    public boolean isClosed()
    throws SQLException
    {
        try
        {
            return _connection.isClosed();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public DatabaseMetaData getMetaData()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            final DatabaseMetaData md = _connection.getMetaData();
            return new DatabaseMetaDataWrapper(md);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void setReadOnly(boolean readOnly)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.setReadOnly(readOnly);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public boolean isReadOnly()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.isReadOnly();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void setCatalog(String catalog)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.setCatalog(catalog);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public String getCatalog()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.getCatalog();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void setTransactionIsolation(int level)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.setTransactionIsolation(level);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public int getTransactionIsolation()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.getTransactionIsolation();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public SQLWarning getWarnings()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.getWarnings();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void clearWarnings()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.clearWarnings();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    private StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        if (null == _scope)
            return new StatementWrapper(conn, stmt);
        else
            return _scope.getSqlDialect().getStatementWrapper(conn, stmt);
    }

    private StatementWrapper getStatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        if (null == _scope)
            return new StatementWrapper(conn, stmt, sql);
        else
            return _scope.getSqlDialect().getStatementWrapper(conn, stmt, sql);
    }


    public Statement createStatement(int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.createStatement(resultSetType, resultSetConcurrency));
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareStatement(sql, resultSetType, resultSetConcurrency), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareCall(sql, resultSetType, resultSetConcurrency), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public Map<String, Class<?>> getTypeMap()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.getTypeMap();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public SQLException logAndCheckException(SQLException e)
    {
        LOG.debug("SQLException", e);

        // Remember the first exception than happened on this connection. It may leave the connection in a bad state
        // for subsequent users
        if (_originalSqlException == null)
        {
            _originalSqlException = e;
        }
        else
        {
            // Some other code may have left the connection in a bad state. Suggest it as a possible culprit
            if (_scope.isTransactionActive())
            {
                LOG.warn("Additional SQLException on a Connection that has already thrown a SQLException. Additional exception to be logged separately, original exception is: ", _originalSqlException);
            }
            else
            {
                LOG.debug("Additional SQLException on a Connection that has already thrown a SQLException. Additional exception to be logged separately, original exception is: ", _originalSqlException);
            }
        }
        return e;
    }

    public void setTypeMap(Map<String, Class<?>> map)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.setTypeMap(map);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void setHoldability(int holdability)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.setHoldability(holdability);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public int getHoldability()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.getHoldability();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public Savepoint setSavepoint()
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.setSavepoint();
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public Savepoint setSavepoint(String name)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.setSavepoint(name);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void rollback(Savepoint savepoint)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.rollback(savepoint);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public void releaseSavepoint(Savepoint savepoint)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            _connection.releaseSavepoint(savepoint);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareStatement(sql, autoGeneratedKeys), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareStatement(sql, columnIndexes), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames)
    throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return getStatementWrapper(this, _connection.prepareStatement(sql, columnNames), sql);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public static int getActiveConnectionCount()
    {
        return _openConnections.size();
    }

    public static int getProbableLeakCount()
    {
        return _loggedLeaks.size();
    }

    public String toString()
    {
        return "Connection wrapper for SPID " + _spid + ", originally allocated to thread " + _allocatingThreadName + " at " + DateFormat.getInstance().format(_allocationTime);
    }


    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        return iface.isAssignableFrom(_connection.getClass());
    }

    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();
        close();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        if (iface.isAssignableFrom(_connection.getClass()))
            return (T)_connection;
        return null;
    }


    public Array createArrayOf(String typeName, Object[] elements) throws SQLException
    {
        checkForSuspiciousClose();
        try
        {
            return _connection.createArrayOf(typeName, elements);
        }
        catch (SQLException e)
        {
            throw logAndCheckException(e);
        }
    }

    public Blob createBlob() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public Clob createClob() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public NClob createNClob() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public SQLXML createSQLXML() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public Properties getClientInfo() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public String getClientInfo(String name) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public boolean isValid(int timeout) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException
    {
        throw new UnsupportedOperationException();
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException
    {
        throw new UnsupportedOperationException();
    }

    
    // TODO: now that we require JDK/JRE 7.0, these methods should be properly implemented.

    public void setSchema(String schema) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public String getSchema() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void abort(Executor executor) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public int getNetworkTimeout() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    private void checkForSuspiciousClose() throws SQLException
    {
        if (_suspiciousCloseStackTrace != null && isClosed())
        {
            throw new SQLException("This connection has already been closed, and may have been left in a bad state. See nested exception for potentially suspect code.", _suspiciousCloseStackTrace);
        }
    }

    public void markAsSuspiciouslyClosed()
    {
        _suspiciousCloseStackTrace = new Throwable("This connection may have been closed by a codepath that did not intend to leave it in this state");
    }

    private class DatabaseMetaDataWrapper implements DatabaseMetaData
    {
        private final DatabaseMetaData _md;
        
        DatabaseMetaDataWrapper(DatabaseMetaData md)
        {
            _md = md;
        }

        private void log(String methodName, String message, long start)
        {
            long duration = System.currentTimeMillis() - start;

            if (getLogger().isDebugEnabled())
                getLogger().debug(message + " " + DateUtil.formatDuration(duration));

            QueryProfiler.getInstance().track(null, "DatabaseMetaData." + methodName, null, duration, Thread.currentThread().getStackTrace(),
                                              ViewServlet.isRequestThread(), QueryLogging.emptyQueryLogging());
        }

        @Override
        public boolean allProceduresAreCallable()
                throws SQLException
        {
            try
            {
                return _md.allProceduresAreCallable();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean allTablesAreSelectable()
                throws SQLException
        {
            try
            {
                return _md.allTablesAreSelectable();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getURL()
                throws SQLException
        {
            try
            {
                return _md.getURL();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getUserName()
                throws SQLException
        {
            try
            {
                return _md.getUserName();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean isReadOnly()
                throws SQLException
        {
            try
            {
                return _md.isReadOnly();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean nullsAreSortedHigh()
                throws SQLException
        {
            try
            {
                return _md.nullsAreSortedHigh();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean nullsAreSortedLow()
                throws SQLException
        {
            try
            {
                return _md.nullsAreSortedLow();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean nullsAreSortedAtStart()
                throws SQLException
        {
            try
            {
                return _md.nullsAreSortedAtStart();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean nullsAreSortedAtEnd()
                throws SQLException
        {
            try
            {
                return _md.nullsAreSortedAtEnd();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getDatabaseProductName()
                throws SQLException
        {
            try
            {
                return _md.getDatabaseProductName();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getDatabaseProductVersion()
                throws SQLException
        {
            try
            {
                return _md.getDatabaseProductVersion();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getDriverName()
                throws SQLException
        {
            try
            {
                return _md.getDriverName();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getDriverVersion()
                throws SQLException
        {
            try
            {
                return _md.getDriverVersion();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getDriverMajorVersion()
        {
            return _md.getDriverMajorVersion();
        }

        @Override
        public int getDriverMinorVersion()
        {
            return _md.getDriverMinorVersion();
        }

        @Override
        public boolean usesLocalFiles()
                throws SQLException
        {
            try
            {
                return _md.usesLocalFiles();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean usesLocalFilePerTable()
                throws SQLException
        {
            try
            {
                return _md.usesLocalFilePerTable();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsMixedCaseIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.supportsMixedCaseIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean storesUpperCaseIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.storesUpperCaseIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean storesLowerCaseIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.storesLowerCaseIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean storesMixedCaseIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.storesMixedCaseIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsMixedCaseQuotedIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.supportsMixedCaseQuotedIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean storesUpperCaseQuotedIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.storesUpperCaseQuotedIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean storesLowerCaseQuotedIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.storesLowerCaseQuotedIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean storesMixedCaseQuotedIdentifiers()
                throws SQLException
        {
            try
            {
                return _md.storesMixedCaseQuotedIdentifiers();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getIdentifierQuoteString()
                throws SQLException
        {
            try
            {
                return _md.getIdentifierQuoteString();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getSQLKeywords()
                throws SQLException
        {
            try
            {
                return _md.getSQLKeywords();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getNumericFunctions()
                throws SQLException
        {
            try
            {
                return _md.getNumericFunctions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getStringFunctions()
                throws SQLException
        {
            try
            {
                return _md.getStringFunctions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getSystemFunctions()
                throws SQLException
        {
            try
            {
                return _md.getSystemFunctions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getTimeDateFunctions()
                throws SQLException
        {
            try
            {
                return _md.getTimeDateFunctions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getSearchStringEscape()
                throws SQLException
        {
            try
            {
                return _md.getSearchStringEscape();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getExtraNameCharacters()
                throws SQLException
        {
            try
            {
                return _md.getExtraNameCharacters();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsAlterTableWithAddColumn()
                throws SQLException
        {
            try
            {
                return _md.supportsAlterTableWithAddColumn();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsAlterTableWithDropColumn()
                throws SQLException
        {
            try
            {
                return _md.supportsAlterTableWithDropColumn();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsColumnAliasing()
                throws SQLException
        {
            try
            {
                return _md.supportsColumnAliasing();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean nullPlusNonNullIsNull()
                throws SQLException
        {
            try
            {
                return _md.nullPlusNonNullIsNull();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsConvert()
                throws SQLException
        {
            try
            {
                return _md.supportsConvert();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsConvert(int fromType, int toType)
                throws SQLException
        {
            try
            {
                return _md.supportsConvert(fromType, toType);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsTableCorrelationNames()
                throws SQLException
        {
            try
            {
                return _md.supportsTableCorrelationNames();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsDifferentTableCorrelationNames()
                throws SQLException
        {
            try
            {
                return _md.supportsDifferentTableCorrelationNames();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsExpressionsInOrderBy()
                throws SQLException
        {
            try
            {
                return _md.supportsExpressionsInOrderBy();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsOrderByUnrelated()
                throws SQLException
        {
            try
            {
                return _md.supportsOrderByUnrelated();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsGroupBy()
                throws SQLException
        {
            try
            {
                return _md.supportsGroupBy();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsGroupByUnrelated()
                throws SQLException
        {
            try
            {
                return _md.supportsGroupByUnrelated();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsGroupByBeyondSelect()
                throws SQLException
        {
            try
            {
                return _md.supportsGroupByBeyondSelect();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsLikeEscapeClause()
                throws SQLException
        {
            try
            {
                return _md.supportsLikeEscapeClause();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsMultipleResultSets()
                throws SQLException
        {
            try
            {
                return _md.supportsMultipleResultSets();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsMultipleTransactions()
                throws SQLException
        {
            try
            {
                return _md.supportsMultipleTransactions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsNonNullableColumns()
                throws SQLException
        {
            try
            {
                return _md.supportsNonNullableColumns();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsMinimumSQLGrammar()
                throws SQLException
        {
            try
            {
                return _md.supportsMinimumSQLGrammar();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsCoreSQLGrammar()
                throws SQLException
        {
            try
            {
                return _md.supportsCoreSQLGrammar();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsExtendedSQLGrammar()
                throws SQLException
        {
            try
            {
                return _md.supportsExtendedSQLGrammar();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsANSI92EntryLevelSQL()
                throws SQLException
        {
            try
            {
                return _md.supportsANSI92EntryLevelSQL();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsANSI92IntermediateSQL()
                throws SQLException
        {
            try
            {
                return _md.supportsANSI92IntermediateSQL();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsANSI92FullSQL()
                throws SQLException
        {
            try
            {
                return _md.supportsANSI92FullSQL();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsIntegrityEnhancementFacility()
                throws SQLException
        {
            try
            {
                return _md.supportsIntegrityEnhancementFacility();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsOuterJoins()
                throws SQLException
        {
            try
            {
                return _md.supportsOuterJoins();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsFullOuterJoins()
                throws SQLException
        {
            try
            {
                return _md.supportsFullOuterJoins();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsLimitedOuterJoins()
                throws SQLException
        {
            try
            {
                return _md.supportsLimitedOuterJoins();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getSchemaTerm()
                throws SQLException
        {
            try
            {
                return _md.getSchemaTerm();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getProcedureTerm()
                throws SQLException
        {
            try
            {
                return _md.getProcedureTerm();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getCatalogTerm()
                throws SQLException
        {
            try
            {
                return _md.getCatalogTerm();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean isCatalogAtStart()
                throws SQLException
        {
            try
            {
                return _md.isCatalogAtStart();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public String getCatalogSeparator()
                throws SQLException
        {
            try
            {
                return _md.getCatalogSeparator();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSchemasInDataManipulation()
                throws SQLException
        {
            try
            {
                return _md.supportsSchemasInDataManipulation();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSchemasInProcedureCalls()
                throws SQLException
        {
            try
            {
                return _md.supportsSchemasInProcedureCalls();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSchemasInTableDefinitions()
                throws SQLException
        {
            try
            {
                return _md.supportsSchemasInTableDefinitions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSchemasInIndexDefinitions()
                throws SQLException
        {
            try
            {
                return _md.supportsSchemasInIndexDefinitions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSchemasInPrivilegeDefinitions()
                throws SQLException
        {
            try
            {
                return _md.supportsSchemasInPrivilegeDefinitions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsCatalogsInDataManipulation()
                throws SQLException
        {
            try
            {
                return _md.supportsCatalogsInDataManipulation();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsCatalogsInProcedureCalls()
                throws SQLException
        {
            try
            {
                return _md.supportsCatalogsInProcedureCalls();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsCatalogsInTableDefinitions()
                throws SQLException
        {
            try
            {
                return _md.supportsCatalogsInTableDefinitions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsCatalogsInIndexDefinitions()
                throws SQLException
        {
            try
            {
                return _md.supportsCatalogsInIndexDefinitions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsCatalogsInPrivilegeDefinitions()
                throws SQLException
        {
            try
            {
                return _md.supportsCatalogsInPrivilegeDefinitions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsPositionedDelete()
                throws SQLException
        {
            try
            {
                return _md.supportsPositionedDelete();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsPositionedUpdate()
                throws SQLException
        {
            try
            {
                return _md.supportsPositionedUpdate();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSelectForUpdate()
                throws SQLException
        {
            try
            {
                return _md.supportsSelectForUpdate();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsStoredProcedures()
                throws SQLException
        {
            try
            {
                return _md.supportsStoredProcedures();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSubqueriesInComparisons()
                throws SQLException
        {
            try
            {
                return _md.supportsSubqueriesInComparisons();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSubqueriesInExists()
                throws SQLException
        {
            try
            {
                return _md.supportsSubqueriesInExists();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSubqueriesInIns()
                throws SQLException
        {
            try
            {
                return _md.supportsSubqueriesInIns();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSubqueriesInQuantifieds()
                throws SQLException
        {
            try
            {
                return _md.supportsSubqueriesInQuantifieds();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsCorrelatedSubqueries()
                throws SQLException
        {
            try
            {
                return _md.supportsCorrelatedSubqueries();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsUnion()
                throws SQLException
        {
            try
            {
                return _md.supportsUnion();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsUnionAll()
                throws SQLException
        {
            try
            {
                return _md.supportsUnionAll();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsOpenCursorsAcrossCommit()
                throws SQLException
        {
            try
            {
                return _md.supportsOpenCursorsAcrossCommit();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsOpenCursorsAcrossRollback()
                throws SQLException
        {
            try
            {
                return _md.supportsOpenCursorsAcrossRollback();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsOpenStatementsAcrossCommit()
                throws SQLException
        {
            try
            {
                return _md.supportsOpenStatementsAcrossCommit();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsOpenStatementsAcrossRollback()
                throws SQLException
        {
            try
            {
                return _md.supportsOpenStatementsAcrossRollback();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxBinaryLiteralLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxBinaryLiteralLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxCharLiteralLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxCharLiteralLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxColumnNameLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxColumnNameLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxColumnsInGroupBy()
                throws SQLException
        {
            try
            {
                return _md.getMaxColumnsInGroupBy();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxColumnsInIndex()
                throws SQLException
        {
            try
            {
                return _md.getMaxColumnsInIndex();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxColumnsInOrderBy()
                throws SQLException
        {
            try
            {
                return _md.getMaxColumnsInOrderBy();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxColumnsInSelect()
                throws SQLException
        {
            try
            {
                return _md.getMaxColumnsInSelect();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxColumnsInTable()
                throws SQLException
        {
            try
            {
                return _md.getMaxColumnsInTable();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxConnections()
                throws SQLException
        {
            try
            {
                return _md.getMaxConnections();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxCursorNameLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxCursorNameLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxIndexLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxIndexLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxSchemaNameLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxSchemaNameLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxProcedureNameLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxProcedureNameLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxCatalogNameLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxCatalogNameLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxRowSize()
                throws SQLException
        {
            try
            {
                return _md.getMaxRowSize();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean doesMaxRowSizeIncludeBlobs()
                throws SQLException
        {
            try
            {
                return _md.doesMaxRowSizeIncludeBlobs();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxStatementLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxStatementLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxStatements()
                throws SQLException
        {
            try
            {
                return _md.getMaxStatements();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxTableNameLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxTableNameLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxTablesInSelect()
                throws SQLException
        {
            try
            {
                return _md.getMaxTablesInSelect();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getMaxUserNameLength()
                throws SQLException
        {
            try
            {
                return _md.getMaxUserNameLength();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getDefaultTransactionIsolation()
                throws SQLException
        {
            try
            {
                return _md.getDefaultTransactionIsolation();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsTransactions()
                throws SQLException
        {
            try
            {
                return _md.supportsTransactions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsTransactionIsolationLevel(int level) throws SQLException
        {
            try
            {
                return _md.supportsTransactionIsolationLevel(level);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException
        {
            try
            {
                return _md.supportsDataDefinitionAndDataManipulationTransactions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsDataManipulationTransactionsOnly() throws SQLException
        {
            try
            {
                return _md.supportsDataManipulationTransactionsOnly();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean dataDefinitionCausesTransactionCommit()
                throws SQLException
        {
            try
            {
                return _md.dataDefinitionCausesTransactionCommit();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean dataDefinitionIgnoredInTransactions()
                throws SQLException
        {
            try
            {
                return _md.dataDefinitionIgnoredInTransactions();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException
        {
            try
            {
                return _md.getProcedures(catalog, schemaPattern, procedureNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException
        {
            try
            {
                return _md.getProcedureColumns(catalog, schemaPattern, procedureNamePattern, columnNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException
        {
            try
            {
                long start = System.currentTimeMillis();
                ResultSet ret = _md.getTables(catalog, schemaPattern, tableNamePattern, types);
                log("getTables()", "getTables(" + catalog + ", " + schemaPattern + ", " + tableNamePattern + ")", start);

                return ret;
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getSchemas() throws SQLException
        {
            try
            {
                long start = System.currentTimeMillis();
                ResultSet ret = _md.getSchemas();
                log("getSchemas()", "getSchemas()", start);

                return ret;
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getCatalogs() throws SQLException
        {
            try
            {
                long start = System.currentTimeMillis();
                ResultSet ret = _md.getCatalogs();
                log("getCatalogs()", "getCatalogs()", start);

                return ret;
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getTableTypes() throws SQLException
        {
            try
            {
                long start = System.currentTimeMillis();
                ResultSet ret =  _md.getTableTypes();
                log("getTableTypes()", "getTableTypes()", start);

                return ret;
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException
        {
            try
            {
                long start = System.currentTimeMillis();
                ResultSet ret = _md.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
                log("getColumns()", "getColumns(" + catalog + ", " + schemaPattern + ", " + tableNamePattern + ", " + columnNamePattern + ")", start);

                return ret;
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException
        {
            try
            {
                return _md.getColumnPrivileges(catalog, schema, table, columnNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException
        {
            try
            {
                return _md.getTablePrivileges(catalog, schemaPattern, tableNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException
        {
            try
            {
                return _md.getBestRowIdentifier(catalog, schema, table, scope, nullable);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException
        {
            try
            {
                return _md.getVersionColumns(catalog, schema, table);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException
        {
            try
            {
                long start = System.currentTimeMillis();
                ResultSet ret = _md.getPrimaryKeys(catalog, schema, table);
                log("getPrimaryKeys()", "getPrimaryKeys(" + catalog + ", " + schema + ", " + table + ")", start);

                return ret;
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException
        {
            try
            {
                long start = System.currentTimeMillis();
                ResultSet ret = _md.getImportedKeys(catalog, schema, table);
                log("getImportedKeys()", "getImportedKeys(" + catalog + ", " + schema + ", " + table + ")", start);

                return ret;
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException
        {
            try
            {
                return _md.getExportedKeys(catalog, schema, table);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable)
                throws SQLException
        {
            try
            {
                return _md.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getTypeInfo()
                throws SQLException
        {
            try
            {
                return _md.getTypeInfo();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException
        {
            try
            {
                return _md.getIndexInfo(catalog, schema, table, unique, approximate);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsResultSetType(int type)
                throws SQLException
        {
            try
            {
                return _md.supportsResultSetType(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsResultSetConcurrency(int type, int concurrency)
                throws SQLException
        {
            try
            {
                return _md.supportsResultSetConcurrency(type, concurrency);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean ownUpdatesAreVisible(int type)
                throws SQLException
        {
            try
            {
                return _md.ownUpdatesAreVisible(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean ownDeletesAreVisible(int type)
                throws SQLException
        {
            try
            {
                return _md.ownDeletesAreVisible(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean ownInsertsAreVisible(int type)
                throws SQLException
        {
            try
            {
                return _md.ownInsertsAreVisible(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean othersUpdatesAreVisible(int type)
                throws SQLException
        {
            try
            {
                return _md.othersUpdatesAreVisible(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean othersDeletesAreVisible(int type)
                throws SQLException
        {
            try
            {
                return _md.othersDeletesAreVisible(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean othersInsertsAreVisible(int type)
                throws SQLException
        {
            try
            {
                return _md.othersInsertsAreVisible(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean updatesAreDetected(int type)
                throws SQLException
        {
            try
            {
                return _md.updatesAreDetected(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean deletesAreDetected(int type)
                throws SQLException
        {
            try
            {
                return _md.deletesAreDetected(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean insertsAreDetected(int type)
                throws SQLException
        {
            try
            {
                return _md.insertsAreDetected(type);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsBatchUpdates()
                throws SQLException
        {
            try
            {
                return _md.supportsBatchUpdates();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
                throws SQLException
        {
            try
            {
                return _md.getUDTs(catalog, schemaPattern, typeNamePattern, types);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public Connection getConnection()
                throws SQLException
        {
            try
            {
                return _md.getConnection();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsSavepoints()
                throws SQLException
        {
            try
            {
                return _md.supportsSavepoints();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsNamedParameters()
                throws SQLException
        {
            try
            {
                return _md.supportsNamedParameters();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsMultipleOpenResults()
                throws SQLException
        {
            try
            {
                return _md.supportsMultipleOpenResults();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsGetGeneratedKeys()
                throws SQLException
        {
            try
            {
                return _md.supportsGetGeneratedKeys();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
                throws SQLException
        {
            try
            {
                return _md.getSuperTypes(catalog, schemaPattern, typeNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
                throws SQLException
        {
            try
            {
                return _md.getSuperTables(catalog, schemaPattern, tableNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern)
                throws SQLException
        {
            try
            {
                return _md.getAttributes(catalog, schemaPattern, typeNamePattern, attributeNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsResultSetHoldability(int holdability)
                throws SQLException
        {
            try
            {
                return _md.supportsResultSetHoldability(holdability);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getResultSetHoldability()
                throws SQLException
        {
            try
            {
                return _md.getResultSetHoldability();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getDatabaseMajorVersion()
                throws SQLException
        {
            try
            {
                return _md.getDatabaseMajorVersion();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getDatabaseMinorVersion()
                throws SQLException
        {
            try
            {
                return _md.getDatabaseMinorVersion();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getJDBCMajorVersion()
                throws SQLException
        {
            try
            {
                return _md.getJDBCMajorVersion();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getJDBCMinorVersion()
                throws SQLException
        {
            try
            {
                return _md.getJDBCMinorVersion();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public int getSQLStateType()
                throws SQLException
        {
            try
            {
                return _md.getSQLStateType();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean locatorsUpdateCopy()
                throws SQLException
        {
            try
            {
                return _md.locatorsUpdateCopy();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsStatementPooling()
                throws SQLException
        {
            try
            {
                return _md.supportsStatementPooling();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public RowIdLifetime getRowIdLifetime()
                throws SQLException
        {
            try
            {
                return _md.getRowIdLifetime();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getSchemas(String catalog, String schemaPattern)
                throws SQLException
        {
            try
            {
                return _md.getSchemas(catalog, schemaPattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean supportsStoredFunctionsUsingCallSyntax()
                throws SQLException
        {
            try
            {
                return _md.supportsStoredFunctionsUsingCallSyntax();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public boolean autoCommitFailureClosesAllResultSets()
                throws SQLException
        {
            try
            {
                return _md.autoCommitFailureClosesAllResultSets();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getClientInfoProperties()
                throws SQLException
        {
            try
            {
                return _md.getClientInfoProperties();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
                throws SQLException
        {
            try
            {
                return _md.getFunctions(catalog, schemaPattern, functionNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern)
                throws SQLException
        {
            try
            {
                return _md.getFunctionColumns(catalog, schemaPattern, functionNamePattern, columnNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        @Override
        public <T> T unwrap(Class<T> iface)
                throws SQLException
        {
            return (T)_md;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface)
                throws SQLException
        {
            return iface.isAssignableFrom(DatabaseMetaData.class);
        }


        // JDBC 4.1 methods below must be here so we compile on JDK 7; Once JRE 7 is required, should implement these via delegation.

        public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException
        {
            try
            {
                return _md.getPseudoColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }

        public boolean generatedKeyAlwaysReturned() throws SQLException
        {
            try
            {
                return _md.generatedKeyAlwaysReturned();
            }
            catch (SQLException e)
            {
                throw logAndCheckException(e);
            }
        }
    }
}
