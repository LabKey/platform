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

import java.io.IOException;
import java.sql.SQLException;

/**
 * <code>AbstractTaskFactory</code>
 *
 * @author brendanx
 */
abstract public class AbstractTaskFactory implements TaskFactory, Cloneable
{
    private TaskId _id;
    private TaskId _dependencyId;
    private boolean _join;
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

    public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
    {
        AbstractTaskFactory factory = (AbstractTaskFactory) clone();

        return factory.configure((AbstractTaskFactorySettings) settings);
    }

    private TaskFactory configure(AbstractTaskFactorySettings settings)
    {
        _id = settings.getId();
        if (settings.getDependencyId() != null)
            _dependencyId = settings.getDependencyId();
        if (settings.isJoinSet())
            _join = settings.isJoin();
        if (settings.getLocation() != null)
            _executionLocation = ExecutionLocation.valueOf(settings.getLocation());
        return this;
    }

    /**
     * By default tasks participate, but may be made conditional on other tasks.
     * Override to remove a task from processing under certain conditions.
     *  
     * @param job the <code>PipelineJob</code> about which task is being interrogated
     * @return true if task is part of processing this job
     * @throws IOException
     * @throws SQLException
     */
    public boolean isParticipant(PipelineJob job) throws IOException, SQLException
    {
        if (_dependencyId != null)
        {
            TaskFactory factory = PipelineJobService.get().getTaskFactory(_dependencyId);
            return factory.isParticipant(job);
        }
        
        return true;
    }

    public TaskId getId()
    {
        return _id;
    }

    public TaskId getDependencyId()
    {
        return _dependencyId;
    }

    public void setDependencyId(TaskId dependencyId)
    {
        _dependencyId = dependencyId;
    }

    public boolean isJoin()
    {
        return _join;
    }

    public void setJoin(boolean join)
    {
        _join = join;
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
