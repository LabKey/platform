/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.DbSchema;

import java.util.*;

public class SqlBuilder extends Builder
{
    private DbSchema _schema;
    private SqlDialect _dialect;


    public SqlBuilder(DbSchema schema)
    {
        _schema = schema;
        _dialect = schema.getSqlDialect();
    }

    public SqlBuilder(SqlDialect dialect)
    {
        _dialect = dialect;
    }

    public boolean appendComment(String comment)
    {
        return super.appendComment(comment, getDialect());
    }

    /**
     * Append a '?' to the generated SQL, and add the object to the list of the params.
     * @param value
     */
    public void appendParam(Object value)
    {
        append(" ? ");
        add(value);
    }

    public void addAll(Collection<?> params)
    {
        super.addAll(Arrays.asList(params.toArray()));
    }

    public void appendLiteral(String value)
    {
        if (value.indexOf("\\") >= 0 || value.indexOf("\'") >= 0)
            throw new IllegalArgumentException("Illegal characters in '" + value + "'");
        append("'" + value + "'");
    }

    public SqlDialect getDialect()
    {
        return _dialect;
    }

    public DbSchema getDbSchema()
    {
        return _schema;
    }

    public void appendIdentifier(String str)
    {
        append("\"" + str + "\"");
    }

    public boolean allowUnsafeCode()
    {
        return false;
    }
}
