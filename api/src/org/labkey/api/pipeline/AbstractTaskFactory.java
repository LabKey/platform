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
import java.util.List;
import java.util.Collections;

/**
 * <code>AbstractTaskFactory</code>
 *
 * @author brendanx
 */
abstract public class AbstractTaskFactory extends ClusterSettingsImpl implements TaskFactory, Cloneable
{
    private TaskId _id;
    private TaskId _dependencyId;
    private boolean _join;
    private String _executionLocation;
    private int _autoRetry = -1;

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

    public List<String> getActionNames()
    {
        return Collections.singletonList(getId().toString());
    }

    private TaskFactory configure(AbstractTaskFactorySettings settings)
    {
        _id = settings.getId();
        if (settings.getDependencyId() != null)
            _dependencyId = settings.getDependencyId();
        if (settings.isJoinSet())
            _join = settings.isJoin();
        if (settings.getLocation() != null)
            _executionLocation = settings.getLocation();
        if (settings.isAutoRetrySet())
            _autoRetry = settings.getAutoRetry();
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

    public boolean isAutoRetryEnabled(PipelineJob job) throws IOException, SQLException
    {
        // TODO: Check log file for wallclock expiration on cluster jobs
        return true;
    }

    /**
     * Returns the id for this task factory, under which it is stored in the
     * task registry.
     *
     * @return the id for this task factory
     */
    public TaskId getId()
    {
        return _id;
    }

    /**
     * Returns the id of the task with the <code>run()</code> method that should
     * be called for this task factory, allowing a task factory to delegate work
     * based on the state of the <code>PipelineJob</code> (e.g. file conversion
     * which runs a different conversion command depending on the input file).
     *
     * @param job the job on which the task is to run
     * @return the id for the task to run
     */
    public TaskId getActiveId(PipelineJob job)
    {
        return getId();
    }

    /**
     * Returns the id of a task used in <code>isParticipant()</code> to determine
     * whether this task should be run.
     *
     * @return the id of a task on which the participation of this task depends
     */
    public TaskId getDependencyId()
    {
        return _dependencyId;
    }

    /**
     * Sets the id of a task used in <code>isParticipant()</code> to determine
     * whether this task should be run.
     *
     * @param dependencyId the id of a task on which the participation of this task depends
     */
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

    public String getExecutionLocation()
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
        return _executionLocation;
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
        _executionLocation = location;
    }

    /**
     * Returns the number of times to automatically retry this task.
     *
     * @return number of times to automatically retry this taks.
     */
    public int getAutoRetry()
    {
        if (_autoRetry == -1)
            return PipelineJobService.get().getDefaultAutoRetry();
        return _autoRetry;
    }

    /**
     * Sets the number of times to automatically retry this task.
     *
     * @param autoRetry the number of times to automatically retry this task
     */
    public void setAutoRetry(int autoRetry)
    {
        _autoRetry = autoRetry;
    }
}
