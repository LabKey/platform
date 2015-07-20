/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.Collections;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 11/18/2014
 *
 * Allow certain types of regular pipeline tasks to be registered within an ETL context.
 * So far TransformPipelineJob has been modified to implement FileAnalysisJobSupport; this provides
 * basic support to run instances of CommandTask as steps in an ETL
 *
 */
public class ExternalPipelineTaskProvider extends StepProviderImpl
{
    @Override
    public String getName()
    {
        return "ExternalPipelineTask";
    }

    @Override
    public List<String> getLegacyNames()
    {
        return Collections.emptyList();
    }

    @Override
    public Class getStepClass()
    {
        return ExternalPipelineTaskStep.class;
    }

    @Override
    public StepMeta createMetaInstance()
    {
        return new ExternalPipelineTaskMeta();
    }

    @Override
    public TransformTask createStepInstance(TransformTaskFactory f, PipelineJob job, StepMeta meta, TransformJobContext context)
    {
        return new ExternalPipelineTaskStep(f, job, meta, context);
    }
}
