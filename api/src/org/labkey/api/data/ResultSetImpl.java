/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.MemTracker;

import javax.sql.rowset.CachedRowSet;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;

/**
* User: adam
* Date: 10/2/12
*/
public class ResultSetImpl extends LoggingResultSetWrapper implements TableResultSet
{
    private static final Logger _log = Logger.getLogger(ResultSetImpl.class);

    private final @Nullable DbScope _scope;
    private final @Nullable Connection _connection;
    private int _maxRows = Table.ALL_ROWS;

    private boolean _isComplete = true;

    // for resource tracking
    private Throwable _debugCreated = null;
    protected boolean _wasClosed = false;


    public ResultSetImpl(ResultSet rs, QueryLogging queryLogging)
    {
        this(rs, Table.ALL_ROWS, queryLogging);
    }


    public ResultSetImpl(ResultSet rs, int maxRows, QueryLogging queryLogging)
    {
        this(null, null, rs, maxRows, queryLogging);
    }


    public ResultSetImpl(@Nullable Connection connection, @Nullable DbScope scope, ResultSet rs, int maxRows, QueryLogging queryLogging)
    {
        super(rs, queryLogging);
        MemTracker.getInstance().put(this);
        //noinspection ConstantConditions
        assert null != (_debugCreated = new Throwable("created ResultSetImpl"));
        _maxRows = maxRows;
        _connection = connection;
        _scope = scope;
    }


    public void setMaxRows(int i)
    {
        _maxRows = i;
    }


    public boolean isComplete()
    {
        return _isComplete;
    }


    void setComplete(boolean isComplete)
    {
        _isComplete = isComplete;
    }

    @Override
    public int getSize()
    {
        return -1;
    }

    public boolean next() throws SQLException
    {
        boolean success = super.next();
        if (!success || Table.ALL_ROWS == _maxRows)
            return success;
        if (getRow() == _maxRows + 1)
        {
            _isComplete = false;
        }
        return getRow() <= _maxRows;
    }


    public void close() throws SQLException
    {
        // Uncached case... close everything down
        if (null != _scope)
        {
            Statement stmt = getStatement();
            super.close();
            if (stmt != null)
            {
                stmt.close();
            }
            _scope.releaseConnection(_connection);
        }
        else
            super.close();

        _wasClosed = true;
    }


    public int size() throws SQLException
    {
        if (resultset instanceof CachedRowSet)
            return ((CachedRowSet) resultset).size();
        return -1;
    }


    public Iterator<Map<String, Object>> iterator()
    {
        return new ResultSetIterator(this);
    }

    public String getTruncationMessage(int maxRows)
    {
        return "Displaying only the first " + maxRows + " rows.";
    }

    public Map<String, Object> getRowMap()
    {
        throw new UnsupportedOperationException("getRowMap()");
    }


    protected void finalize() throws Throwable
    {
        if (!_wasClosed)
        {
            close();
            if (null != _debugCreated)
                _log.error("ResultSet was not closed", _debugCreated);
        }
        super.finalize();
    }
}
