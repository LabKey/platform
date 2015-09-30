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
package org.labkey.di.steps;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.di.pipeline.TaskRefTaskImpl;

import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 9/22/2015
 */

/**
 *  Clear the cached metadata for a given schema, forcing a refresh on next access to the schema/tables.
 *  Usage is inline to an ETL which performs dynamic table creation/changes. Adding this as a further step will ensure
 *  the app gets refreshed metadata.
 *
 *  Settings:
 *  schema: the schema name to reset
 */
public class ResetSchemaMetadata extends TaskRefTaskImpl
{
    private final String SCHEMA = "schema";

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.singletonList(SCHEMA);
    }

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        job.info("Resetting metadata for schema " + settings.get(SCHEMA));
        DbScope.getLabKeyScope().invalidateSchema(settings.get(SCHEMA), DbSchemaType.All);
        return new RecordedActionSet(makeRecordedAction());
    }
}
