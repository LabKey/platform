package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.di.api.ScheduledPipelineJobContext;
import org.labkey.di.api.ScheduledPipelineJobDescriptor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.concurrent.Callable;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-03-18
 * Time: 4:05 PM
 */
public class TransformJobRunner implements Job
{
    private static final Logger LOG = Logger.getLogger(TransformJobRunner.class);


    public TransformJobRunner()
    {
    }


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        ScheduledPipelineJobContext info = ScheduledPipelineJobContext.getFromJobDetail(context);
        ScheduledPipelineJobDescriptor d = info.getDescriptor();

        Callable c = d.getChecker(info);
        try
        {
            boolean hasWork = Boolean.TRUE == c.call();
            if (!hasWork)
                return;

            PipelineJob job = d.getPipelineJob(info);
            if (null == job)
                return;

            try
            {
                PipelineService.get().setStatus(job, PipelineJob.WAITING_STATUS, null, true);
                PipelineService.get().queueJob(job);
            }
            catch (Exception e)
            {
                LOG.error("Unable to queue ETL job", e);
            }
        }
        catch (JobExecutionException x)
        {
            throw x;
        }
        catch (RuntimeException x)
        {
            throw x;
        }
        catch (Exception x)
        {
            throw new UnexpectedException(x);
        }
    }
}
