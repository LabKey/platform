/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MemTracker;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Selector that is driven by SQL, which subclasses can control how it's interpreted (LabKey SQL, raw DB SQL, etc)
 */
public abstract class SqlExecutingSelector<FACTORY extends SqlFactory, SELECTOR extends SqlExecutingSelector<FACTORY, SELECTOR>> extends BaseSelector<SELECTOR>
{
    int _maxRows = Table.ALL_ROWS;
    protected long _offset = Table.NO_OFFSET;
    @Nullable Map<String, Object> _namedParameters = null;
    private ConnectionFactory _connectionFactory = super::getConnection;

    private @Nullable AsyncQueryRequest _asyncRequest = null;
    private @Nullable StackTraceElement[] _loggingStacktrace = null;
    private final QueryLogging _queryLogging;
    private static final Logger LOGGER = LogManager.getLogger(SqlExecutingSelector.class);

    // SQL factory used for the duration of a single query execution. This allows reuse of instances, since query-specific
    // optimizations won't mutate the ExecutingSelector's externally set state.
    abstract protected FACTORY getSqlFactory(boolean isResultSet);

    SqlExecutingSelector(DbScope scope)
    {
        this(scope, null);
    }

    private SqlExecutingSelector(DbScope scope, Connection conn)
    {
        this(scope, conn, new QueryLogging());
    }

    SqlExecutingSelector(DbScope scope, Connection conn, @NotNull QueryLogging queryLogging)
    {
        super(scope, conn);
        _queryLogging = queryLogging;
    }

    private interface ConnectionFactory
    {
        Connection get() throws SQLException;
    }

    @Override
    public Connection getConnection() throws SQLException
    {
        return _connectionFactory.get();
    }

    /**
     * <p>Calling this method with cache=false ensures that the JDBC driver will not cache the produced ResultSet in
     * memory, which is useful when potentially working with very large (e.g., > 100MB) ResultSets. Calling it with
     * cache=true (the default setting) ensures the JDBC driver's default caching behavior.</p>
     *
     * <p>By default, the PostgreSQL JDBC driver caches every ResultSet in its entirety. This can lead to
     * OutOfMemoryErrors when working with very large ResultSets. When the underlying database is PostgreSQL, calling
     * this method with false instructs this SqlExecutingSelector to use an unshared Connection and configure it with
     * special settings that disable the driver caching. The trade-off is that the underlying database query will not
     * use the shared Connection that other code on the thread (up or down the call stack) may be using, making
     * Connection exhaustion more likely; that's why JDBC caching is on by default. Calling this method is not
     * compatible with passing in an explicit Connection to the constructor.</p>
     *
     * <p>When the underlying database is not PostgreSQL, calling this method has no effect, other than validating that
     * the stashed Connection is null.</p>
     *
     * @return this SqlExecutingSelector, to allow chaining of setters
     * @throws IllegalStateException if a Connection was provided at construction time
     */
    public SELECTOR setJdbcCaching(boolean cache)
    {
        if (null != _conn)
            throw new IllegalStateException("Calling setJdbcCaching() is not valid when a Connection has already been provided");

        if (!cache && getScope().getSqlDialect().isJdbcCachingEnabledByDefault())
        {
            // Get a fresh read-only connection directly from the pool... not part of the current transaction, not shared
            // with the thread, etc. This connection shouldn't cache ResultSet data in the JDBC driver, making it suitable
            // for streaming very large ResultSets. See #39753 and #39888.
            _connectionFactory = () -> {
                ConnectionWrapper conn = getScope().getPooledConnection(DbScope.ConnectionType.Pooled, null);
                conn.configureToDisableJdbcCaching(new SQLFragment("SELECT FakeColumn FROM FakeTable"));
                return conn;
            };
        }
        else
        {
            _connectionFactory = super::getConnection;
        }

        return getThis();
    }

    @Override
    protected ResultSetFactory getStandardResultSetFactory()
    {
        return getStandardResultSetFactory(true);
    }

    @Override
    protected ResultSetFactory getStandardResultSetFactory(boolean closeResultSet)
    {
        return new ExecutingResultSetFactory(getSqlFactory(false), closeResultSet, false);
    }

    public SELECTOR setMaxRows(int maxRows)
    {
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for maxRows; should be positive, Table.ALL_ROWS or Table.NO_ROWS";

        _maxRows = maxRows;
        return getThis();
    }

    private static boolean validOffset(long offset)
    {
        return offset >= 0;
    }

    public SELECTOR setOffset(long offset)
    {
        assert validOffset(offset) : offset + " is an illegal value for offset; should be positive or Table.NO_OFFSET";

        _offset = offset;
        return getThis();
    }

    public SELECTOR setNamedParameters(@Nullable Map<String, Object> namedParameters)
    {
        _namedParameters = namedParameters;
        return getThis();
    }

