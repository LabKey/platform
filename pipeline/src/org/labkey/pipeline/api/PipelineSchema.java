/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 */
public class PipelineSchema
{
    private static final PipelineSchema _instance = new PipelineSchema();

    public static PipelineSchema getInstance()
    {
        return _instance;
    }

    private static final String SCHEMA_NAME = "pipeline";

    private PipelineSchema()
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

    public TableInfo getTableInfoPipelineRoots()
    {
        return getSchema().getTable("PipelineRoots");
    }

    public TableInfo getTableInfoStatusFiles()
    {
        return getSchema().getTable("StatusFiles");
    }

    public TableInfo getTableInfoTriggerConfigurations()
    {
        return getSchema().getTable("TriggerConfigurations");
    }
}
