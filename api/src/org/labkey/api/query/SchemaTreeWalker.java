/*
 * Copyright (c) 2012 LabKey Corporation
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

/**
 * User: kevink
 * Date: 10/9/12
 *
 * WARNING: Walks the entire schema and query tree (which may be very expensive.)
 */
public class SchemaTreeWalker<R, P> extends SimpleSchemaTreeVisitor<R, P>
{
    @Override
    public R visitDefaultSchema(DefaultSchema schema, Path path, P param)
    {
        R r = null;
        r = visitAndReduce(schema.getSchemas(), path, param, r);
        return r;
    }

    @Override
    public R visitUserSchema(UserSchema schema, Path path, P param)
    {
        R r = null;
        r = visitAndReduce(schema.getSchemas(), path, param, r);
        r = visitAndReduce(schema.getTables(), path, param, r);
        return r;
    }

    @Override
    public R visitTable(TableInfo table, Path path, P param)
    {
        return null;
    }
}
