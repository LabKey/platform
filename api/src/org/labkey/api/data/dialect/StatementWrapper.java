/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

package org.labkey.api.data.dialect;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.OneBasedList;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.util.BreakpointThread;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ViewServlet;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class StatementWrapper implements Statement, PreparedStatement, CallableStatement
{
    private final ConnectionWrapper _conn;
    private final Statement _stmt;
    private final Logger _log;
    private String _debugSql = "";
    private long _msStart = 0;
    private boolean userCancelled = false;
    // NOTE: CallableStatement supports getObject(), but PreparedStatement doesn't
    private OneBasedList<Object> _parameters = null;
    private @Nullable StackTraceElement[] _stackTrace = null;
    private @Nullable Boolean _requestThread = null;
    private QueryLogging _queryLogging = QueryLogging.emptyQueryLogging();

    String _sqlStateTestException = null;


    public StatementWrapper(ConnectionWrapper conn, Statement stmt)
    {
        _conn = conn;
        _log = conn.getLogger();
        _stmt = stmt;
        assert MemTracker.getInstance().put(this);
    }

    public StatementWrapper(ConnectionWrapper conn, Statement stmt, String sql)
    {
        this(conn, stmt);
        _debugSql = sql;
    }

    public void setStackTrace(@Nullable StackTraceElement[] stackTrace)
    {
        _stackTrace = stackTrace;
    }

    public @Nullable Boolean isRequestThread()
    {
        return null != _requestThread ? _requestThread : ViewServlet.isRequestThread();
    }

    public void setRequestThread(@Nullable Boolean requestThread)
    {
        _requestThread = requestThread;
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

    public QueryLogging getQueryLogging()
    {
        return _queryLogging;
    }

    public void setQueryLogging(QueryLogging queryLogging)
    {
        _queryLogging = queryLogging;
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

    public void registerOutParameter(int parameterIndex, int sqlType, String typeName)
            throws SQLException
    {
        ((CallableStatement)_stmt).registerOutParameter(parameterIndex, sqlType, typeName);
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
            if (null != _sqlStateTestException)
                throw new SQLException("Test sql exception", _sqlStateTestException);

            ResultSet rs = ((PreparedStatement)_stmt).executeQuery();
            assert MemTracker.getInstance().put(rs);
            return rs;
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(_debugSql, ex, -1);
        }
    }

    public int executeUpdate()
            throws SQLException
    {
        beforeExecute();
        Exception ex = null;
        int rows = -1;
        try
        {
            return rows = ((PreparedStatement)_stmt).executeUpdate();
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(_debugSql, ex, rows);
        }
    }

    private boolean _set(int i, @Nullable Object o)
    {
        if (null == _parameters)
            _parameters = new OneBasedList<>(10);
        while (_parameters.size() < i)
            _parameters.add(null);
        _parameters.set(i, o);
        return true;
    }

    public void setNull(int parameterIndex, int sqlType)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setNull(parameterIndex, sqlType);
        _set(parameterIndex, null);
    }

    public void setBoolean(int parameterIndex, boolean x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setBoolean(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setByte(int parameterIndex, byte x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setByte(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setShort(int parameterIndex, short x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setShort(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setInt(int parameterIndex, int x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setInt(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setLong(int parameterIndex, long x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setLong(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setFloat(int parameterIndex, float x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setFloat(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setDouble(int parameterIndex, double x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setDouble(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setBigDecimal(int parameterIndex, BigDecimal x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setBigDecimal(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setString(int parameterIndex, String x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setString(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setBytes(int parameterIndex, byte[] x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setBytes(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setDate(int parameterIndex, Date x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setDate(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setTime(int parameterIndex, Time x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setTime(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setTimestamp(int parameterIndex, Timestamp x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setTimestamp(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, int length)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setAsciiStream(parameterIndex, x, length);
        _set(parameterIndex, x);
    }

    public void setUnicodeStream(int parameterIndex, InputStream x, int length)
            throws SQLException
    {
        //noinspection deprecation
        ((PreparedStatement)_stmt).setUnicodeStream(parameterIndex, x, length);
        _set(parameterIndex, x);
    }

    public void setBinaryStream(int parameterIndex, InputStream x, int length)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setBinaryStream(parameterIndex, x, length);
        _set(parameterIndex, x);
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
        _set(parameterIndex, x);
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setObject(parameterIndex, x, targetSqlType);
        _set(parameterIndex, x);
    }

    public void setObject(int parameterIndex, Object x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setObject(parameterIndex, x);
        _set(parameterIndex, x);
    }

    public boolean execute()
            throws SQLException
    {
        beforeExecute();
        Exception ex = null;
        Boolean ret=null;
        try
        {
            ret = ((PreparedStatement)_stmt).execute();
            return ret;
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            int rows = (ret==Boolean.FALSE) ? _stmt.getUpdateCount() : -1;
            afterExecute(_debugSql, ex, rows);
        }
    }

    public void addBatch()
            throws SQLException
    {
        ((PreparedStatement)_stmt).addBatch();
    }

    // NOTE: We intentionally do not store potentially large parameters (reader, blob, etc.)

    public void setCharacterStream(int parameterIndex, Reader reader, int length)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setCharacterStream(parameterIndex, reader, length);
    }

    public void setRef(int i, Ref x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setRef(i, x);
        _set(i, x);
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
        _set(i, x);
    }

    public ResultSetMetaData getMetaData()
            throws SQLException
    {
        ResultSetMetaData rs = ((PreparedStatement)_stmt).getMetaData();
        assert MemTracker.getInstance().put(rs);
        return rs;
    }

    public void setDate(int parameterIndex, Date x, Calendar cal)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setDate(parameterIndex, x, cal);
        _set(parameterIndex, x);
    }

    public void setTime(int parameterIndex, Time x, Calendar cal)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setTime(parameterIndex, x, cal);
        _set(parameterIndex, x);
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setTimestamp(parameterIndex, x, cal);
        _set(parameterIndex, x);
    }

    public void setNull(int parameterIndex, int sqlType, String typeName)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setNull(parameterIndex, sqlType, typeName);
        _set(parameterIndex, null);
    }

    public void setURL(int parameterIndex, URL x)
            throws SQLException
    {
        ((PreparedStatement)_stmt).setURL(parameterIndex, x);
        _set(parameterIndex, x);
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
            assert MemTracker.getInstance().put(rs);
            return rs;
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(_debugSql, ex, -1);
        }
    }

    public int executeUpdate(String sql)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        Exception ex = null;
        try
        {
            return rows = _stmt.executeUpdate(sql);
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(sql, ex, rows);
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
        userCancelled = true;
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
            afterExecute(sql, ex, -1);
        }
    }

    public ResultSet getResultSet()
            throws SQLException
    {
        ResultSet rs = _stmt.getResultSet();
        assert MemTracker.getInstance().put(rs);
        return rs;
    }

    public int getUpdateCount()
            throws SQLException
    {
        int updateCount;
        updateCount = _stmt.getUpdateCount();
        return updateCount;
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
        beforeExecute();
        Exception ex = null;
        try
        {
            return _stmt.executeBatch();
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(_debugSql, ex, -1);
        }
    }

    public Connection getConnection()
            throws SQLException
    {
        return _conn;
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
        assert MemTracker.getInstance().put(rs);
        return rs;
    }

    public int executeUpdate(String sql, int autoGeneratedKeys)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        Exception ex = null;
        try
        {
            return rows = _stmt.executeUpdate(sql, autoGeneratedKeys);
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(sql, ex, rows);
        }
    }

    public int executeUpdate(String sql, int[] columnIndexes)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        Exception ex = null;
        try
        {
            return rows = _stmt.executeUpdate(sql, columnIndexes);
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(sql, ex, rows);
        }
    }

    public int executeUpdate(String sql, String[] columnNames)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        Exception ex = null;
        try
        {
            return rows = _stmt.executeUpdate(sql, columnNames);
        }
        catch (SQLException sqlx)
        {
            ex = sqlx;
            throw sqlx;
        }
        finally
        {
            afterExecute(sql, ex, rows);
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
            afterExecute(sql, ex, -1);
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
            afterExecute(sql, ex, -1);
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
            afterExecute(sql, ex, -1);
        }
    }

    public int getResultSetHoldability()
            throws SQLException
    {
        return _stmt.getResultSetHoldability();
    }

    @Override
    public String toString()
    {
        return _stmt.toString();
    }

    // TODO: These methods should be properly implemented via delegation.

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

    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    // JDBC 4.1 methods below must be here so we compile on JDK 7; implement once we require JRE 7.

    public <T> T getObject(String parameterName, Class<T> type) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public void closeOnCompletion() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    public boolean isCloseOnCompletion() throws SQLException
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
        // Crawler.java and BaseWebDriverTest.java use "8(" as attempted injection string
        if (_debugSql.contains("\"8(\"") && !_debugSql.contains("\"\"8(\"\"")) // 18196
            throw new IllegalArgumentException("SQL injection test failed: " + _debugSql);
        _msStart = System.currentTimeMillis();
    }


    private void afterExecute(String sql, Exception x, int rowsAffected)
    {
        if (null != x)
        {
            ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.DialectSQL, sql, true);
            if (SqlDialect.isConfigurationException(x))
            {
                ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            }
        }
        _logStatement(sql, x, rowsAffected, getQueryLogging());
    }
    

    private static Package _java_lang = java.lang.String.class.getPackage();

    private void _logStatement(String sql, Exception x, int rowsAffected, QueryLogging queryLogging)
    {
        long elapsed = System.currentTimeMillis() - _msStart;
        boolean isAssertEnabled = false;
        assert isAssertEnabled = true;

        // Make a copy of the parameters list (it gets modified below) and switch to zero-based list (_parameters is a one-based list)
        List<Object> zeroBasedList = null != _parameters ? new ArrayList<>(_parameters.getUnderlyingList()) : null;
        QueryProfiler.getInstance().track(_conn.getScope(), sql, zeroBasedList, elapsed, _stackTrace, isRequestThread(), queryLogging);

        if (!_log.isEnabledFor(Level.DEBUG) && !isAssertEnabled)
            return;

        StringBuilder logEntry = new StringBuilder(sql.length() * 2);
        logEntry.append("SQL ");

        Integer sid = _conn.getSPID();
        if (sid != null)
            logEntry.append(" [").append(sid).append("]");
        if (_msStart != 0)
            logEntry.append(" time ").append(DateUtil.formatDuration(elapsed));

        if (-1 != rowsAffected)
            logEntry.append("\n    " + rowsAffected + " rows affected");

        logEntry.append("\n    ");
        logEntry.append(sql.trim().replace("\n", "\n    "));

        if (null != _parameters)
        {
            for (int i = 1; i <= _parameters.size(); i++)
            {
                try
                {
                    Object o = i <= _parameters.size() ? _parameters.get(i) : null;
                    String value;
                    if (o == null)
                        value = "NULL";
                    else if (o instanceof Container)
                        value = "'" + ((Container)o).getId() + "'        " + ((Container)o).getPath();
                    else if (o instanceof String)
                        value = "'" + escapeSql((String) o) + "'";
                    else
                        value = String.valueOf(o);
                    if (value.length() > 100)
                        value = value.substring(0,100) + ". . .";
                    logEntry.append("\n    --[").append(i).append("] ");
                    logEntry.append(value);
                    Class c = null==o ? null : o.getClass();
                    if (null != c && c != String.class && c != Integer.class)
                        logEntry.append(" :").append(c.getPackage() == _java_lang ? c.getSimpleName() : c.getName());
                }
                catch (Exception ex)
                {
                    /* */
                }
            }
            _parameters.clear();
        }
        _parameters = null;

        if (userCancelled)
            logEntry.append("\n    cancelled by user");
        if (null != x)
            logEntry.append("\n    ").append(x);
        _appendTableStackTrace(logEntry, 5);

        final String logString = logEntry.toString();
        _log.log(Level.DEBUG, logString);

// modified on trunk, just commenting out for now
//        MemTracker.getInstance().put(new MemTrackable(){
//            @Override
//            public String toMemTrackerString()
//            {
//                return logString;
//            }
//        });

        // check for deadlock or transaction related error
        if (x instanceof SQLException && SqlDialect.isTransactionException(x))
        {
            BreakpointThread.dumpThreads(_log);
        }
    }


    // Copied from Commons Lang 2.5 StringEscapeUtils. The method has been removed from Commons Lang 3.1 because it's
    // simplistic and misleading. But we're only using it for logging.
    private static String escapeSql(String str)
    {
        if (str == null)
        {
            return null;
        }
        return StringUtils.replace(str, "'", "''");
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


    public String getDebugSql()
    {
        return _debugSql;
    }
}