    /**
     *  Generates the current select SQL and returns the execution plan. SQL is generated as if getResultSet() had been
     *  called, which means that TableSelector limit, offset, sort, filter, etc. are all respected.
     */
    Collection<String> getExecutionPlan()
    {
        SqlDialect dialect = getScope().getSqlDialect();

        if (dialect.canShowExecutionPlan())
        {
            return dialect.getExecutionPlan(getScope(), getSqlFactory(true).getSql());
        }
        else
        {
            throw new IllegalStateException("Can't obtain execution plan from scope \"" + getScope().getDisplayName() + "\" (" + dialect.getProductName() + ")");
        }
    }

    /**
     *  Convenience method that generates select SQL and logs the execution plan to the passed in Logger (if non-null)
     */
    @SuppressWarnings("unused")
    public SELECTOR logExecutionPlan(@Nullable Logger logger)
    {
        if (null != logger)
            logger.info(String.join("\n", getExecutionPlan()));

        return getThis();
    }

    @Override
    @NotNull
    public QueryLogging getQueryLogging()
    {
        return _queryLogging;
    }

    protected TableResultSet getResultSet(ResultSetFactory factory, boolean cache)
    {
        return factory.handleResultSet((rs, conn) -> wrapResultSet(rs, conn, cache, true));
    }

    @Override
    protected TableResultSet wrapResultSet(ResultSet rs, Connection conn, boolean cache, boolean requireClose) throws SQLException
    {
        if (cache)
        {
            // Cache ResultSet and meta data
            return CachedResultSets.create(rs, true, _maxRows, _loggingStacktrace, getQueryLogging()).setRequireClose(requireClose);
        }
        else
        {
            // Wrap with a ResultSet implementation that closes the connection when closed
            return new ResultSetImpl(conn, getScope(), rs, _maxRows, getQueryLogging());
        }
    }

    @Override
    public TableResultSet getResultSet()
    {
        return getResultSet(true);
    }

    public TableResultSet getResultSet(boolean cache)
    {
        return getResultSet(cache, false);
    }

    /**
     * If no transaction is active and the SQL statement is a SELECT, this method assumes it is safe to tweak
     * connection parameters (such as disabling auto-commit, and never committing) to optimize memory and other
     * resource usage.
     *
     * If you are, for example, invoking a stored procedure that will have side effects via a SELECT statement,
     * you must explicitly start your own transaction and commit it.
     */
    public TableResultSet getResultSet(boolean cache, boolean scrollable)
    {
        SqlFactory sqlFactory = getSqlFactory(true);
        ExecutingResultSetFactory factory = new ExecutingResultSetFactory(sqlFactory, cache, scrollable);

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

        int retry = getScope().isTransactionActive() ? 0 : 1;

        while (true)
        {
            try
            {
                return rowCountResultSetFactory.handleResultSet((rs, conn) -> {
                    rs.next();
                    return rs.getLong(1);
                });
            }
            catch (RuntimeSQLException|ConcurrencyFailureException x)
            {
                if (SqlDialect.isTransactionException(x))
                    if (retry-- > 0)
                        continue;
                throw x;
            }
        }
    }


    @Override
    public boolean exists()
    {
        return exists(getSqlFactory(false));
    }

    protected boolean exists(FACTORY factory)
    {
        ResultSetFactory existsResultSetFactory = new ExecutingResultSetFactory(new ExistsSqlFactory(factory));

        return existsResultSetFactory.handleResultSet((rs, conn) -> {
            rs.next();
            return rs.getBoolean(1);
        });
    }


    void setAsyncRequest(@Nullable AsyncQueryRequest asyncRequest)
    {
        _asyncRequest = asyncRequest;

        if (null != asyncRequest)
            _loggingStacktrace = asyncRequest.getCreationStackTrace();
    }

