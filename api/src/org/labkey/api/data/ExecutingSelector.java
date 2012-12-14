/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Table.TableResultSet;
import org.labkey.api.data.dialect.SqlDialect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public abstract class ExecutingSelector<FACTORY extends SqlFactory, SELECTOR extends ExecutingSelector<FACTORY, SELECTOR>> extends BaseSelector implements Selector
{
    protected int _maxRows = Table.ALL_ROWS;
    protected long _offset = Table.NO_OFFSET;
    protected @Nullable Map<String, Object> _namedParameters = null;

    // SQL factory used for the duration of a single query. This allows reuse of instances, since query-specific
    // optimizations won't mutate the ExecutingSelector's externally set state.
    abstract protected FACTORY getSqlFactory(boolean isResultSet);

    protected ExecutingSelector(DbScope scope)
    {
        super(scope, null);     // TODO: Constructor that takes a Connection?
    }

    @Override
    protected ResultSetFactory getStandardResultSetFactory()
    {
        return new ExecutingResultSetFactory(getSqlFactory(false));
    }

    // SELECTOR and getThis() make it easier to chain setMaxRows() and setOffset() while returning the correct selector type from subclasses
    abstract protected SELECTOR getThis();

    public SELECTOR setMaxRows(int maxRows)
    {
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for maxRows; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        _maxRows = maxRows;
        return getThis();
    }

    public SELECTOR setOffset(long offset)
    {
        assert Table.validOffset(offset) : offset + " is an illegal value for offset; should be positive or Table.NO_OFFSET";

        _offset = offset;
        return getThis();
    }

    public SELECTOR setNamedParameters(@Nullable Map<String, Object> namedParameters)
    {
        _namedParameters = namedParameters;
        return getThis();
    }

    @Deprecated
    private <K> K handleResultSet(SqlFactory sqlFactory, ResultSetHandler<K> handler, boolean closeOnSuccess, boolean scrollable, boolean tweakJdbcParameters)
    {
        boolean queryFailed = false;
        Connection conn = null;
        ResultSet rs = null;

        SQLFragment sql = sqlFactory.getSql();

        try
        {
            if (null == sql)
            {
                return handler.handle(null, null);
            }

            DbScope scope = getScope();

            conn = getConnection();

            if (tweakJdbcParameters && Table.isSelect(sql.getSQL()) && !scope.isTransactionActive())
            {
                // Only fiddle with the Connection settings if we're not inside of a transaction so we won't mess
                // up any state the caller is relying on. Also, only do this when we're fairly certain that it's
                // a read-only statement (starting with SELECT)
                scope.getSqlDialect().configureToDisableJdbcCaching(conn);
            }

            rs = Table._executeQuery(conn, sql.getSQL(), sql.getParamsArray(), scrollable, getAsyncRequest(), sqlFactory.getStatementMaxRows());
            sqlFactory.processResultSet(rs);

            return handler.handle(rs, conn);
        }
        catch(SQLException e)
        {
            // TODO: Substitute SQL parameter placeholders with values?
            Table.logException(sql.getSQL(), sql.getParamsArray(), conn, e, getLogLevel());
            queryFailed = true;
            throw getExceptionFramework().translate(getScope(), "Message", sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            if (closeOnSuccess || queryFailed)
                close(rs, conn);
        }
    }

    @Deprecated
    protected Table.TableResultSet getResultSet(SqlFactory sqlFactory, boolean scrollable, boolean cache) throws SQLException
    {
        if (cache)
        {
            return handleResultSet(sqlFactory, new ResultSetHandler<TableResultSet>()
            {
                @Override
                public TableResultSet handle(ResultSet rs, Connection conn) throws SQLException
                {
                    return Table.cacheResultSet(rs, true, _maxRows, getLoggingStacktrace());
                }
            }, true, scrollable, true);
        }
        else
        {
            return handleResultSet(sqlFactory, new ResultSetHandler<TableResultSet>()
            {
                @Override
                public TableResultSet handle(ResultSet rs, Connection conn) throws SQLException
                {
                    return new ResultSetImpl(conn, getScope(), rs, _maxRows);
                }
            }, false, scrollable, true);
        }
    }

    protected Table.TableResultSet getResultSet(ResultSetFactory factory, boolean cache) throws SQLException
    {
        if (cache)
        {
            return handleResultSet(factory, new ResultSetHandler<TableResultSet>()
            {
                @Override
                public TableResultSet handle(ResultSet rs, Connection conn) throws SQLException
                {
                    // We're handing back a ResultSet, so cache the meta data
                    return Table.cacheResultSet(rs, true, _maxRows, getLoggingStacktrace());
                }
            });
        }
        else
        {
            return handleResultSet(factory, new ResultSetHandler<TableResultSet>()
            {
                @Override
                public TableResultSet handle(ResultSet rs, Connection conn) throws SQLException
                {
                    return new ResultSetImpl(conn, getScope(), rs, _maxRows);
                }
            });
        }
    }

    @Override
    public Table.TableResultSet getResultSet() throws SQLException
    {
        return getResultSet(false, true);
    }

    // TODO: Consider getResultSet(boolean cache)... which would necessitate reordering these parameters

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public Table.TableResultSet getResultSet(boolean scrollable, boolean cache) throws SQLException
    {
        boolean closeResultSet = cache;
        SqlFactory sqlFactory = getSqlFactory(true);
        ExecutingResultSetFactory factory = new ExecutingResultSetFactory(sqlFactory, closeResultSet, scrollable, true);

        return getResultSet(factory, cache);
    }

    @Override
    public long getRowCount()
    {
        return getRowCount(getSqlFactory(false));
    }

    protected long getRowCount(FACTORY factory)
    {
        ResultSetFactory rowCountResultSetFactory = new ExecutingResultSetFactory(new RowCountSqlFactory(factory));

        return handleResultSet(rowCountResultSetFactory, new ResultSetHandler<Long>()
        {
            @Override
            public Long handle(ResultSet rs, Connection conn) throws SQLException
            {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    @Override
    public boolean exists()
    {
        return exists(getSqlFactory(false));
    }

    protected boolean exists(FACTORY factory)
    {
        ResultSetFactory existsResultSetFactory = new ExecutingResultSetFactory(new ExistsSqlFactory(factory));

        return handleResultSet(existsResultSetFactory, new ResultSetHandler<Boolean>()
        {
            @Override
            public Boolean handle(ResultSet rs, Connection conn) throws SQLException
            {
                rs.next();
                return rs.getBoolean(1);
            }
        });
    }


    // Wraps the underlying factory's SQL with a SELECT COUNT(*) query
    private static class RowCountSqlFactory extends BaseSqlFactory
    {
        private final SqlFactory _factory;

        private RowCountSqlFactory(SqlFactory factory)
        {
            _factory = factory;
        }

        @Override
        public SQLFragment getSql()
        {
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM\n(\n");
            sql.append(_factory.getSql());
            sql.append("\n) x");

            return sql;
        }
    }


    // Wraps the underlying factory's SQL with an EXISTS query that returns true or false
    private class ExistsSqlFactory extends BaseSqlFactory
    {
        private final SqlFactory _factory;

        private ExistsSqlFactory(SqlFactory factory)
        {
            _factory = factory;
        }

        @Override
        public SQLFragment getSql()
        {
            SqlDialect dialect = getScope().getSqlDialect();

            // This EXISTS syntax works on PostgreSQL and SQL Server
            SQLFragment sql = new SQLFragment("SELECT CASE WHEN EXISTS\n(\n");
            sql.append(_factory.getSql());
            sql.append("\n)\n");
            sql.append("THEN ");
            sql.append(dialect.getBooleanTRUE());
            sql.append(" ELSE ");
            sql.append(dialect.getBooleanFALSE());
            sql.append(" END");

            return sql;
        }
    }


    protected class ExecutingResultSetFactory implements ResultSetFactory
    {
        private final SqlFactory _factory;
        private final boolean _closeResultSet;
        private final boolean _scrollable;
        private final boolean _tweakJdbcParameters;

        private SQLFragment _sql = null;

        protected ExecutingResultSetFactory(SqlFactory factory)
        {
            this(factory, true, false, false);
        }

        protected ExecutingResultSetFactory(SqlFactory factory, boolean closeResultSet, boolean scrollable, boolean tweakJdbcParameters)
        {
            _factory = factory;
            _closeResultSet = closeResultSet;
            _scrollable = scrollable;
            _tweakJdbcParameters = tweakJdbcParameters;
        }

        @Override
        public ResultSet getResultSet(Connection conn) throws SQLException
        {
            // Stash the generated SQL in case we need to log it later
            _sql = _factory.getSql();

            if (null == _sql)
            {
                return null;
            }

            DbScope scope = getScope();

            if (_tweakJdbcParameters && Table.isSelect(_sql.getSQL()) && !scope.isTransactionActive())
            {
                // Only fiddle with the Connection settings if we're not inside of a transaction so we won't mess
                // up any state the caller is relying on. Also, only do this when we're fairly certain that it's
                // a read-only statement (starting with SELECT)
                scope.getSqlDialect().configureToDisableJdbcCaching(conn);
            }

            ResultSet rs = Table._executeQuery(conn, _sql.getSQL(), _sql.getParamsArray(), _scrollable, getAsyncRequest(), _factory.getStatementMaxRows());

            // Just to be safe: if processResultSet() throws SQLException then caller will close the result set; if it
            // throws anything else, we will lose the result set, so we need to close it here.
            boolean close = true;

            try
            {
                _factory.processResultSet(rs);
                close = false;

                return rs;
            }
            catch (SQLException e)
            {
                close = false;
                throw e;
            }
            finally
            {
                if (close)
                    close(rs, null);  // Connection will be released by caller
            }
        }

        @Override
        public boolean shouldCloseResultSet()
        {
            return _closeResultSet;
        }

        @Override
        public void handleSqlException(SQLException e, Connection conn)
        {
            // TODO: Substitute SQL parameter placeholders with values?
            Table.logException(_sql.getSQL(), _sql.getParamsArray(), conn, e, getLogLevel());
            throw getExceptionFramework().translate(getScope(), "Message", _sql.getSQL(), e);  // TODO: Change message
        }
    }
}
