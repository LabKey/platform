/*
 * Copyright (c) 2013-2015 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * User: daxh
 * Date: 4/17/13
 */
public class TestTask extends TransformTask
{
    // used to test step-level properties are persisted
    public static final int recordsInserted = 3;
    public static final int recordsModified = 2;
    public static final int recordsDeleted = 1;
    // used to test to job-level propeties are persisted
    public static final int recordsInsertedJob = 777;
    public static final long duration = 42; // in milliseconds

    //
    // Tests can set this variable on the job variable map.  This task
    // will throw an exception after running this task "FailAfter" times.
    // Setting the variable to -1 will never fail
    //
    public static final String FailStep = "FailStep";
    public static final String InjectedException = "Injected exception!";
    public static final String Transient = "TransientProperty";

    public TestTask(TransformTaskFactory factory, PipelineJob job, SimpleQueryTransformStepMeta meta)
    {
        super(factory, job, meta);
    }

    // test action that adds 3 inserted, 2 modified, and 1 deleted row(s)
    public void doWork(RecordedAction action)  throws PipelineJobException
    {
        VariableMap map = getVariableMap();
        String s = (String) map.get(FailStep);

        if (null != s && StringUtils.equalsIgnoreCase(s, _factory.getId().getName()))
        {
            //throw new PipelineJobException("test injected failure!");
            getJob().setActiveTaskStatus(PipelineJob.TaskStatus.error);
            return;
        }

        action.addProperty(TransformProperty.RecordsInserted.getPropertyDescriptor(), recordsInserted);
        getJob().getLogger().info("Test task fake inserted " + String.valueOf(recordsInserted) + " rows");
        action.addProperty(TransformProperty.RecordsModified.getPropertyDescriptor(), recordsModified);
        getJob().getLogger().info("Test task fake modified " + String.valueOf(recordsModified) + " rows");
        action.addProperty(TransformProperty.RecordsDeleted.getPropertyDescriptor(), recordsDeleted);
        getJob().getLogger().info("Test task fake deleted " + String.valueOf(recordsDeleted) + " rows");
        try
        {
            Thread.sleep(duration);
            // input is source table
            // output is dest table
            // todo: this is a fake URI, figure out the real story for the Data Input/Ouput for a transform step
            action.addInput(new URI(_meta.getSourceSchema() + "." + _meta.getSourceQuery()), TransformTask.INPUT_ROLE);
            action.addOutput(new URI(((SimpleQueryTransformStepMeta)_meta).getFullTargetString()), TransformTask.OUTPUT_ROLE, false);
        }
        catch (URISyntaxException ignore){}
        catch (InterruptedException ignore) {}
    }
}
