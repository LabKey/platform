package org.labkey.api.util;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewServlet;

import java.io.File;
import java.util.Collection;

/**
 * Created by adam on 12/2/2015.
 */

// Runs each MaintenanceTask in order
class MaintenancePipelineJob extends PipelineJob
{
    private final Collection<MaintenanceTask> _tasks;

    public MaintenancePipelineJob(ViewBackgroundInfo info, PipeRoot pipeRoot, Collection<MaintenanceTask> tasks)
    {
        super(null, info, pipeRoot);
        setLogFile(new File(pipeRoot.getRootPath(), FileUtil.makeFileNameWithTimestamp("system_maintenance", "log")));
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
                task.run();
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
