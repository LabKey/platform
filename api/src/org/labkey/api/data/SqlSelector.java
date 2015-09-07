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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.util.List;

public class SqlSelector extends SqlExecutingSelector<SqlSelector.SimpleSqlFactory, SqlSelector>
{
    private final SQLFragment _sql;

    // Execute select SQL against a scope, with the ability to provide a connection
    public SqlSelector(DbScope scope, @Nullable Connection conn, SQLFragment sql, @NotNull QueryLogging queryLogging)
    {
        super(scope, conn, queryLogging);
        _sql = sql;
    }

    public SqlSelector(DbScope scope, @Nullable Connection conn, SQLFragment sql)
    {
        this(scope, conn, sql, new QueryLogging());
    }

    // Execute select SQL against a scope and a specific Connection
    public SqlSelector(DbScope scope, @Nullable Connection conn, CharSequence sql)
    {
        this(scope, conn, new SQLFragment(sql));
    }

    public SqlSelector(DbScope scope, SQLFragment sql, @NotNull QueryLogging queryLogging)
    {
        this(scope, null, sql, queryLogging);
    }

    // Execute select SQL against a scope
    public SqlSelector(DbScope scope, SQLFragment sql)
    {
        this(scope, null, sql);
    }

    // Execute select SQL against a scope
    public SqlSelector(DbScope scope, CharSequence sql)
    {
        this(scope, null, sql);
    }

    // Execute select SQL against a schema
    public SqlSelector(DbSchema schema, SQLFragment sql)
    {
        this(schema.getScope(), sql);
        if (null == sql)
            throw new NullPointerException();
    }

    // Execute select SQL against a schema; simple query with no parameters
    public SqlSelector(DbSchema schema, CharSequence sql)
    {
        this(schema.getScope(), sql);
    }

    // Execute select SQL against a schema
    public SqlSelector(DbSchema schema, CharSequence sql, Object... params)
    {
        this(schema.getScope(), new SQLFragment(sql, params));
    }

    // Execute select SQL against a schema
    public SqlSelector(DbSchema schema, CharSequence sql, List<Object> params)
    {
        this(schema.getScope(), new SQLFragment(sql, params));
    }

    @Override
    protected SqlSelector getThis()
    {
        return this;
    }

    @Override
    protected SimpleSqlFactory getSqlFactory(boolean isResultSet)
    {
        // No special handling for ResultSet... until SqlSelector supports limit/offset
        return new SimpleSqlFactory();
    }

    public class SimpleSqlFactory extends BaseSqlFactory
    {
        @Override
        public SQLFragment getSql()
        {
            return _sql;   // TODO: Handle limit and offset
        }
    }
}
