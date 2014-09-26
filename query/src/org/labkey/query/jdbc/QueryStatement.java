/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.apache.log4j.Logger;
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
    static Logger _log = Logger.getLogger(QueryStatement.class);

    QueryStatement(QueryConnection conn)
    {
        this._conn = conn;
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
    public int executeUpdate(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws SQLException
    {
        if (_closed)
            return;
        _closed = true;
        ResultSetUtil.close(_rs);
        _rs = null;
    }

    @Override
    public int getMaxFieldSize() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxFieldSize(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxRows() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaxRows(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEscapeProcessing(boolean b) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getQueryTimeout() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setQueryTimeout(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancel() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearWarnings() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCursorName(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getResultSet() throws SQLException
    {
        return _rs;
    }

    @Override
    public int getUpdateCount() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getMoreResults() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchDirection(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchDirection() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchSize(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchSize() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetType() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addBatch(String s) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearBatch() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int[] executeBatch() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Connection getConnection() throws SQLException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean getMoreResults(int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String s, int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String s, int[] ints) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int executeUpdate(String s, String[] strings) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s, int i) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s, int[] ints) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean execute(String s, String[] strings) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResultSetHoldability() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPoolable(boolean b) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPoolable() throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> tClass) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException
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
}
