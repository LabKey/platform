/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Pair;

import java.sql.*;
import java.text.DateFormat;
import java.util.*;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public class ConnectionWrapper implements java.sql.Connection
{
    private final Connection _connection;
    private final SqlDialect _dialect;
    private final Integer _spid;
    private final java.util.Date _allocationTime = new java.util.Date();
    private final String _allocatingThreadName;

    private static final Map<ConnectionWrapper, Pair<Thread,Throwable>> _openConnections = Collections.synchronizedMap(new IdentityHashMap<ConnectionWrapper, Pair<Thread, Throwable>>());

    private static final Set<ConnectionWrapper> _loggedLeaks = new HashSet<ConnectionWrapper>();

    /**
     * Mapping from SPID (database process id) to the name of the most recent Java thread to use the connection
     * Useful for checking out database deadlock problems when debugging.
     */
    private static final Map<Integer, String> _mostRecentSPIDThreads = new HashMap<Integer, String>();

    /**
     * String representation of the map, useful because it's a pain to look through the
     * map if you're paused in the debugger due to JVM restrictions on invoking methods when paused.
     */
    @SuppressWarnings({"UNUSED_SYMBOL", "FieldCanBeLocal", "UnusedDeclaration"})
    private static String _mostRecentSPIDUsageString = "";
    private static Logger _logDefault = Logger.getLogger(ConnectionWrapper.class);
    private static boolean _explicitLogger = _logDefault.getLevel() != null || _logDefault.getParent() != null  && _logDefault.getParent().getName().equals("org.labkey.api.data");
    private Logger _log;

    protected ConnectionWrapper(Connection conn, SqlDialect dialect, Integer spid, Logger log) throws SQLException
    {
        _connection = conn;
        _spid = spid;
        _dialect = dialect;
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
        assert MemTracker.put(this);
        _allocatingThreadName = Thread.currentThread().getName();
        //noinspection ConstantConditions
        assert null == _openConnections.put(this,  new Pair<Thread,Throwable>(Thread.currentThread(), new Throwable())) || true;

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
            if (className.equals("org.labkey.api.view.ViewServlet"))
                break;
            if (className.endsWith("Controller") && !className.startsWith("org.labkey.api.view"))
                return Logger.getLogger(className);
        }
        return _logDefault;
    }


    public void setLogger(Logger log)
    {
        _log = log;
    }


    public Logger getLogger()
    {
        return _log;
    }


    public SqlDialect getDialect()
    {
        return _dialect;
    }


    public static boolean Dump()
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
        Set<Integer> result = new HashSet<Integer>();
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
        return _dialect.getStatementWrapper(this, _connection.createStatement());
    }

    public PreparedStatement prepareStatement(String sql)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareStatement(sql), sql);
    }

    public CallableStatement prepareCall(String sql)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareCall(sql), sql);
    }

    public String nativeSQL(String sql)
    throws SQLException
    {
        return _connection.nativeSQL(sql);
    }

    public void setAutoCommit(boolean autoCommit)
    throws SQLException
    {
        _connection.setAutoCommit(autoCommit);
    }

    public boolean getAutoCommit()
    throws SQLException
    {
        return _connection.getAutoCommit();
    }

    public void commit()
    throws SQLException
    {
        _connection.commit();
    }

    public void rollback()
    throws SQLException
    {
        _connection.rollback();
    }

    public void close()
    throws SQLException
    {
        //noinspection ConstantConditions
        assert null != _openConnections.remove(this) || true;
        _loggedLeaks.remove(this);
        // The Tomcat connection pool violates the API for close() - it throws an exception
        // if it's already been closed instead of doing a no-op
        if (null != _connection && !isClosed())
            _connection.close();
    }

    public boolean isClosed()
    throws SQLException
    {
        return _connection.isClosed();
    }

    public DatabaseMetaData getMetaData()
    throws SQLException
    {
        return _connection.getMetaData();
    }

    public void setReadOnly(boolean readOnly)
    throws SQLException
    {
        _connection.setReadOnly(readOnly);
    }

    public boolean isReadOnly()
    throws SQLException
    {
        return _connection.isReadOnly();
    }

    public void setCatalog(String catalog)
    throws SQLException
    {
        _connection.setCatalog(catalog);
    }

    public String getCatalog()
    throws SQLException
    {
        return _connection.getCatalog();
    }

    public void setTransactionIsolation(int level)
    throws SQLException
    {
        _connection.setTransactionIsolation(level);
    }

    public int getTransactionIsolation()
    throws SQLException
    {
        return _connection.getTransactionIsolation();
    }

    public SQLWarning getWarnings()
    throws SQLException
    {
        return _connection.getWarnings();
    }

    public void clearWarnings()
    throws SQLException
    {
        _connection.clearWarnings();
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.createStatement(resultSetType, resultSetConcurrency));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareStatement(sql, resultSetType, resultSetConcurrency), sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareCall(sql, resultSetType, resultSetConcurrency), sql);
    }

    public Map<String, Class<?>> getTypeMap()
    throws SQLException
    {
        return _connection.getTypeMap();
    }

    public void setTypeMap(Map<String, Class<?>> map)
    throws SQLException
    {
        _connection.setTypeMap(map);
    }

    public void setHoldability(int holdability)
    throws SQLException
    {
        _connection.setHoldability(holdability);
    }

    public int getHoldability()
    throws SQLException
    {
        return _connection.getHoldability();
    }

    public Savepoint setSavepoint()
    throws SQLException
    {
        return _connection.setSavepoint();
    }

    public Savepoint setSavepoint(String name)
    throws SQLException
    {
        return _connection.setSavepoint(name);
    }

    public void rollback(Savepoint savepoint)
    throws SQLException
    {
        _connection.rollback(savepoint);
    }

    public void releaseSavepoint(Savepoint savepoint)
    throws SQLException
    {
        _connection.releaseSavepoint(savepoint);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareStatement(sql, autoGeneratedKeys), sql);
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareStatement(sql, columnIndexes), sql);
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames)
    throws SQLException
    {
        return _dialect.getStatementWrapper(this, _connection.prepareStatement(sql, columnNames), sql);
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


    // The following methods are "implemented" to allow compiling and running on JDK/JRE 6.0 while still supporting
    // JDK/JRE 5.0.  If/when we require JDK/JRE 6.0, these methods should be properly implemented.


    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }


    public <T> T unwrap(Class<T> iface) throws SQLException
    {
        throw new UnsupportedOperationException();
    }


    public Array createArrayOf(String typeName, Object[] elements) throws SQLException
    {
        throw new UnsupportedOperationException();
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
}
