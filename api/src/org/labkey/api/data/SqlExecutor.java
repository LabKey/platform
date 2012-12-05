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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 10/25/11
 * Time: 11:27 PM
 */
public class SqlExecutor extends JdbcCommand
{
    private final SQLFragment _sql;

    // Execute SQL. When conn is null (vast majority of cases), a pooled connection will be obtained from the scope and
    // closed after execution. If conn is provided then that connection will be used and will NOT be closed afterwards.
    public SqlExecutor(@NotNull DbScope scope, @NotNull SQLFragment sql, @Nullable Connection conn)
    {
        super(scope, conn);
        _sql = sql;
    }

    // Execute SQLFragment against a scope.
    public SqlExecutor(@NotNull DbScope scope, SQLFragment sql)
    {
        this(scope, sql, null);
    }

    // Execute SQL against a scope
    public SqlExecutor(@NotNull DbScope scope, String sql, Object... params)
    {
        this(scope, new SQLFragment(sql, params));
    }

    // Execute SQL against a schema
    public SqlExecutor(@NotNull DbSchema schema, SQLFragment sql)
    {
        this(schema.getScope(), sql);
    }

    public SqlExecutor(@NotNull DbSchema schema, String sql, Object... params)
    {
        this(schema.getScope(), new SQLFragment(sql, params));
    }

    public SQLFragment getSql()
    {
        return _sql;
    }

    public int execute()
    {
        Connection conn = null;

        try
        {
            conn = getConnection();
            return Table.execute(conn, _sql.getSQL(), _sql.getParamsArray());
        }
        catch(SQLException e)
        {
            Table.logException(_sql.getSQL(), _sql.getParamsArray(), conn, e, getLogLevel());
            throw getExceptionFramework().translate(getScope(), "Message", _sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            close(null, conn);
        }
    }
}
