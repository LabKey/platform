/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.view.ViewBackgroundInfo;
import org.quartz.JobExecutionContext;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Created by adam on 12/2/2015.
 */
public class SystemMaintenanceJob implements org.quartz.Job, Callable<String>
{
    private static final Logger LOG = Logger.getLogger(SystemMaintenanceJob.class);

    private final @Nullable String _taskName;
    private final @Nullable User _user;

    @SuppressWarnings("unused")
    public SystemMaintenanceJob()
    {
        this(null, null);
    }

    public SystemMaintenanceJob(@Nullable String taskName, @Nullable User user)
    {
        _taskName = taskName;
        _user = user;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext)
    {
        // Log all exceptions
        try
        {
            call();
        }
        catch (Throwable t)
        {
            ExceptionUtil.logExceptionToMothership(null, t);
        }
    }

    @Override
    // Determine the tasks to run and queue a pipeline job to run them. This method may throw exceptions.
    public String call()
    {
        Set<String> disabledTasks = SystemMaintenance.getProperties().getDisabledTasks();
        Collection<MaintenanceTask> tasksToRun = new LinkedList<>();

        for (MaintenanceTask task : SystemMaintenance.getTasks())
        {
            if (null != _taskName && !_taskName.isEmpty())
            {
                // If _taskName is set, then admin has invoked a single task from the UI... skip all the others
                if (task.getName().equals(_taskName))
                {
                    tasksToRun.add(task);
                    break;
                }
            }
            else
            {
                // If the task can't be disabled or isn't disabled now then include it
                if (!task.canDisable() || !disabledTasks.contains(task.getName()))
                {
                    tasksToRun.add(task);
                }
            }
        }

        Container c = ContainerManager.getRoot();
        ViewBackgroundInfo vbi = new ViewBackgroundInfo(c, _user, null);
        PipeRoot root = PipelineService.get().findPipelineRoot(c);

        if (null == root)
            throw new ConfigurationException("Invalid pipeline configuration at the root container");

        if (!root.isValid())
            throw new ConfigurationException("Invalid pipeline configuration at the root container: " + root.getRootPath().getPath());

        final String jobGuid;

        try
        {
            PipelineJob job = new MaintenancePipelineJob(vbi, root, tasksToRun);
            LOG.info("Queuing MaintenancePipelineJob [thread " + Thread.currentThread().getName() + " to " + PipelineService.get().toString() + "]");
            PipelineService.get().queueJob(job);
            jobGuid = job.getJobGUID();
        }
        catch (PipelineValidationException e)
        {
            throw new RuntimeException(e);
        }

        return jobGuid;
    }
}
