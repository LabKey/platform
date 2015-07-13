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

import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 7/21/2014
 */
public class TaskRefTransformStep extends TransformTask
{
    protected final TaskRefTransformStepMeta _meta;

    public TaskRefTransformStep(TransformTaskFactory factory, PipelineJob job, TaskRefTransformStepMeta meta, TransformJobContext context)
    {
        super(factory, job, meta, context);
        _meta = meta;
    }

    @Override
    public void doWork(RecordedAction action) throws PipelineJobException
    {
        try
        {
            getJob().debug("TaskRefTransformStep.doWork called");
            TaskRefTask taskInstance = _meta.getTaskInstance();
            String className = taskInstance.getClass().getName();
            taskInstance.setContainerUser(_context);
            getJob().info("Running taskref task " + className);
            Map<String, String> output = digestRecordedActionSet(taskInstance.run(getJob()));
            output.put("class", className);

            // Persist output and classname into dataintegration.TransformConfiguration.TransformState
            getVariableMap().put(TransformProperty.Parameters, output);

            // TODO: Read rows inserted, deleted, updated, from output map, and enable recordWork
            // recordWork(action);
        }
        catch (PipelineJobException e)
        {
            throw e;
        }
        catch (Exception x)
        {
            throw new PipelineJobException(x);
        }
    }

    private Map<String, String> digestRecordedActionSet(RecordedActionSet ras)
    {
        Map<String, String> result = new LinkedHashMap<>();
        // In case there's more than one Ra in the set, label the parameters accordingly
        boolean prefixWithRaNames = ras.getActions().size() > 1;

        for (RecordedAction ra : ras.getActions())
        {
            String raPrefix = prefixWithRaNames ? ra.getName() + ":" : "";
            for (Map.Entry<RecordedAction.ParameterType, Object> param : ra.getParams().entrySet())
            {
                result.put(raPrefix + param.getKey().getName(), param.getValue().toString());
            }
        }

        return result;
    }
}
