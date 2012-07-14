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

    // Execute select SQL against a scope
    public SqlExecutor(DbScope scope, SQLFragment sql)
    {
        super(scope);
        _sql = sql;
    }

    // Execute select SQL against a scope
    public SqlExecutor(DbScope scope, String sql)
    {
        this(scope, new SQLFragment(sql));
    }

    // Execute select SQL against a schema
    public SqlExecutor(DbSchema schema, SQLFragment sql)
    {
        this(schema.getScope(), sql);
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
            Table.doCatch( _sql.getSQL(), _sql.getParamsArray(), conn, e);
            throw getExceptionFramework().translate(getScope(), "Message", _sql.getSQL(), e);  // TODO: Change message
        }
        finally
        {
            doFinally(conn);
        }
    }

    protected void doFinally(@Nullable Connection conn)
    {
        Table.doFinally(null, null, conn, getScope());
    }
}
