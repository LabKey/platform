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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseSelector.ResultSetHandler;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.ResultSetUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * User: adam
 * Date: 10/25/11
 * Time: 11:27 PM
 */
public class SqlExecutor extends JdbcCommand
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

    public int execute(CharSequence sql, Object... params)
    {
        return execute(new SQLFragment(sql, params));
    }

    public int execute(SQLFragment sql)
    {
        return execute(sql, NORMAL_EXECUTOR, null);
    }

    public <T> T executeWithResults(SQLFragment sql, ResultSetHandler<T> handler)
    {
        ResultsHandlingStatementExecutor<T> resultsExecutor = new ResultsHandlingStatementExecutor<T>();
        return execute(sql, resultsExecutor, handler);
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
            throw getExceptionFramework().translate(getScope(), "Message", sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            close(null, conn);
        }
    }

    // StatementExecutor is a bit convoluted, but these classes allow normal and results-returning executions
    // to share the same code path.
    private static interface StatementExecutor<T, C>
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
            Statement stmt = null;

            try
            {
                if (parameters.isEmpty())
                {
                    stmt = conn.createStatement();
                    if (stmt.execute(sql))
                        return -1;
                    else
                        return stmt.getUpdateCount();
                }
                else
                {
                    stmt = conn.prepareStatement(sql);
                    Table.setParameters((PreparedStatement) stmt, parameters);
                    if (((PreparedStatement)stmt).execute())
                        return -1;
                    else
                        return stmt.getUpdateCount();
                }
            }
            finally
            {
                ResultSetUtil.close(stmt);
                Table.closeParameters(parameters);
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
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try
            {
                stmt = conn.prepareStatement(sql);
                Table.setParameters(stmt, parameters);

                rs = dialect.executeWithResults(stmt);
                return handler.handle(rs, conn);
            }
            finally
            {
                ResultSetUtil.close(rs);
                ResultSetUtil.close(stmt);
                Table.closeParameters(parameters);
            }
        }
    }
}
