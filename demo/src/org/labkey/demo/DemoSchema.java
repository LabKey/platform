/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.demo;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 * User: brittp
 * Date: Jan 23, 2006
 * Time: 1:20:25 PM
 */
public class DemoSchema
{
    private static final DemoSchema _instance = new DemoSchema();
    private static final String SCHEMA_NAME = "demo";

    public static DemoSchema getInstance()
    {
        return _instance;
    }

    private DemoSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be 
        // accessed via cpas.demo.DemoSchema.getInstance()
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoPerson()
    {
        return getSchema().getTable("Person");
    }
}
