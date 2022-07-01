/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.OneBasedList;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.ResultSetWrapper;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DebugInfoDumper;
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
    /** Track the place that closed this statement for troubleshooting purposes */
    private @Nullable Throwable _closingStackTrace = null;
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

    public @NotNull Boolean isRequestThread()
    {
        return null != _requestThread ? _requestThread : ViewServlet.isRequestThread();
    }

    public @Nullable Throwable getClosingStackTrace()
    {
        return _closingStackTrace;
    }

    public void setRequestThread(@Nullable Boolean requestThread)
    {
        _requestThread = requestThread;
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterIndex, sqlType);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, int scale)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterIndex, sqlType, scale);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    public QueryLogging getQueryLogging()
    {
        return _queryLogging;
    }

    public void setQueryLogging(QueryLogging queryLogging)
    {
        _queryLogging = queryLogging;
    }

    @Override
    public boolean wasNull()
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).wasNull();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public String getString(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getString(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public boolean getBoolean(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getBoolean(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public byte getByte(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getByte(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public short getShort(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getShort(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getInt(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getInt(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public long getLong(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getLong(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public float getFloat(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getFloat(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public double getDouble(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getDouble(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(int parameterIndex, int scale)
            throws SQLException
    {
        try
        {
            //noinspection deprecation
            return ((CallableStatement)_stmt).getBigDecimal(parameterIndex, scale);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public byte[] getBytes(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getBytes(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Date getDate(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getDate(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Time getTime(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getTime(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getTimestamp(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Object getObject(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getObject(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getBigDecimal(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Object getObject(int i, Map<String, Class<?>> map)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getObject(i, map);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Ref getRef(int i)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getRef(i);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Blob getBlob(int i)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getBlob(i);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Clob getClob(int i)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getClob(i);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Array getArray(int i)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getArray(i);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Date getDate(int parameterIndex, Calendar cal)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getDate(parameterIndex, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Time getTime(int parameterIndex, Calendar cal)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getTime(parameterIndex, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex, Calendar cal)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getTimestamp(parameterIndex, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, String typeName)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterIndex, sqlType, typeName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, int scale)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType, scale);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, String typeName)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType, typeName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public URL getURL(int parameterIndex)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getURL(parameterIndex);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setURL(String parameterName, URL val)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).setURL(parameterName, val);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setNull(String parameterName, int sqlType)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).setNull(parameterName, sqlType);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBoolean(String parameterName, boolean x)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).setBoolean(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setByte(String parameterName, byte x)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).setByte(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setShort(String parameterName, short x)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).setShort(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setInt(String parameterName, int x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setInt(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setLong(String parameterName, long x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setLong(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setFloat(String parameterName, float x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setFloat(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setDouble(String parameterName, double x)
            throws SQLException
    {
        try
        {
            ((CallableStatement)_stmt).setDouble(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBigDecimal(String parameterName, BigDecimal x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setBigDecimal(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setString(String parameterName, String x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setString(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBytes(String parameterName, byte[] x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setBytes(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setDate(String parameterName, Date x)
            throws SQLException
    {
        try
        {
         ((CallableStatement)_stmt).setDate(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTime(String parameterName, Time x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setTime(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setTimestamp(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, int length)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setAsciiStream(parameterName, x, length);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, int length)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setBinaryStream(parameterName, x, length);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType, int scale)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setObject(parameterName, x, targetSqlType, scale);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setObject(parameterName, x, targetSqlType);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setObject(String parameterName, Object x)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setObject(parameterName, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, int length)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setCharacterStream(parameterName, reader, length);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setDate(String parameterName, Date x, Calendar cal)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setDate(parameterName, x, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTime(String parameterName, Time x, Calendar cal)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setTime(parameterName, x, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x, Calendar cal)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setTimestamp(parameterName, x, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setNull(String parameterName, int sqlType, String typeName)
            throws SQLException
    {
        try
        {
             ((CallableStatement)_stmt).setNull(parameterName, sqlType, typeName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public String getString(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getString(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public boolean getBoolean(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getBoolean(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public byte getByte(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getByte(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public short getShort(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getShort(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getInt(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getInt(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public long getLong(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getLong(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public float getFloat(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getFloat(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public double getDouble(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getDouble(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public byte[] getBytes(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getBytes(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Date getDate(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getDate(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Time getTime(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getTime(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Timestamp getTimestamp(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getTimestamp(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Object getObject(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getObject(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getBigDecimal(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Object getObject(String parameterName, Map<String, Class<?>> map)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getObject(parameterName, map);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Ref getRef(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getRef(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Blob getBlob(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getBlob(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Clob getClob(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getClob(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Array getArray(String parameterName)
            throws SQLException
    {
        try
        {
            return  ((CallableStatement)_stmt).getArray(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Date getDate(String parameterName, Calendar cal)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getDate(parameterName, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Time getTime(String parameterName, Calendar cal)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getTime(parameterName, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public Timestamp getTimestamp(String parameterName, Calendar cal)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getTimestamp(parameterName, cal);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public URL getURL(String parameterName)
            throws SQLException
    {
        try
        {
            return ((CallableStatement)_stmt).getURL(parameterName);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public ResultSet executeQuery()
            throws SQLException
    {
        beforeExecute();
        SQLException ex = null;
        try
        {
            if (null != _sqlStateTestException)
                throw new SQLException("Test sql exception", _sqlStateTestException);

            ResultSet rs = ((PreparedStatement)_stmt).executeQuery();
            assert MemTracker.getInstance().put(rs);
            return wrap(rs);
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

    @Override
    public int executeUpdate()
            throws SQLException
    {
        beforeExecute();
        SQLException ex = null;
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

    private void _set(int i, @Nullable Object o)
    {
        if (null == _parameters)
            _parameters = new OneBasedList<>(10);
        while (_parameters.size() < i)
            _parameters.add(null);
        _parameters.set(i, o);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setNull(parameterIndex, sqlType);
            _set(parameterIndex, null);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setBoolean(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setByte(int parameterIndex, byte x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setByte(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setShort(int parameterIndex, short x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setShort(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setInt(int parameterIndex, int x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setInt(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setLong(int parameterIndex, long x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setLong(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setFloat(int parameterIndex, float x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setFloat(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setDouble(int parameterIndex, double x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setDouble(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setBigDecimal(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setString(int parameterIndex, String x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setString(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setBytes(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setDate(int parameterIndex, Date x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setDate(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTime(int parameterIndex, Time x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setTime(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setTimestamp(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setAsciiStream(parameterIndex, x, length);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length)
            throws SQLException
    {
        try
        {
            //noinspection deprecation
            ((PreparedStatement)_stmt).setUnicodeStream(parameterIndex, x, length);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setBinaryStream(parameterIndex, x, length);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void clearParameters()
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).clearParameters();
            if (null != _parameters) _parameters.clear();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scale)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setObject(parameterIndex, x, targetSqlType, scale);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setObject(parameterIndex, x, targetSqlType);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setObject(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public boolean execute()
            throws SQLException
    {
        beforeExecute();
        SQLException ex = null;
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

    @Override
    public void addBatch()
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).addBatch();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    // NOTE: We intentionally do not store potentially large parameters (reader, blob, etc.)

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setCharacterStream(parameterIndex, reader, length);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setRef(int i, Ref x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setRef(i, x);
            _set(i, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBlob(int i, Blob x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setBlob(i, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setClob(int i, Clob x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setClob(i, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setArray(int i, Array x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setArray(i, x);
            _set(i, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public ResultSetMetaData getMetaData()
            throws SQLException
    {
        try
        {
            ResultSetMetaData rs = ((PreparedStatement)_stmt).getMetaData();
            assert MemTracker.getInstance().put(rs);
            return rs;
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setDate(parameterIndex, x, cal);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setTime(parameterIndex, x, cal);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setTimestamp(parameterIndex, x, cal);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setNull(parameterIndex, sqlType, typeName);
            _set(parameterIndex, null);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setURL(int parameterIndex, URL x)
            throws SQLException
    {
        try
        {
            ((PreparedStatement)_stmt).setURL(parameterIndex, x);
            _set(parameterIndex, x);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public ParameterMetaData getParameterMetaData()
            throws SQLException
    {
        try
        {
            return ((PreparedStatement)_stmt).getParameterMetaData();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public ResultSet executeQuery(String sql)
            throws SQLException
    {
        beforeExecute(sql);
        SQLException ex = null;
        try
        {
            ResultSet rs = _stmt.executeQuery(sql);
            assert MemTracker.getInstance().put(rs);
            return wrap(rs);
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

    @Override
    public int executeUpdate(String sql)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        SQLException ex = null;
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

    @Override
    public void close()
            throws SQLException
    {
        try
        {
            _stmt.close();
            if (AppProps.getInstance().isDevMode() && _closingStackTrace == null)
            {
                _closingStackTrace = new Throwable("Remembering stack for closing Statement on thread " + Thread.currentThread().getName());
            }
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getMaxFieldSize()
            throws SQLException
    {
        try
        {
            return _stmt.getMaxFieldSize();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setMaxFieldSize(int max)
            throws SQLException
    {
        try
        {
            _stmt.setMaxFieldSize(max);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getMaxRows()
            throws SQLException
    {
        try
        {
            return _stmt.getMaxRows();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setMaxRows(int max)
            throws SQLException
    {
        try
        {
            _stmt.setMaxRows(max);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setEscapeProcessing(boolean enable)
            throws SQLException
    {
        try
        {
            _stmt.setEscapeProcessing(enable);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getQueryTimeout()
            throws SQLException
    {
        try
        {
            return _stmt.getQueryTimeout();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setQueryTimeout(int seconds)
            throws SQLException
    {
        try
        {
            _stmt.setQueryTimeout(seconds);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void cancel()
            throws SQLException
    {
        try
        {
            userCancelled = true;
            _stmt.cancel();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public SQLWarning getWarnings()
            throws SQLException
    {
        try
        {
            return _stmt.getWarnings();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void clearWarnings()
            throws SQLException
    {
        try
        {
            _stmt.clearWarnings();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setCursorName(String name)
            throws SQLException
    {
        try
        {
            _stmt.setCursorName(name);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public boolean execute(String sql)
            throws SQLException
    {
        beforeExecute(sql);
        SQLException ex = null;
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

    @Override
    public ResultSet getResultSet()
            throws SQLException
    {
        try
        {
            ResultSet rs = _stmt.getResultSet();
            assert MemTracker.getInstance().put(rs);
            return wrap(rs);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getUpdateCount()
            throws SQLException
    {
        try
        {
            int updateCount;
            updateCount = _stmt.getUpdateCount();
            return updateCount;
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public boolean getMoreResults()
            throws SQLException
    {
        try
        {
            return _stmt.getMoreResults();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setFetchDirection(int direction)
            throws SQLException
    {
        try
        {
            _stmt.setFetchDirection(direction);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getFetchDirection()
            throws SQLException
    {
        try
        {
            return _stmt.getFetchDirection();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setFetchSize(int rows)
            throws SQLException
    {
        try
        {
            _stmt.setFetchSize(rows);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getFetchSize()
            throws SQLException
    {
        try
        {
            return _stmt.getFetchSize();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getResultSetConcurrency()
            throws SQLException
    {
        try
        {
            return _stmt.getResultSetConcurrency();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int getResultSetType()
            throws SQLException
    {
        try
        {
            return _stmt.getResultSetType();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void addBatch(String sql)
            throws SQLException
    {
        try
        {
            _stmt.addBatch(sql);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void clearBatch()
            throws SQLException
    {
        try
        {
            _stmt.clearBatch();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int[] executeBatch()
            throws SQLException
    {
        beforeExecute();
        SQLException ex = null;
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

    @Override
    public Connection getConnection()
    {
        return _conn;
    }

    @Override
    public boolean getMoreResults(int current)
            throws SQLException
    {
        try
        {
            return _stmt.getMoreResults(current);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public ResultSet getGeneratedKeys()
            throws SQLException
    {
        try
        {
            ResultSet rs = _stmt.getGeneratedKeys();
            assert MemTracker.getInstance().put(rs);
            return wrap(rs);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        SQLException ex = null;
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

    @Override
    public int executeUpdate(String sql, int[] columnIndexes)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        SQLException ex = null;
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

    @Override
    public int executeUpdate(String sql, String[] columnNames)
            throws SQLException
    {
        beforeExecute(sql);
        int rows = -1;
        SQLException ex = null;
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

    @Override
    public boolean execute(String sql, int autoGeneratedKeys)
            throws SQLException
    {
        beforeExecute(sql);
        SQLException ex = null;
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

    @Override
    public boolean execute(String sql, int[] columnIndexes)
            throws SQLException
    {
        beforeExecute(sql);
        SQLException ex = null;
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

    @Override
    public boolean execute(String sql, String[] columnNames)
            throws SQLException
    {
        beforeExecute(sql);
        SQLException ex = null;
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

    @Override
    public int getResultSetHoldability()
            throws SQLException
    {
        try
        {
            return _stmt.getResultSetHoldability();
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public String toString()
    {
        return _stmt.toString();
    }

    // TODO: These methods should be properly implemented via delegation.

    @Override
    public boolean isWrapperFor(Class<?> iface)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> iface)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPoolable()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPoolable(boolean poolable)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException
    {
        try
        {
            if (length > Integer.MAX_VALUE)
                throw new IllegalArgumentException("File length exceeds " + Integer.MAX_VALUE);
            setBinaryStream(parameterIndex, x, (int)length);
        }
        catch (SQLException e)
        {
            throw _conn.logAndCheckException(e);
        }
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClob(int parameterIndex, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNClob(int parameterIndex, NClob value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNString(int parameterIndex, String value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getCharacterStream(int parameterIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getCharacterStream(String parameterName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(int parameterIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(String parameterName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(int parameterIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(String parameterName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(int parameterIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(String parameterName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(int parameterIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(String parameterName)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(int parameterIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(String parameterName)
    {
        throw new UnsupportedOperationException();
    }//--

    @Override
    public void setAsciiStream(String parameterName, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBlob(String parameterName, Blob x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClob(String parameterName, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClob(String parameterName, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setClob(String parameterName, Clob x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNClob(String parameterName, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNClob(String parameterName, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNClob(String parameterName, NClob value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setNString(String parameterName, String value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRowId(String parameterName, RowId x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSQLXML(String parameterName, SQLXML xmlObject)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getObject(int parameterIndex, Class<T> type)
    {
        throw new UnsupportedOperationException();
    }

    // JDBC 4.1 methods below must be here so we compile on JDK 7; implement once we require JRE 7.

    @Override
    public <T> T getObject(String parameterName, Class<T> type)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void closeOnCompletion()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCloseOnCompletion()
    {
        throw new UnsupportedOperationException();
    }

    private void beforeExecute(String sql)
    {
        _debugSql = sql;
        beforeExecute();
    }

    private void beforeExecute()
    {
        // Crawler.java and BaseWebDriverTest.java use "8(" as attempted injection string
        if (_debugSql.contains("\"8(\"") && !_debugSql.contains("\"\"8(\"\"")) // 18196
            throw new IllegalArgumentException("SQL injection test failed: " + _debugSql);
        _msStart = System.currentTimeMillis();
    }


    private void afterExecute(String sql, @Nullable SQLException x, int rowsAffected)
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
    

    private static final Package _java_lang = java.lang.String.class.getPackage();

    private void _logStatement(String sql, @Nullable SQLException x, int rowsAffected, QueryLogging queryLogging)
    {
        long elapsed = System.currentTimeMillis() - _msStart;
        boolean isAssertEnabled = false;
        assert isAssertEnabled = true;

        if (isAssertEnabled && AppProps.getInstance().isDevMode() && isMutatingSql(sql))
            SpringActionController.executingMutatingSql(sql);

        // Make a copy of the parameters list (it gets modified below) and switch to zero-based list (_parameters is a one-based list)
        List<Object> zeroBasedList;

        if (null != _parameters)
        {
            zeroBasedList = new ArrayList<>(_parameters.size());

            // Translate parameters that can't be cached (for now, just JDBC arrays). I'd rather stash the original parameters and send
            // those to the query profiler, but this would require one or more non-standard methods on StatementWrapper. See #24314.
            for (Object o : _parameters)
            {
                if (o instanceof Array)
                {
                    try
                    {
                        o = ((Array) o).getArray();
                    }
                    catch (SQLException e)
                    {
                        _log.error("Could not retrieve array", e);
                    }
                }

                zeroBasedList.add(o);
            }
        }
        else
        {
            zeroBasedList = null;
        }

        // Hold on to this stack trace so that we can reuse it later (if collection has been enabled)
        StackTraceElement[] stack = QueryProfiler.getInstance().track(_conn.getScope(), sql, zeroBasedList, elapsed, _stackTrace, isRequestThread(), queryLogging);

        if (x != null)
        {
            _conn.logAndCheckException(x);
        }

        //noinspection ConstantConditions
        if (!_log.isEnabled(Level.DEBUG) && !isAssertEnabled)
            return;

        StringBuilder logEntry = new StringBuilder(sql.length() * 2);
        logEntry.append("SQL ");

        Integer sid = _conn.getSPID();
        if (sid != null)
            logEntry.append(" [").append(sid).append("]");
        if (_msStart != 0)
            logEntry.append(" time ").append(DateUtil.formatDuration(elapsed));

        if (-1 != rowsAffected)
            logEntry.append("\n    ").append(rowsAffected).append(" rows affected");

        logEntry.append("\n    ");
        logEntry.append(sql.trim().replace("\n", "\n    "));

        if (null != _parameters)
        {
            for (int i = 1; i <= _parameters.size(); i++)
            {
                try
                {
                    Object o = _parameters.get(i);
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
                        value = value.substring(0, 100) + ". . .";
                    logEntry.append("\n    --[").append(i).append("] ");
                    logEntry.append(value);
                    Class<?> c = null==o ? null : o.getClass();
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
        _appendTableStackTrace(logEntry, 5, stack);

        final String logString = logEntry.toString();
        _log.log(Level.DEBUG, logString);

        // check for deadlock or transaction related error
        if (SqlDialect.isTransactionException(x))
        {
            DebugInfoDumper.dumpThreads(_log);
        }
    }

    private boolean isMutatingSql(String sql)
    {
        return new MutatingSqlDetector(sql).isMutating();
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


    private void _appendTableStackTrace(StringBuilder sb, int count, @Nullable StackTraceElement[] ste)
    {
        if (ste != null)
        {
            int i = 1;  // Always skip getStackTrace() call
            for (; i < ste.length; i++)
            {
                String line = ste[i].toString();
                if (!(line.startsWith("org.labkey.api.data.") || line.startsWith("java.lang.Thread")))
                    break;
            }
            int last = Math.min(ste.length, i + count);
            for (; i < last; i++)
            {
                String line = ste[i].toString();
                if (line.startsWith("javax.servlet.http.HttpServlet.service("))
                    break;
                sb.append("\n    ").append(line);
            }
        }
    }


    public String getDebugSql()
    {
        return _debugSql;
    }


    ResultSet wrap(ResultSet rs)
    {
        return new ResultSetWrapper(rs)
        {
            @Override
            public boolean next() throws SQLException
            {
                try
                {
                    return super.next();
                }
                catch (SQLException x)
                {
                    if (SqlDialect.isTransactionException(x))
                        _logStatement(_debugSql, x, -1, getQueryLogging());
                    throw x;
                }
            }
        };
    }
}
