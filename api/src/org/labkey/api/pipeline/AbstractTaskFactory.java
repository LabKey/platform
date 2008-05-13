/*
 * Copyright (c) 2008 LabKey Corporation
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

/**
 * <code>AbstractTaskFactory</code>
 *
 * @author brendanx
 */
abstract public class AbstractTaskFactory implements TaskFactory, Cloneable
{
    private TaskId _id;
    private ExecutionLocation _executionLocation;

    public AbstractTaskFactory(Class namespaceClass)
    {
        this(namespaceClass, null);
    }

    public AbstractTaskFactory(Class namespaceClass, String name)
    {
        this(new TaskId(namespaceClass, name));
    }

    public AbstractTaskFactory(TaskId id)
    {
        _id = id;
    }

    public AbstractTaskFactory(TaskFactorySettings settings)
    {
        this(settings.getId());

        if (settings.getLocation() != null)
            _executionLocation = ExecutionLocation.valueOf(settings.getLocation());
    }

    public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
    {
        AbstractTaskFactory factory = (AbstractTaskFactory) clone();

        return factory.configure(settings);
    }

    private TaskFactory configure(TaskFactorySettings settings)
    {
        _id = settings.getId();
        if (settings.getLocation() != null)
            _executionLocation = ExecutionLocation.valueOf(settings.getLocation());
        return this;
    }

    public TaskId getId()
    {
        return _id;
    }

    public ExecutionLocation getExecutionLocation()
    {
        if (_executionLocation == null)
            return PipelineJobService.get().getDefaultExecutionLocation();
        return _executionLocation;
    }

    /**
     * Exists to allow direct Spring configuration of a simple factory
     * that does not use the full <code>AbstractTaskFactorySettings</code> method
     * of configuration.
     *
     * @return the execution location as a string
     */
    public String getLocation()
    {
        return (_executionLocation == null ? null : _executionLocation.toString());
    }

    /**
     * Exists to allow direct Spring configuration of a simple factory
     * that does not use the full <code>AbstractTaskFactorySettings</code> method
     * of configuration.
     *
     * @param location string execution location value
     */
    public void setLocation(String location)
    {
        _executionLocation = ExecutionLocation.valueOf(location);
    }
}
