/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.GUID;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class SqlBuilder extends Builder
{
    private final DbSchema _schema;
    private final @NotNull SqlDialect _dialect;


    public SqlBuilder(DbSchema schema)
    {
        _schema = schema;
        _dialect = schema.getSqlDialect();
    }

    public SqlBuilder(@NotNull SqlDialect dialect)
    {
        Objects.requireNonNull(dialect);
        _schema = null;
        _dialect = dialect;
    }

    public boolean appendComment(String comment)
    {
        return super.appendComment(comment, getDialect());
    }

    /**
     * Append a '?' to the generated SQL, and add the object to the list of the params.
     */
    public void appendParam(Object value)
    {
        append(" ? ");
        add(value);
    }

    @Override
    public SqlBuilder addAll(Collection<?> params)
    {
        super.addAll(Arrays.asList(params.toArray()));
        return this;
    }

    public @NotNull SqlDialect getDialect()
    {
        return _dialect;
    }

    public DbSchema getDbSchema()
    {
        return _schema;
    }

    public boolean allowUnsafeCode()
    {
        return false;
    }

    @Override
    public SQLFragment appendValue(CharSequence s)
    {
        return super.appendValue(s, _dialect);
    }

//    @Override
    @Override
    public SQLFragment appendStringLiteral(CharSequence s)
    {
        return super.appendStringLiteral(s, _dialect);
    }

    @Override
    public SQLFragment appendStringLiteral(CharSequence s, @NotNull SqlDialect d)
    {
        assert null==d || _dialect==d;
        return super.appendStringLiteral(s,  _dialect);
    }

    @Override
    public SQLFragment appendValue(CharSequence s, SqlDialect d)
    {
        assert null==d || _dialect==d;
        return super.appendValue(s, _dialect);
    }

    @Override
    public SQLFragment appendValue(GUID g)
    {
        return super.appendValue(g, _dialect);
    }

    @Override
    public SQLFragment appendValue(GUID g, SqlDialect d)
    {
        assert null==d || _dialect==d;
        return super.appendValue(g, _dialect);
    }

    @Override
    public SQLFragment appendValue(@NotNull Container c)
    {
        return super.appendValue(c, _dialect);
    }

    @Override
    public SQLFragment appendValue(@NotNull Container c, SqlDialect d)
    {
        assert null==d || _dialect==d;
        return super.appendValue(c, _dialect);
    }

    public SQLFragment appendValue(Boolean B)
    {
        return super.appendValue(B, _dialect);
    }

    @Override
    public SQLFragment appendValue(Boolean B, @NotNull SqlDialect d)
    {
        assert null==d || _dialect==d;
        return super.appendValue(B, _dialect);
    }
}
