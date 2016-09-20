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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;

import java.io.IOException;

/**
 * <code>AbstractTaskFactory</code>
 *
 * @author brendanx
 */
abstract public class AbstractTaskFactory<SettingsType extends AbstractTaskFactorySettings, FactoryType extends AbstractTaskFactory<SettingsType, FactoryType>> implements TaskFactory<SettingsType>, Cloneable
{
    private TaskId _id;
    private TaskId _dependencyId;
    private boolean _join;
    private boolean _largeWork;
    private String _executionLocation;
    private String _groupParameterName;
    private int _autoRetry = -1;

    private Module _declaringModule;

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

    public FactoryType cloneAndConfigure(SettingsType settings) throws CloneNotSupportedException
    {
        FactoryType result = (FactoryType) clone();
        result.configure(settings);
        return result;
    }

    public String getGroupParameterName()
    {
        return _groupParameterName;
    }

    protected void configure(SettingsType settings)
    {
        _id = settings.getId();
        if (settings.getDependencyId() != null)
            _dependencyId = settings.getDependencyId();
        if (settings.isJoinSet())
            _join = settings.isJoin();
        if (settings.isLargeWorkSet())
            _largeWork = settings.isLargeWork();
        if (settings.getLocation() != null)
            _executionLocation = settings.getLocation();
        if (settings.isAutoRetrySet())
            _autoRetry = settings.getAutoRetry();
        if (settings.getGroupParameterName() != null)
            _groupParameterName = settings.getGroupParameterName();
        if (settings.getDeclaringModule() != null)
            _declaringModule = settings.getDeclaringModule();
    }

    /**
     * By default tasks participate, but may be made conditional on other tasks.
     * Override to remove a task from processing under certain conditions.
     *
     * @param job the <code>PipelineJob</code> about which task is being interrogated
     * @return true if task is part of processing this job
     */
    public boolean isParticipant(PipelineJob job) throws IOException
    {
        if (_dependencyId != null)
        {
            TaskFactory factory = PipelineJobService.get().getTaskFactory(_dependencyId);
            return factory.isParticipant(job);
        }

        return true;
    }

    @Override
    public void validateParameters(PipelineJob job) throws PipelineValidationException
    {
    }

    public boolean isAutoRetryEnabled(PipelineJob job)
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

    /**
     * Indicates that the inputs are too large to want to copy to the local file system if they're
     * coming from a remote file system. This prevents us from filling up the local disk.
     */
    public boolean isLargeWork()
    {
        return _largeWork;
    }

    public void setLargeWork(boolean largeWork)
    {
        _largeWork = largeWork;
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

    public WorkDirectory createWorkDirectory(String jobGUID, FileAnalysisJobSupport jobSupport, Logger logger) throws IOException
    {
        PipelineJobService service = PipelineJobService.get();
        WorkDirFactory factory = (_largeWork ? service.getLargeWorkDirFactory() :
                service.getWorkDirFactory());
        return factory.createWorkDirectory(jobGUID, jobSupport, false, logger);
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

    @Override
    public void setDeclaringModule(@NotNull Module declaringModule)
    {
        if (declaringModule == null)
            throw new IllegalArgumentException("Declaring module must not be null");

        if (_declaringModule != null)
            throw new IllegalStateException("Declaring module already set");

        _declaringModule = declaringModule;
    }

    @Override
    public Module getDeclaringModule()
    {
        return _declaringModule;
    }
}
