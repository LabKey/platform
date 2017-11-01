/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.di.DataIntegrationQuerySchema;
import org.labkey.di.DataIntegrationModule;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class ETLPipelineProvider extends PipelineProvider
{
    public static final String NAME = "ETL";

    public ETLPipelineProvider(DataIntegrationModule module)
    {
        super(NAME, module);
    }

    @Override
    public void preDeleteStatusFile(User user, PipelineStatusFile sf)
    {
        // Delete the our own records that point to the pipeline job record
        SQLFragment sql = new SQLFragment("DELETE FROM dataintegration.transformrun WHERE JobId = ?", sf.getRowId());
        new SqlExecutor(DataIntegrationQuerySchema.getSchema()).execute(sql);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {

    }
}
