/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
import org.apache.log4j.Priority;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.common.util.Pair;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
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

    private static final Map<ConnectionWrapper, Pair<Thread,Throwable>> _openConnections = Collections.synchronizedMap(new IdentityHashMap<ConnectionWrapper,Pair<Thread,Throwable>>());

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


    public SqlDialect getDialect()
    {
        return _dialect;
    }


    public static boolean Dump()
    {
        for (Pair<Thread,Throwable> p : _openConnections.values())
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
        return new StatementWrapper(_connection.createStatement());
    }

    public PreparedStatement prepareStatement(String sql)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareStatement(sql), sql);
    }

    public CallableStatement prepareCall(String sql)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareCall(sql), sql);
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
        if (null != _connection)
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
        return new StatementWrapper(_connection.createStatement(resultSetType, resultSetConcurrency));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareStatement(sql, resultSetType, resultSetConcurrency), sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareCall(sql, resultSetType, resultSetConcurrency), sql);
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
        return new StatementWrapper(_connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), sql);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareStatement(sql, autoGeneratedKeys), sql);
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareStatement(sql, columnIndexes), sql);
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames)
    throws SQLException
    {
        return new StatementWrapper(_connection.prepareStatement(sql, columnNames), sql);
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


    public class StatementWrapper implements Statement, PreparedStatement, CallableStatement
    {
        final Statement _stmt;
        String _debugSql = "";
        long _msStart = 0;
        // NOTE: CallableStatement supports getObject(), but PreparedStatement doesn't
        ArrayList<Object> _parameters = null;

        StatementWrapper(Statement stmt)
        {
            _stmt = stmt;
            assert MemTracker.put(this);
        }

        StatementWrapper(Statement stmt, String sql)
        {
            _stmt = stmt;
            _debugSql = sql;
            assert MemTracker.put(this);
        }
        
        public void registerOutParameter(int parameterIndex, int sqlType)
                throws SQLException
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterIndex, sqlType);
        }

        public void registerOutParameter(int parameterIndex, int sqlType, int scale)
                throws SQLException
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterIndex, sqlType, scale);
        }

        public boolean wasNull()
                throws SQLException
        {
            return ((CallableStatement)_stmt).wasNull();
        }

        public String getString(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getString(parameterIndex);
        }

        public boolean getBoolean(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getBoolean(parameterIndex);
        }

        public byte getByte(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getByte(parameterIndex);
        }

        public short getShort(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getShort(parameterIndex);
        }

        public int getInt(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getInt(parameterIndex);
        }

        public long getLong(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getLong(parameterIndex);
        }

        public float getFloat(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getFloat(parameterIndex);
        }

        public double getDouble(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getDouble(parameterIndex);
        }

        public BigDecimal getBigDecimal(int parameterIndex, int scale)
                throws SQLException
        {
            //noinspection deprecation
            return ((CallableStatement)_stmt).getBigDecimal(parameterIndex, scale);
        }

        public byte[] getBytes(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getBytes(parameterIndex);
        }

        public Date getDate(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getDate(parameterIndex);
        }

        public Time getTime(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getTime(parameterIndex);
        }

        public Timestamp getTimestamp(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getTimestamp(parameterIndex);
        }

        public Object getObject(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getObject(parameterIndex);
        }

        public BigDecimal getBigDecimal(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getBigDecimal(parameterIndex);
        }

        public Object getObject(int i, Map<String, Class<?>> map)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getObject(i, map);
        }

        public Ref getRef(int i)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getRef(i);
        }

        public Blob getBlob(int i)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getBlob(i);
        }

        public Clob getClob(int i)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getClob(i);
        }

        public Array getArray(int i)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getArray(i);
        }

        public Date getDate(int parameterIndex, Calendar cal)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getDate(parameterIndex, cal);
        }

        public Time getTime(int parameterIndex, Calendar cal)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getTime(parameterIndex, cal);
        }

        public Timestamp getTimestamp(int parameterIndex, Calendar cal)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getTimestamp(parameterIndex, cal);
        }

        public void registerOutParameter(int paramIndex, int sqlType, String typeName)
                throws SQLException
        {
            ((CallableStatement)_stmt).registerOutParameter(paramIndex, sqlType, typeName);
        }

        public void registerOutParameter(String parameterName, int sqlType)
                throws SQLException
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType);
        }

        public void registerOutParameter(String parameterName, int sqlType, int scale)
                throws SQLException
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType, scale);
        }

        public void registerOutParameter(String parameterName, int sqlType, String typeName)
                throws SQLException
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType, typeName);
        }

        public URL getURL(int parameterIndex)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getURL(parameterIndex);
        }

        public void setURL(String parameterName, URL val)
                throws SQLException
        {
            ((CallableStatement)_stmt).setURL(parameterName, val);
        }

        public void setNull(String parameterName, int sqlType)
                throws SQLException
        {
            ((CallableStatement)_stmt).setNull(parameterName, sqlType);
        }

        public void setBoolean(String parameterName, boolean x)
                throws SQLException
        {
            ((CallableStatement)_stmt).setBoolean(parameterName, x);
        }

        public void setByte(String parameterName, byte x)
                throws SQLException
        {
            ((CallableStatement)_stmt).setByte(parameterName, x);
        }

        public void setShort(String parameterName, short x)
                throws SQLException
        {
            ((CallableStatement)_stmt).setShort(parameterName, x);
        }

        public void setInt(String parameterName, int x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setInt(parameterName, x);
        }

        public void setLong(String parameterName, long x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setLong(parameterName, x);
        }

        public void setFloat(String parameterName, float x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setFloat(parameterName, x);
        }

        public void setDouble(String parameterName, double x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setDouble(parameterName, x);
        }

        public void setBigDecimal(String parameterName, BigDecimal x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setBigDecimal(parameterName, x);
        }

        public void setString(String parameterName, String x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setString(parameterName, x);
        }

        public void setBytes(String parameterName, byte[] x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setBytes(parameterName, x);
        }

        public void setDate(String parameterName, Date x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setDate(parameterName, x);
        }

        public void setTime(String parameterName, Time x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setTime(parameterName, x);
        }

        public void setTimestamp(String parameterName, Timestamp x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setTimestamp(parameterName, x);
        }

        public void setAsciiStream(String parameterName, InputStream x, int length)
                throws SQLException
        {
             ((CallableStatement)_stmt).setAsciiStream(parameterName, x, length);
        }

        public void setBinaryStream(String parameterName, InputStream x, int length)
                throws SQLException
        {
             ((CallableStatement)_stmt).setBinaryStream(parameterName, x, length);
        }

        public void setObject(String parameterName, Object x, int targetSqlType, int scale)
                throws SQLException
        {
             ((CallableStatement)_stmt).setObject(parameterName, x, targetSqlType, scale);
        }

        public void setObject(String parameterName, Object x, int targetSqlType)
                throws SQLException
        {
             ((CallableStatement)_stmt).setObject(parameterName, x, targetSqlType);
        }

        public void setObject(String parameterName, Object x)
                throws SQLException
        {
             ((CallableStatement)_stmt).setObject(parameterName, x);
        }

        public void setCharacterStream(String parameterName, Reader reader, int length)
                throws SQLException
        {
             ((CallableStatement)_stmt).setCharacterStream(parameterName, reader, length);
        }

        public void setDate(String parameterName, Date x, Calendar cal)
                throws SQLException
        {
             ((CallableStatement)_stmt).setDate(parameterName, x, cal);
        }

        public void setTime(String parameterName, Time x, Calendar cal)
                throws SQLException
        {
             ((CallableStatement)_stmt).setTime(parameterName, x, cal);
        }

        public void setTimestamp(String parameterName, Timestamp x, Calendar cal)
                throws SQLException
        {
             ((CallableStatement)_stmt).setTimestamp(parameterName, x, cal);
        }

        public void setNull(String parameterName, int sqlType, String typeName)
                throws SQLException
        {
             ((CallableStatement)_stmt).setNull(parameterName, sqlType, typeName);
        }

        public String getString(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getString(parameterName);
        }

        public boolean getBoolean(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getBoolean(parameterName);
        }

        public byte getByte(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getByte(parameterName);
        }

        public short getShort(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getShort(parameterName);
        }

        public int getInt(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getInt(parameterName);
        }

        public long getLong(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getLong(parameterName);
        }

        public float getFloat(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getFloat(parameterName);
        }

        public double getDouble(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getDouble(parameterName);
        }

        public byte[] getBytes(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getBytes(parameterName);
        }

        public Date getDate(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getDate(parameterName);
        }

        public Time getTime(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getTime(parameterName);
        }

        public Timestamp getTimestamp(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getTimestamp(parameterName);
        }

        public Object getObject(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getObject(parameterName);
        }

        public BigDecimal getBigDecimal(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getBigDecimal(parameterName);
        }

        public Object getObject(String parameterName, Map<String, Class<?>> map)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getObject(parameterName, map);
        }

        public Ref getRef(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getRef(parameterName);
        }

        public Blob getBlob(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getBlob(parameterName);
        }

        public Clob getClob(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getClob(parameterName);
        }

        public Array getArray(String parameterName)
                throws SQLException
        {
            return  ((CallableStatement)_stmt).getArray(parameterName);
        }

        public Date getDate(String parameterName, Calendar cal)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getDate(parameterName, cal);
        }

        public Time getTime(String parameterName, Calendar cal)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getTime(parameterName, cal);
        }

        public Timestamp getTimestamp(String parameterName, Calendar cal)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getTimestamp(parameterName, cal);
        }

        public URL getURL(String parameterName)
                throws SQLException
        {
            return ((CallableStatement)_stmt).getURL(parameterName);
        }

        public ResultSet executeQuery()
                throws SQLException
        {
            beforeExecute();
            Exception ex = null;
            try
            {
                ResultSet rs = ((PreparedStatement)_stmt).executeQuery();
                assert MemTracker.put(rs);
                return rs;
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(_debugSql, ex);
            }
        }

        public int executeUpdate()
                throws SQLException
        {
            beforeExecute();
            Exception ex = null;
            try
            {
                return ((PreparedStatement)_stmt).executeUpdate();
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(_debugSql, ex);
            }
        }

        private boolean _set(int i, Object o)
        {
            if (null == _parameters)
                _parameters = new ArrayList<Object>(10);
            while (_parameters.size() < i+1)
                _parameters.add(null);
            _parameters.set(i, o);
            return true;
        }

        public void setNull(int parameterIndex, int sqlType)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setNull(parameterIndex, sqlType);
        }

        public void setBoolean(int parameterIndex, boolean x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setBoolean(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setByte(int parameterIndex, byte x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setByte(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setShort(int parameterIndex, short x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setShort(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setInt(int parameterIndex, int x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setInt(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setLong(int parameterIndex, long x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setLong(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setFloat(int parameterIndex, float x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setFloat(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setDouble(int parameterIndex, double x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setDouble(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setBigDecimal(int parameterIndex, BigDecimal x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setBigDecimal(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setString(int parameterIndex, String x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setString(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setBytes(int parameterIndex, byte[] x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setBytes(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setDate(int parameterIndex, Date x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setDate(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setTime(int parameterIndex, Time x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setTime(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setTimestamp(int parameterIndex, Timestamp x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setTimestamp(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public void setAsciiStream(int parameterIndex, InputStream x, int length)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setAsciiStream(parameterIndex, x, length);
            assert _set(parameterIndex, x);
        }

        public void setUnicodeStream(int parameterIndex, InputStream x, int length)
                throws SQLException
        {
            //noinspection deprecation
            ((PreparedStatement)_stmt).setUnicodeStream(parameterIndex, x, length);
            assert _set(parameterIndex, x);
        }

        public void setBinaryStream(int parameterIndex, InputStream x, int length)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setBinaryStream(parameterIndex, x, length);
            assert _set(parameterIndex, x);
        }

        public void clearParameters()
                throws SQLException
        {
            ((PreparedStatement)_stmt).clearParameters();
            if (null != _parameters) _parameters.clear();
        }

        public void setObject(int parameterIndex, Object x, int targetSqlType, int scale)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setObject(parameterIndex, x, targetSqlType, scale);
            assert _set(parameterIndex, x);
        }

        public void setObject(int parameterIndex, Object x, int targetSqlType)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setObject(parameterIndex, x, targetSqlType);
            assert _set(parameterIndex, x);
        }

        public void setObject(int parameterIndex, Object x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setObject(parameterIndex, x);
            assert _set(parameterIndex, x);
        }

        public boolean execute()
                throws SQLException
        {
            beforeExecute();
            Exception ex = null;
            try
            {
                return ((PreparedStatement)_stmt).execute();
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(_debugSql, ex);
            }
        }

        public void addBatch()
                throws SQLException
        {
            ((PreparedStatement)_stmt).addBatch();
        }

        public void setCharacterStream(int parameterIndex, Reader reader, int length)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setCharacterStream(parameterIndex, reader, length);
        }

        public void setRef(int i, Ref x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setRef(i, x);
        }

        public void setBlob(int i, Blob x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setBlob(i, x);
        }

        public void setClob(int i, Clob x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setClob(i, x);
        }

        public void setArray(int i, Array x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setArray(i, x);
        }

        public ResultSetMetaData getMetaData()
                throws SQLException
        {
            ResultSetMetaData rs = ((PreparedStatement)_stmt).getMetaData();
            assert MemTracker.put(rs);
            return rs;
        }

        public void setDate(int parameterIndex, Date x, Calendar cal)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setDate(parameterIndex, x, cal);
        }

        public void setTime(int parameterIndex, Time x, Calendar cal)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setTime(parameterIndex, x, cal);
        }

        public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setTimestamp(parameterIndex, x, cal);
        }

        public void setNull(int paramIndex, int sqlType, String typeName)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setNull(paramIndex, sqlType, typeName);
        }

        public void setURL(int parameterIndex, URL x)
                throws SQLException
        {
            ((PreparedStatement)_stmt).setURL(parameterIndex, x);
        }

        public ParameterMetaData getParameterMetaData()
                throws SQLException
        {
            return ((PreparedStatement)_stmt).getParameterMetaData();
        }

        public ResultSet executeQuery(String sql)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                ResultSet rs = _stmt.executeQuery(sql);
                assert MemTracker.put(rs);
                return rs;
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(_debugSql, ex);
            }
        }

        public int executeUpdate(String sql)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.executeUpdate(sql);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public void close()
                throws SQLException
        {
            _stmt.close();
        }

        public int getMaxFieldSize()
                throws SQLException
        {
            return _stmt.getMaxFieldSize();
        }

        public void setMaxFieldSize(int max)
                throws SQLException
        {
            _stmt.setMaxFieldSize(max);
        }

        public int getMaxRows()
                throws SQLException
        {
            return _stmt.getMaxRows();
        }

        public void setMaxRows(int max)
                throws SQLException
        {
            _stmt.setMaxRows(max);
        }

        public void setEscapeProcessing(boolean enable)
                throws SQLException
        {
            _stmt.setEscapeProcessing(enable);
        }

        public int getQueryTimeout()
                throws SQLException
        {
            return _stmt.getQueryTimeout();
        }

        public void setQueryTimeout(int seconds)
                throws SQLException
        {
            _stmt.setQueryTimeout(seconds);
        }

        public void cancel()
                throws SQLException
        {
            _stmt.cancel();
        }

        public SQLWarning getWarnings()
                throws SQLException
        {
            return _stmt.getWarnings();
        }

        public void clearWarnings()
                throws SQLException
        {
            _stmt.clearWarnings();
        }

        public void setCursorName(String name)
                throws SQLException
        {
            _stmt.setCursorName(name);
        }

        public boolean execute(String sql)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.execute(sql);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public ResultSet getResultSet()
                throws SQLException
        {
            ResultSet rs = _stmt.getResultSet();
            assert MemTracker.put(rs);
            return rs;
        }

        public int getUpdateCount()
                throws SQLException
        {
            return _stmt.getUpdateCount();
        }

        public boolean getMoreResults()
                throws SQLException
        {
            return _stmt.getMoreResults();
        }

        public void setFetchDirection(int direction)
                throws SQLException
        {
            _stmt.setFetchDirection(direction);
        }

        public int getFetchDirection()
                throws SQLException
        {
            return _stmt.getFetchDirection();
        }

        public void setFetchSize(int rows)
                throws SQLException
        {
            _stmt.setFetchSize(rows);
        }

        public int getFetchSize()
                throws SQLException
        {
            return _stmt.getFetchSize();
        }

        public int getResultSetConcurrency()
                throws SQLException
        {
            return _stmt.getResultSetConcurrency();
        }

        public int getResultSetType()
                throws SQLException
        {
            return _stmt.getResultSetType();
        }

        public void addBatch(String sql)
                throws SQLException
        {
            _stmt.addBatch(sql);
        }

        public void clearBatch()
                throws SQLException
        {
            _stmt.clearBatch();
        }

        public int[] executeBatch()
                throws SQLException
        {
            // UNDONE: logging
            return _stmt.executeBatch();
        }

        public Connection getConnection()
                throws SQLException
        {
            return ConnectionWrapper.this;
        }

        public boolean getMoreResults(int current)
                throws SQLException
        {
            return _stmt.getMoreResults(current);
        }

        public ResultSet getGeneratedKeys()
                throws SQLException
        {
            ResultSet rs = _stmt.getGeneratedKeys();
            assert MemTracker.put(rs);
            return rs;
        }

        public int executeUpdate(String sql, int autoGeneratedKeys)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.executeUpdate(sql, autoGeneratedKeys);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public int executeUpdate(String sql, int[] columnIndexes)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.executeUpdate(sql, columnIndexes);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public int executeUpdate(String sql, String[] columnNames)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.executeUpdate(sql, columnNames);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public boolean execute(String sql, int autoGeneratedKeys)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.execute(sql, autoGeneratedKeys);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public boolean execute(String sql, int[] columnIndexes)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.execute(sql, columnIndexes);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public boolean execute(String sql, String[] columnNames)
                throws SQLException
        {
            beforeExecute(sql);
            Exception ex = null;
            try
            {
                return _stmt.execute(sql, columnNames);
            }
            catch (SQLException sqlx)
            {
                ex = sqlx;
                throw sqlx;
            }
            finally
            {
                _logStatement(sql, ex);
            }
        }

        public int getResultSetHoldability()
                throws SQLException
        {
            return _stmt.getResultSetHoldability();
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

        public boolean isClosed() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public boolean isPoolable() throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setPoolable(boolean poolable) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException
        {
            if (length > Integer.MAX_VALUE)
                throw new IllegalArgumentException("File length exceeds " + Integer.MAX_VALUE);
            setBinaryStream(parameterIndex, x, (int)length);
        }

        public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setClob(int parameterIndex, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setClob(int parameterIndex, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNClob(int parameterIndex, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNClob(int parameterIndex, NClob value) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNString(int parameterIndex, String value) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setRowId(int parameterIndex, RowId x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public Reader getCharacterStream(int parameterIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public Reader getCharacterStream(String parameterName) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public Reader getNCharacterStream(int parameterIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public Reader getNCharacterStream(String parameterName) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public NClob getNClob(int parameterIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public NClob getNClob(String parameterName) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public String getNString(int parameterIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public String getNString(String parameterName) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public RowId getRowId(int parameterIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public RowId getRowId(String parameterName) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public SQLXML getSQLXML(int parameterIndex) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public SQLXML getSQLXML(String parameterName) throws SQLException
        {
            throw new UnsupportedOperationException();
        }//--

        public void setAsciiStream(String parameterName, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBinaryStream(String parameterName, InputStream x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBlob(String parameterName, InputStream inputStream) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setBlob(String parameterName, Blob x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setCharacterStream(String parameterName, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setClob(String parameterName, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setClob(String parameterName, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setClob(String parameterName, Clob x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNCharacterStream(String parameterName, Reader value) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNClob(String parameterName, Reader reader) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNClob(String parameterName, Reader reader, long length) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNClob(String parameterName, NClob value) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setNString(String parameterName, String value) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setRowId(String parameterName, RowId x) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException
        {
            throw new UnsupportedOperationException();
        }


        private final void beforeExecute(String sql)
        {
            _debugSql = sql;
            beforeExecute();
        }

        private final void beforeExecute()
        {
            // see Crawler.java in and BaseSeleniumWebTest.java
            if (_debugSql.contains("\"8(\""))
                throw new IllegalArgumentException("SQL injection test failed: " + _debugSql);
            _msStart = System.currentTimeMillis();
        }
        
        private void _logStatement(String sql, Exception x)
        {
            if (!_log.isEnabledFor(Priority.DEBUG))
                return;
            long ms = System.currentTimeMillis() - _msStart;

            Connection conn = ConnectionWrapper.this;

            StringBuilder logEntry = new StringBuilder(sql.length() * 2);
            logEntry.append("SQL ");

            Integer sid = null;
            if (conn instanceof ConnectionWrapper)
                sid = ((ConnectionWrapper)conn).getSPID();
            if (sid != null)
                logEntry.append(" [").append(sid).append("]");
            if (_msStart != 0)
                logEntry.append(" time ").append(DateUtil.formatDuration(ms));

            String[] lines = sql.split("\n");
            for (String line : lines)
                logEntry.append("\n    ").append(line);

            if (null != _parameters)
            {
                for (int i=1 ; i<_parameters.size() ; i++)
                {
                    try
                    {
                        Object o = i < _parameters.size() ? _parameters.get(i) : null;
                        String value = String.valueOf(o);
                        logEntry.append("\n    ?[").append(i).append("] ").append(value);
                    }
                    catch (Exception ex)
                    {
                        /* */
                    }
                }
                _parameters.clear();
            }
            _parameters = null;

            if (null != x)
                logEntry.append("\n    ").append(x);
            _appendTableStackTrace(logEntry, 5);
            _log.log(Priority.DEBUG, logEntry);
        }


        private void _appendTableStackTrace(StringBuilder sb, int count)
        {
            StackTraceElement[] ste = Thread.currentThread().getStackTrace();
            int i=1;  // Always skip getStackTrace() call
            for ( ; i<ste.length ; i++)
            {
                String line = ste[i].toString();
                if (!(line.startsWith("org.labkey.api.data.") || line.startsWith("java.lang.Thread")))
                    break;
            }
            int last = Math.min(ste.length,i+count);
            for ( ; i<last ; i++)
            {
                String line = ste[i].toString();
                if (line.startsWith("javax.servlet.http.HttpServlet.service("))
                    break;
                sb.append("\n    ").append(line);
            }
        }
    }
}
