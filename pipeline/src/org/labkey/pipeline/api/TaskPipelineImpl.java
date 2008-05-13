/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;

/**
 * <code>TaskPipelineImpl</code> implements the <code>TaskPipeline</code>
 * interface for use in the <code>TaskRegistry</code>.
 */
public class TaskPipelineImpl implements TaskPipeline, Cloneable
{
    /**
     * Used to identify the pipeline in configuration.
     */
    private TaskId _id;

    /**
     * Tasks to execute in the pipeline.
     */
    private TaskId[] _taskProgression;

    public TaskPipelineImpl()
    {
        this(new TaskId(TaskPipeline.class));
    }
    
    public TaskPipelineImpl(TaskId id)
    {
        _id = id;
    }

    public TaskPipeline cloneAndConfigure(TaskPipelineSettings settings,
                                          TaskId[] taskProgression)
            throws CloneNotSupportedException
    {
        TaskPipelineImpl pipeline = (TaskPipelineImpl) clone();
        
        return pipeline.configure(settings, taskProgression);
    }

    private TaskPipeline configure(TaskPipelineSettings settings, TaskId[] taskProgression)
    {
        _id = settings.getId();
        if (settings.getTaskProgressionSpec() != null)
            _taskProgression = taskProgression;
        return this;
    }

    public String getName()
    {
        return _id.toString();
    }

    public TaskId getId()
    {
        return _id;
    }

    public TaskId[] getTaskProgression()
    {
        return _taskProgression;
    }
}