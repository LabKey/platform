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
import org.labkey.api.pipeline.RecordedActionSet;

/**
 * User: daxh
 * Date: 4/17/13
 */
abstract public class TransformTask extends PipelineJob.Task<TransformTaskFactory>
{
    protected RecordedActionSet _records = new RecordedActionSet();

    public TransformTask(TransformTaskFactory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        TransformJob job = (TransformJob) getJob();
        // undone:  for multi-step transforms we'll need to have a better way to record
        // logging job start and stop
        job.logRunStart();

        //
        // do transform work here!
        //
        doWork();

        job.logRunFinish("Complete", 0);

        return _records;
    }

    abstract public void doWork() throws PipelineJobException;
}
