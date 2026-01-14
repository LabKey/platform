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

package org.labkey.api.data;

import org.apache.logging.log4j.LogManager;
import java.lang.ref.Cleaner;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ResultSetUtil;

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
    private static final Logger _log = LogManager.getLogger(ResultSetImpl.class);

    private int _maxRows;
    private boolean _countComplete;

    private boolean _isComplete = true;

    protected int _size;

    private static final Cleaner CLEANER = Cleaner.create();

    private static class ResultSetState implements Runnable
    {
        private final String _creatingThreadName;
        private final StackTraceElement[] _debugCreated;
        private final @Nullable DbScope _scope;
        private final @Nullable Connection _connection;
        private final ResultSet _rs; // This is the underlying result set

        private boolean _wasClosed = false;

        private ResultSetState(String creatingThreadName, StackTraceElement[] debugCreated, @Nullable DbScope scope, @Nullable Connection connection, ResultSet rs)
        {
            _creatingThreadName = creatingThreadName;
            _debugCreated = debugCreated;
            _scope = scope;
            _connection = connection;
            _rs = rs;
        }

        @Override
        public void run()
        {
            if (!_wasClosed)
            {
                _log.error("ResultSet was not closed. Created by thread " + _creatingThreadName + " with stacktrace: " + ExceptionUtil.renderStackTrace(_debugCreated));
                close(true);
            }
        }

        private void close(boolean closeUnderlying)
        {
            if (!_wasClosed)
            {
                try
                {
                    if (closeUnderlying)
                    {
                        if (null != _scope)
                        {
                            Statement stmt = _rs.getStatement();
                            _rs.close();
                            if (stmt != null)
                            {
                                stmt.close();
                            }
                            if (_connection != null)
                            {
                                _scope.releaseConnection(_connection);
                            }
                        }
                        else
                        {
                            _rs.close();
                        }
                    }
                }
                catch (SQLException e)
                {
                    _log.error("Error closing ResultSet", e);
                }
                finally
                {
                    _wasClosed = true;
                }
            }
        }
    }

    private final ResultSetState _state;
    private final Cleaner.Cleanable _cleanable;

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
        // Capturing the full stack can be expensive so only do it when enabled
        StackTraceElement[] debugCreated = MiniProfiler.getTroubleshootingStackTrace();
        // Capturing the thread name is cheap and provides useful context for troubleshooting failures to close
        String creatingThreadName = Thread.currentThread().getName();
        _maxRows = maxRows;
        try
        {
            if (connection == null && rs.getStatement() != null)
            {
                connection = rs.getStatement().getConnection();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        _state = new ResultSetState(creatingThreadName, debugCreated, scope, connection, rs);
        _cleanable = CLEANER.register(this, _state);
    }


    public void setMaxRows(int i)
    {
        _maxRows = i;
    }


    @Override
    public boolean isComplete()
    {
        return _isComplete;
    }


    void setComplete(boolean isComplete)
    {
        _isComplete = isComplete;
    }

    // A result set must be completely iterated before the size can be determined. This function sets that flag when
    // the result set is done iterating. This should be called as the return from the next() function of any classes
    // extending this class.
    protected boolean hasNext(boolean hasNext)
    {
        _countComplete = !hasNext;
        return hasNext;
    }

    @Override
    public int countAll() throws SQLException
    {
        //noinspection StatementWithEmptyBody
        while(next());
        return _size;
    }

    @Override
    public int getSize()
    {
        if (!_countComplete)
        {
            throw new IllegalStateException("ResultSet must first be completely iterated before getting size");
        }
        return _size;
    }

    @Override
    public boolean next() throws SQLException
    {
        boolean success = super.next();
        if (success)
        {
            if (Table.ALL_ROWS != _maxRows)
            {
                if (getRow() == _maxRows + 1)
                {
                    _isComplete = false;
                }
                success = getRow() <= _maxRows;
            }

            // Keep track of all of the rows that we've iterated
            if (_isComplete)
                _size = Math.max(_size, getRow());
        }

        return hasNext(success);
    }


    @Override
    public void close() throws SQLException
    {
        super.close();
        close(true);
    }

    protected void close(boolean closeUnderlying)
    {
        if (_state._wasClosed)
        {
            if (ResultSetUtil.STRICT_CHECKING)
                throw new IllegalStateException("ResultSet has already been closed!");
        }
        else
        {
            _state.close(closeUnderlying);
            _cleanable.clean();
        }
    }

    @Override
    public @NotNull Iterator<Map<String, Object>> iterator()
    {
        return new ResultSetIterator(this);
    }

    @Override
    public String getTruncationMessage(int maxRows)
    {
        return "Displaying only the first " + maxRows + " rows.";
    }

    @Override
    public Map<String, Object> getRowMap()
    {
        throw new UnsupportedOperationException("getRowMap()");
    }



    @Override
    public @Nullable Connection getConnection()
    {
        return _state._connection;
    }
}
