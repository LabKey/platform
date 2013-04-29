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
import java.util.Date;

/**
 * User: daxh
 * Date: 4/17/13
 */
abstract public class TransformTask extends PipelineJob.Task<TransformTaskFactory>
{
    final private TransformJob _txJob;
    final private RecordedActionSet _records = new RecordedActionSet();
    final private VariableMap _variableMap;

    public static final String ACTION_NAME = "Query Transform";
    public static final String INPUT_ROLE = "Row Source";
    public static final String OUTPUT_ROLE = "Row Destination";

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
        RecordedAction action = new RecordedAction(TransformTask.ACTION_NAME);
        action.setStartTime(new Date());
        doWork(action);
        action.setEndTime(new Date());
        action.setRecordCount(_txJob.getRecordCountForAction(action));
        _records.add(action);
        return _records;
    }

    abstract public void doWork(RecordedAction action) throws PipelineJobException;
}
