/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.data.dialect.SqlDialect;

/**
 * A simple wrapper over a database schema that can be used for integration or other testing purposes.
 * User: arauch
 * Date: Sep 24, 2005
 * Time: 10:46:35 PM
 */
public class TestSchema
{
    private static final TestSchema instance = new TestSchema();

    public static TestSchema getInstance()
    {
        return instance;
    }

    private static final String SCHEMA_NAME = "test";

    private TestSchema()
    {
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

    public TableInfo getTableInfoTestTable()
    {
        return getSchema().getTable("TestTable");
    }
}
