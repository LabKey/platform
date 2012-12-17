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
import java.sql.PreparedStatement;
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
        Connection conn = null;

        try
        {
            conn = getConnection();
            return execute(conn, sql);
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

    private int execute(Connection conn, SQLFragment sqlFragment) throws SQLException
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
            if (null != stmt)
                stmt.close();

            Table.closeParameters(parameters);
        }
    }
}
