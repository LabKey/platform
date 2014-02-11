package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineQueue;
import org.labkey.api.pipeline.PipelineValidationException;

import java.io.File;

/**
 * User: jeckels
 * Date: 2/10/14
 */
public abstract class AbstractPipelineQueue implements PipelineQueue
{
    private static Logger LOG = Logger.getLogger(AbstractPipelineQueue.class);

    @Override
    public void addJob(PipelineJob job, boolean preserveJobId) throws PipelineValidationException
    {
        if (null == job)
            throw new NullPointerException();

        job.validateParameters();

        LOG.debug("PENDING:   " + job.toString());

        // Make sure status file path and Job ID are in synch.
        File logFile = job.getLogFile();
        if (logFile != null)
        {
            PipelineStatusFileImpl pipelineStatusFile = PipelineStatusManager.getStatusFile(logFile);
            if (pipelineStatusFile == null)
            {
                PipelineStatusManager.setStatusFile(job, job.getUser(), PipelineJob.WAITING_STATUS, null, true);
            }

            if (!preserveJobId)
            {
                PipelineStatusManager.resetJobId(job.getLogFile(), job.getJobGUID());
            }
        }

        if (job.setQueue(this, PipelineJob.WAITING_STATUS))
        {
            enqueue(job);
        }

    }

    protected abstract void enqueue(PipelineJob job);
}
