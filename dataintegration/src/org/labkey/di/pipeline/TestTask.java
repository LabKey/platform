/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.di.steps.SimpleQueryTransformStepMeta;

/**
 * User: daxh
 * Date: 4/17/13
 */
public class TestTask extends PipelineJob.Task<TestTaskFactory>
{
    final SimpleQueryTransformStepMeta _meta;
    final TransformJobContext _context;
    final public static String ACTION_NAME = "Test Step";

    public TestTask(TestTaskFactory factory, PipelineJob job, SimpleQueryTransformStepMeta meta, TransformJobContext context)
    {
        super(factory, job);
        _meta = meta;
        _context = context;
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        TransformJob job = (TransformJob) getJob();
        TransformJobSupport support = job.getJobSupport(TransformJobSupport.class);
        BaseQueryTransformDescriptor etl = support.getTransformDescriptor();
        TransformJobContext ctx = support.getTransformJobContext();
        RecordedAction action = new RecordedAction(ACTION_NAME);
        //
        // do transform work here!
        //
        job.getLogger().info("Test task is running and doing great work!");
        return new RecordedActionSet(action);
    }
}
