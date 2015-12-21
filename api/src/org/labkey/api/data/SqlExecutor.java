/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseSelector.ResultSetHandler;
import org.labkey.api.data.BaseSelector.StatementHandler;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.ExceptionUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;

/**
 * Knows how to execute SQL against the underlying database, getting a connection as appropriate
 * User: adam
 * Date: 10/25/11
 */
public class SqlExecutor extends JdbcCommand<SqlExecutor>
{
    private static final NormalStatementExecutor NORMAL_EXECUTOR = new NormalStatementExecutor();

    // When conn is null (vast majority of cases), a pooled connection will be obtained from the scope and closed after
    // execution. If conn is provided then that connection will be used and will NOT be closed afterwards.
    public SqlExecutor(@NotNull DbScope scope, @Nullable Connection conn)
    {
        super(scope, conn);
    }

    public SqlExecutor(@NotNull DbScope scope)
    {
        super(scope, null);
    }

    public SqlExecutor(@NotNull DbSchema schema)
    {
        this(schema.getScope());
    }

    @Override
    protected SqlExecutor getThis()
    {
        return this;
    }

    public int execute(CharSequence sql, Object... params)
    {
        if (sql instanceof SQLFragment)
            throw new IllegalArgumentException();
        return execute(new SQLFragment(sql, params));
    }

    public int execute(SQLFragment sql)
    {
        return execute(sql, NORMAL_EXECUTOR, null).intValue();
    }

    public Collection<String> getExecutionPlan(SQLFragment sql)
    {
        SqlDialect dialect = getScope().getSqlDialect();

        if (dialect.canShowExecutionPlan())
        {
            return dialect.getExecutionPlan(getScope(), sql);
        }
        else
        {
            throw new IllegalStateException("Can't obtain execution plan from scope \"" + getScope().getDisplayName() + "\" (" + dialect.getProductName() + ")");
        }
    }

    /**
     *  Convenience method that logs the plan to the passed in Logger (if non-null)
     */
    public void logExecutionPlan(@Nullable Logger logger, SQLFragment sql)
    {
        if (null != logger)
            logger.info(String.join("\n", getExecutionPlan(sql)));
    }

    public <T> T executeWithResults(SQLFragment sql, ResultSetHandler<T> handler)
    {
        ResultsHandlingStatementExecutor<T> resultsExecutor = new ResultsHandlingStatementExecutor<>();
        return execute(sql, resultsExecutor, handler);
    }

    // Provides the ability to execute a SQL statement that returns multiple result sets. The method prepares the statement,
    // passes it into the StatementHandler for execution and result set handling, and then closes the statement.
    public <T> T executeWithMultipleResults(SQLFragment sql, StatementHandler<T> handler)
    {
        StatementHandlingStatementExecutor<T> statementExecutor = new StatementHandlingStatementExecutor<>();
        return execute(sql, statementExecutor, handler);
    }

    public <T, C> T execute(SQLFragment sql, StatementExecutor<T, C> statementExecutor, @Nullable C context)
    {
        Connection conn = null;

        try
        {
            conn = getConnection();
            return statementExecutor.execute(conn, getScope().getSqlDialect(), sql, context);
        }
        catch(SQLException e)
        {
            Table.logException(sql, conn, e, getLogLevel());
            // StatementWrapper will have decorated the exception already, but not with the parameter substituted version
            ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.DialectSQL, sql.toDebugString(), true);
            throw getExceptionFramework().translate(getScope(), "SqlExecutor.execute()", e);
        }
        finally
        {
            close(null, conn);
        }
    }

    // StatementExecutor is a bit convoluted, but the implementations allow normal and results-returning executions
    // to share the same code path.
    private interface StatementExecutor<T, C>
    {
        T execute(Connection conn, SqlDialect dialect, SQLFragment sqlFragment, @Nullable C context) throws SQLException;
    }

    private static class NormalStatementExecutor implements StatementExecutor<Integer, Object>
    {
        @Override
        public Integer execute(Connection conn, SqlDialect dialect, SQLFragment sqlFragment, Object ignored) throws SQLException
        {
            List<Object> parameters = sqlFragment.getParams();
            String sql = sqlFragment.getSQL();

            if (parameters.isEmpty())
            {
                try (Statement stmt = conn.createStatement())
                {
                    if (stmt.execute(sql))
                        return -1;
                    else
                        return stmt.getUpdateCount();
                }
            }
            else
            {
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
                {
                    Table.setParameters(stmt, parameters, jdbcParameters);
                    if (stmt.execute())
                        return -1;
                    else
                        return stmt.getUpdateCount();
                }
            }
        }
    }

    private static class ResultsHandlingStatementExecutor<T> implements StatementExecutor<T, ResultSetHandler<T>>
    {
        @Override
        public T execute(Connection conn, SqlDialect dialect, SQLFragment sqlFragment, ResultSetHandler<T> handler) throws SQLException
        {
            List<Object> parameters = sqlFragment.getParams();
            String sql = sqlFragment.getSQL();

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
            {
                Table.setParameters(stmt, parameters, jdbcParameters);

                try (ResultSet rs = dialect.executeWithResults(stmt))
                {
                    return handler.handle(rs, conn);
                }
            }
        }
    }

    private static class StatementHandlingStatementExecutor<T> implements StatementExecutor<T, BaseSelector.StatementHandler<T>>
    {
        @Override
        public T execute(Connection conn, SqlDialect dialect, SQLFragment sqlFragment, BaseSelector.StatementHandler<T> handler) throws SQLException
        {
            List<Object> parameters = sqlFragment.getParams();
            String sql = sqlFragment.getSQL();

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
            {
                Table.setParameters(stmt, parameters, jdbcParameters);

                return handler.handle(stmt, conn);
            }
        }
    }
}