    @Nullable
    private AsyncQueryRequest getAsyncRequest()
    {
        return _asyncRequest;
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

            SQLFragment existsSql = new SQLFragment("EXISTS\n(\n");
            existsSql.append(_factory.getSql());
            existsSql.append("\n)");

            // Turn this into an expression that can be SELECTed
            SQLFragment selectSql = dialect.wrapExistsExpression(existsSql);
            selectSql.insert(0, "SELECT ");

            return selectSql;
        }
    }


    // Produces a ResultSet based on SQL generated by a SqlFactory
    // CONSIDER: Pass in a selector and move this class to a separate file
    protected class ExecutingResultSetFactory implements ResultSetFactory
    {
        private final SqlFactory _factory;
        private final boolean _closeResultSet;
        private final boolean _scrollable;

        private @Nullable SQLFragment _sql = null;

        ExecutingResultSetFactory(SqlFactory factory)
        {
            this(factory, true, false);
        }

        ExecutingResultSetFactory(SqlFactory factory, boolean closeResultSet, boolean scrollable)
        {
            _factory = factory;
            _closeResultSet = closeResultSet;
            _scrollable = scrollable;
        }

        @Override
        public <T> T handleResultSet(ResultSetHandler<T> handler)
        {
            boolean success = false;
            Connection conn = null;
            ResultSet rs = null;

            try
            {
                // Stash the generated SQL in case we need to log it later
                _sql = _factory.getSql();

                // Short circuit if no SQL is generated, e.g., AggregateSqlFactory
                if (null != _sql)
                {
                    DbScope scope = getScope();
                    conn = getConnection();

                    try
                    {
                        rs = executeQuery(conn, _sql, _scrollable, getAsyncRequest(), _factory.getStatementMaxRows());
                    }
                    catch (SQLException outer)
                    {
                        boolean rethrowOuter;
                        try
                        {
                            rethrowOuter = conn.isClosed() || !conn.getAutoCommit() || scope.isTransactionActive() || !SqlDialect.isTransactionException(outer);
                        }
                        catch (SQLException se)
                        {
                            LOGGER.warn("Failed to assess state of connection after seeing SQL Exception, will re-throw original Exception.", se);
                            rethrowOuter = true;
                        }
                        if (rethrowOuter)
                            throw outer;

                        // retry if simple transaction exception
                        try
                        {
                            rs = executeQuery(conn, _sql, _scrollable, getAsyncRequest(), _factory.getStatementMaxRows());
                        }
                        catch (SQLException inner)
                        {
                            throw outer;
                        }
                    }

                    // Just to be safe: if processResultSet() throws SQLException then caller will close the result set; if it
                    // throws anything else, we will lose the result set, so we need to close it here.
                    boolean close = true;

                    try
                    {
                        _factory.processResultSet(rs);
                        close = false;
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

                T ret = handler.handle(rs, conn);
                success = true;

                return ret;
            }
            catch(RuntimeSQLException e)
            {
                handleSqlException(e.getSQLException(), conn);
                throw new IllegalStateException(getClass().getSimpleName() + ".handleSqlException() should have thrown an exception");
            }
            catch(BadSqlGrammarException e)
            {
                handleSqlException(e.getSQLException(), conn);
                throw new IllegalStateException(getClass().getSimpleName() + ".handleSqlException() should have thrown an exception");
            }
            catch(SQLException e)
            {
                handleSqlException(e, conn);
                throw new IllegalStateException(getClass().getSimpleName() + ".handleSqlException() should have thrown an exception");
            }
            finally
            {
                if (shouldClose() || !success)
                    close(rs, conn);

                afterComplete(rs);
            }
        }

        private ResultSet executeQuery(Connection conn, SQLFragment sqlFragment, boolean scrollable, @Nullable AsyncQueryRequest asyncRequest, @Nullable Integer statementMaxRows) throws SQLException
        {
            List<Object> parameters = sqlFragment.getParams();
            String sql = sqlFragment.getSQL();
            ResultSet rs;

            if (null == parameters || parameters.isEmpty())
            {
                Statement stmt = conn.createStatement(scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                initializeStatement(conn, stmt, asyncRequest, statementMaxRows);
                rs = stmt.executeQuery(sql);
            }
            else
            {
                PreparedStatement stmt = conn.prepareStatement(sql, scrollable ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                initializeStatement(conn, stmt, asyncRequest, statementMaxRows);

                try (Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
                {
                    Table.setParameters(stmt, parameters, jdbcParameters);
                    rs = stmt.executeQuery();
                }
            }

            if (asyncRequest != null)
            {
                asyncRequest.setStatement(null);
            }

            MemTracker.getInstance().put(rs);
            return rs;
        }

        private void initializeStatement(Connection conn, Statement stmt, @Nullable AsyncQueryRequest asyncRequest, @Nullable Integer statementMaxRows) throws SQLException
        {
            // Don't set max rows if null or special ALL_ROWS value (we're assuming statement.getMaxRows() defaults to 0, though this isn't actually documented...)
            if (null != statementMaxRows && Table.ALL_ROWS != statementMaxRows)
            {
                stmt.setMaxRows(statementMaxRows == Table.NO_ROWS ? 1 : statementMaxRows);
            }

            if (asyncRequest != null)
            {
                asyncRequest.setStatement(stmt);

                // If this is a background request then push the original stack trace into the statement wrapper so it gets
                // logged and stored in the query profiler.
                if (stmt instanceof StatementWrapper)
                {
                    StatementWrapper sw = (StatementWrapper)stmt;
                    sw.setStackTrace(asyncRequest.getCreationStackTrace());
                    sw.setRequestThread(true);      // AsyncRequests aren't really background threads; treat them as request threads.
                    sw.setQueryLogging(getQueryLogging());
                }
            }
            else
            {
                if (stmt instanceof StatementWrapper)
                {
                    StatementWrapper sw = (StatementWrapper)stmt;
                    sw.setQueryLogging(getQueryLogging());
                }
            }
        }

        @Override
        public boolean shouldClose()
        {
            return _closeResultSet;
        }

        @Override
        public void handleSqlException(SQLException e, @Nullable Connection conn)
        {
            Table.logException(_sql, conn, e, getLogLevel());

            if (null != _sql)
                ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.DialectSQL, _sql.toDebugString(), false);

            throw getExceptionFramework().translate(getScope(), "ExecutingSelector", e);
        }
    }
}
