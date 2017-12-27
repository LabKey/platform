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

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewServlet;

import java.io.File;
import java.util.Collection;

/**
 * Runs each {@link MaintenanceTask} in order. If any fail, the others still run, but the job will eventually be set to ERROR
 * status.
 * Created by adam on 12/2/2015.
 */
class MaintenancePipelineJob extends PipelineJob
{
    private final Collection<MaintenanceTask> _tasks;

    MaintenancePipelineJob(ViewBackgroundInfo info, PipeRoot pipeRoot, Collection<MaintenanceTask> tasks)
    {
        super(null, info, pipeRoot);
        setLogFile(new File(pipeRoot.getLogDirectory(), FileUtil.makeFileNameWithTimestamp("system_maintenance", "log")));
        _tasks = tasks;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "System Maintenance";
    }

    @Override
    public void run()
    {
        info("System maintenance started");
        TaskStatus finalStatus = TaskStatus.complete;

        for (MaintenanceTask task : _tasks)
        {
            if (ViewServlet.isShuttingDown())
            {
                info("System maintenance is stopping due to server shut down");
                break;
            }

            boolean success;
            setStatus("Running " + task.getName());
            info(task.getDescription() + " started");
            long start = System.currentTimeMillis();

            try
            {
                task.run(getLogger());
                success = true;
            }
            catch (Throwable t)
            {
                // Log if one of these tasks throws... but continue with other tasks
                ExceptionUtil.logExceptionToMothership(null, t);
                error("Failure running " + task.getName(), t);
                success = false;
                finalStatus = TaskStatus.error;
            }

            long elapsed = System.currentTimeMillis() - start;
            info(task.getDescription() + (success ? " complete; elapsed time " + elapsed / 1000 + " seconds" : " failed"));
        }

        info("System maintenance complete");
        setStatus(finalStatus);
    }
}
