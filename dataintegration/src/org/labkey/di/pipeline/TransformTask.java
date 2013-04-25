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
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;

/**
 * User: daxh
 * Date: 4/17/13
 */
abstract public class TransformTask extends PipelineJob.Task<TransformTaskFactory>
{
    final private TransformJob _txJob;
    final private RecordedActionSet _records = new RecordedActionSet();
    final private VariableMap _variableMap;


    public TransformTask(TransformTaskFactory factory, PipelineJob job)
    {
        super(factory, job);

        _txJob = (TransformJob)job;
        _variableMap = new VariableMapImpl(_txJob.getVariableMap());
    }


    protected TransformJob getTransformJob()
    {
        return _txJob;
    }


    public VariableMap getVariableMap()
    {
        return _variableMap;
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


    protected void addRecordedAction(RecordedAction action)
    {
        _records.add(action);
    }


    abstract public void doWork() throws PipelineJobException;
}
