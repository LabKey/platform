/*
 * Copyright (c) 2011 LabKey Corporation
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

import java.sql.Connection;
import java.sql.SQLException;

public class SqlSelector extends BaseSelector
{
    private final DbScope _scope;
    private final SQLFragment _sql;

    // Execute select SQL against a schema
    public SqlSelector(DbSchema schema, SQLFragment sql)
    {
        this(schema.getScope(), sql);
    }

    // Execute select SQL against a scope
    public SqlSelector(DbScope scope, SQLFragment sql)
    {
        _scope = scope;
        _sql = sql;
    }

    // Execute select SQL against a scope
    public SqlSelector(DbScope scope, String sql)
    {
        _scope = scope;
        _sql = new SQLFragment(sql);
    }

    @Override
    public Connection getConnection() throws SQLException
    {
        return _scope.getConnection();
    }

    @Override
    public DbScope getScope()
    {
        return _scope;
    }

    @Override
    public SQLFragment getSql()
    {
        return _sql;
    }
}
