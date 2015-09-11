/*
 * Copyright (c) 2015 LabKey Corporation
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

/**
 * Created by adam on 9/8/2015.
 */
public class StandardSchemaTableInfoFactory implements SchemaTableInfoFactory
{
    private final String _tableName;
    private final DatabaseTableType _tableType;
    private final @Nullable String _description;

    public StandardSchemaTableInfoFactory(String tableName, DatabaseTableType tableType, @Nullable String description)
    {
        _tableName = tableName;
        _tableType = tableType;
        _description = description;
    }

    @Override
    public String getTableName()
    {
        return _tableName;
    }

    @Override
    public SchemaTableInfo getSchemaTableInfo(DbSchema schema)
    {
        SchemaTableInfo ti = new SchemaTableInfo(schema, _tableType, _tableName);
        ti.setDescription(_description);

        return ti;
    }
}
