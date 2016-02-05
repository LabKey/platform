/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;

/**
 * <code>TaskPipelineImpl</code> implements the <code>TaskPipeline</code>
 * interface for use in the <code>TaskRegistry</code>.
 */
public class TaskPipelineImpl<SettingsType extends TaskPipelineSettings> implements TaskPipeline<SettingsType>, Cloneable
{
    /**
     * Used to identify the pipeline in configuration.
     */
    private TaskId _id;

    /** Module in which the task pipeline is declared. */
    private Module _declaringModule;

    /**
     * Tasks to execute in the pipeline.
     */
    private TaskId[] _taskProgression;
    
    /** ObjectId to use in the LSID for the generated Experiment protocol */
    private String _protocolIdentifier;
    /** Name to show in the UI for the generated Experiment protocol */
    private String _protocolShortDescription;

    public TaskPipelineImpl()
    {
        this(new TaskId(TaskPipeline.class));
    }
    
    public TaskPipelineImpl(TaskId id)
    {
        _id = id;
    }

    public TaskPipeline cloneAndConfigure(SettingsType settings,
                                          TaskId[] taskProgression)
            throws CloneNotSupportedException
    {
        TaskPipelineImpl<SettingsType> pipeline = (TaskPipelineImpl) clone();
        
        return pipeline.configure(settings, taskProgression);
    }

    public String getProtocolIdentifier()
    {
        if (_protocolIdentifier != null)
        {
            return _protocolIdentifier;
        }

        return getName();
    }

    public String getProtocolShortDescription()
    {
        if (_protocolShortDescription != null)
        {
            return _protocolShortDescription;
        }

        return getProtocolIdentifier();
    }

    @Override
    public String getDescription()
    {
        return getProtocolShortDescription();
    }

    private TaskPipeline configure(SettingsType settings, TaskId[] taskProgression)
    {
        _id = settings.getId();
        if (settings.getTaskProgressionSpec() != null)
            _taskProgression = taskProgression;

        if (settings.getDeclaringModule() != null)
            _declaringModule = settings.getDeclaringModule();

        if (settings.getProtocolObjectId() != null)
            _protocolIdentifier = settings.getProtocolObjectId();

        if (settings.getProtocolName() != null)
            _protocolShortDescription = settings.getProtocolName();

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

    public void setTaskProgression(TaskId... taskProgression)
    {
        _taskProgression = taskProgression;
    }

    public Module getDeclaringModule()
    {
        return _declaringModule;
    }

    public void setDeclaringModule(@NotNull Module declaringModule)
    {
        if (declaringModule == null)
            throw new IllegalArgumentException("Declaring module must not be null");

        if (_declaringModule != null)
            throw new IllegalStateException("Declaring module already set");

        _declaringModule = declaringModule;
    }
}
