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
package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;

/**
 * <code>TaskPipelineSpec</code> is used for Spring configuration of a
 * <code>TaskPipeline</code> in the <code>TaskRegistry</code>.  Extend this
 * class, and override <code>TaskPipeline.cloneAndConfigure()</code> to create
 * specific types of <code>TaskPipeline</code> objects that can be configured
 * with Spring beans.
 *
 * @author brendanx
 */
public class TaskPipelineSettings
{
    private TaskId _id;
    private Object[] _taskProgressionSpec = new Object[0];

    /**
     * ObjectId to use in the LSID for the generated Experiment protocol
     */
    private String _protocolIdentifier;

    /**
     * Name to show in the UI for the generated Experiment protocol
     */
    private String _protocolShortDescription;

    /** Module in which the task pipeline is declared. */
    private Module _declaringModule;


    public TaskPipelineSettings(TaskId id)
    {
        _id = id;
    }

    /**
     * Convenience constructor for Spring XML configuration.
     *
     * @param namespaceClass namespace class for TaskId
     */
    public TaskPipelineSettings(Class namespaceClass)
    {
        this(namespaceClass, null);
    }

    /**
     * Convenience constructor for Spring XML configuration.
     *
     * @param namespaceClass namespace class for TaskId
     * @param name name for TaskId
     */
    public TaskPipelineSettings(Class namespaceClass, String name)
    {
        this(new TaskId(namespaceClass, name));
    }

    public TaskId getId()
    {
        return _id;
    }

    public TaskId getCloneId()
    {
        return new TaskId(TaskPipeline.class);
    }

    public Object[] getTaskProgressionSpec()
    {
        return _taskProgressionSpec;
    }

    public void setTaskProgressionSpec(Object[] taskProgressionSpec)
    {
        _taskProgressionSpec = taskProgressionSpec;
    }

    /**
     * @param protocolIdentifier ObjectId to use in the LSID for the generated Experiment protocol
     */
    public void setProtocolObjectId(String protocolIdentifier)
    {
        _protocolIdentifier = protocolIdentifier;
    }

    /**
     * @return ObjectId to use in the LSID for the generated Experiment protocol
     */
    public String getProtocolObjectId()
    {
        return _protocolIdentifier;
    }

    /**
     * @return Name to show in the UI for the generated Experiment protocol
     */
    public String getProtocolName()
    {
        return _protocolShortDescription;
    }

    /**
     * @param protocolShortDescription Name to show in the UI for the generated Experiment protocol
     */
    public void setProtocolName(String protocolShortDescription)
    {
        _protocolShortDescription = protocolShortDescription;
    }

    public void setDeclaringModule(@NotNull Module declaringModule)
    {
        if (declaringModule == null)
            throw new IllegalArgumentException("Declaring module must not be null");

        if (_declaringModule != null)
            throw new IllegalStateException("Declaring module already set");

        _declaringModule = declaringModule;
    }

    public Module getDeclaringModule()
    {
        return _declaringModule;
    }
}
