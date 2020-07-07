/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.query.jdbc;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.CachedResultSet;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.ResultSetUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

/**
 * User: matthewb
 * Date: 4/25/12
 * Time: 6:18 PM
 */
public class QueryStatement implements Statement
{
    final QueryConnection _conn;
    boolean _closed = false;
    ResultSet _rs = null;
    static Logger _log = LogManager.getLogger(QueryStatement.class);

    QueryStatement(QueryConnection conn)
    {
        _conn = conn;
    }

    @Override
    protected void finalize() throws Throwable
    {
        assert null == _rs;
        assert _closed;
        super.finalize();
    }

    @Override
    public ResultSet executeQuery(String s) throws SQLException
    {
        QuerySchema schema = _conn.getQuerySchema();

        boolean cached = false;
        if (_log.isTraceEnabled())
            cached = true;

        _rs = QueryService.get().select(schema, s, null, true, cached);

        if (_log.isTraceEnabled() && _rs instanceof CachedResultSet)
        {
            ResultSetUtil.logData(_rs);
            _rs.beforeFirst();
        }

        return _rs;
    }

    @Override
    public int executeUpdate(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close()
    {
        if (_closed)
            return;
        _closed = true;
        ResultSetUtil.close(_rs);
        _rs = null;
    }

    @Override
    public int getMaxFieldSize()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxFieldSize(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxRows()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxRows(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEscapeProcessing(boolean b)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getQueryTimeout()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setQueryTimeout(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancel()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLWarning getWarnings()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearWarnings()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCursorName(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getResultSet()
    {
        return _rs;
    }

    @Override
    public int getUpdateCount()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getMoreResults()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchDirection(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchDirection()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchSize(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchSize()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetConcurrency()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetType()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addBatch(String s)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearBatch()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int[] executeBatch()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Connection getConnection()
    {
        return null;
    }

    @Override
    public boolean getMoreResults(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getGeneratedKeys()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String s, int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String s, int[] ints)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String s, String[] strings)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s, int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s, int[] ints)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s, String[] strings)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetHoldability()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPoolable(boolean b)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPoolable()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> tClass)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass)
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
}
