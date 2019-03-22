/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.data.TableInfo;

import java.util.List;

/**
 * User: kevink
 * Date: 10/9/12
 *
 * WARNING: Walks the entire schema and query tree (which may be very expensive.)
 */
public class SchemaTreeWalker<R, P> extends SimpleSchemaTreeVisitor<R, P>
{
    protected SchemaTreeWalker(boolean includeHidden)
    {
        super(includeHidden);
    }

    protected SchemaTreeWalker(boolean includeHidden, R defaultValue)
    {
        super(includeHidden, defaultValue);
    }

    @Override
    public R visitDefaultSchema(DefaultSchema schema, Path path, P param)
    {
        R r = null;
        r = visitAndReduce(schema.getSchemas(_includeHidden), path, param, r);
        return r;
    }

    @Override
    public R visitUserSchema(UserSchema schema, Path path, P param)
    {
        R r = null;
        r = visitAndReduce(schema.getSchemas(_includeHidden), path, param, r);

        List<String> names = schema.getTableAndQueryNames(false);
        r = visitTablesAndReduce(schema, names, path, param, r);
        return r;
    }

    /**
     * Helper to visit tables and queries of a user schema.
     * If an error is thrown when creating a table/query, the <code>visitTableError</code>
     * will be called with the name of the table and Exception.
     */
    protected R visitTablesAndReduce(UserSchema schema, Iterable<String> names, Path path, P param, R r)
    {
        for (String name : names)
        {
            try
            {
                TableInfo t = schema.getTable(name);
                r = visitAndReduce(t, path, param, r);
            }
            catch (Exception e)
            {
                r = reduce(visitTableError(schema, name, e, path, param), r);
            }
        }
        return r;
    }

    @Override
    public R visitTable(TableInfo table, Path path, P param)
    {
        return defaultAction(table, path, param);
    }

    @Override
    public R visitTableError(UserSchema schema, String name, Exception e, Path path, P param)
    {
        return defaultErrorAction(schema, name, e, path, param);
    }
}
