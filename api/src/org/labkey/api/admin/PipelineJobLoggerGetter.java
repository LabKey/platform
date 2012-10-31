package org.labkey.api.admin;

import org.apache.log4j.Logger;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.pipeline.PipelineJob;

import java.io.Serializable;

/**
 * Implementation used within a pipeline job. The job knows how to create a Logger that writes to the job's log file.
 * User: jeckels
 * Date: 10/30/12
 */
public class PipelineJobLoggerGetter implements LoggerGetter, Serializable
{
    private final PipelineJob _job;

    public PipelineJobLoggerGetter(PipelineJob job)
    {
        _job = job;
    }

    @Override
    public Logger getLogger()
    {
        return _job.getLogger();
    }
}
