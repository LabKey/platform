package org.labkey.pipeline.api;

import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.resource.Resource;

/**
 * User: kevink
 * Date: 11/8/13
 *
 * Creates TaskFactory from a task.xml config file.
 */
public class TaskPipelineResource
{
    public static TaskPipeline create(TaskId taskId, Resource pipelineConfig)
    {
        return new TaskPipelineImpl(taskId);
    }
}
