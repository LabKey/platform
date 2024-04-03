package org.labkey.pipeline.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.quartz.DateBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for tracking the job status changes that we failed to persist to the DB because the DB was down.
 * Retries setting the status once a minute.
 */
public class JobStatusRetryJob implements org.quartz.Job
{
    private static final Map<String, Pair<Runnable, Throwable>> _queuedUpdates = Collections.synchronizedMap(new HashMap<>());

    static
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever())
                    .startAt(DateBuilder.futureDate(60, DateBuilder.IntervalUnit.SECOND))
                    .build();

            // Quartz Job that triggers the deferred updates
            JobDetail job = JobBuilder.newJob(JobStatusRetryJob.class).build();

            // Schedule trigger to execute the updates
            scheduler.scheduleJob(job, trigger);
        }
        catch (SchedulerException e)
        {
            UnexpectedException.rethrow(e);
        }
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException
    {
        // Attempt the updates to the DB rows again in the hopes that the DB is back online
        if (!_queuedUpdates.isEmpty())
        {
            Map<String, Pair<Runnable, Throwable>> todo;
            synchronized (_queuedUpdates)
            {
                // Copy so we can iterate and modify the map
                todo = new HashMap<>(_queuedUpdates);
            }

            for (Map.Entry<String, Pair<Runnable, Throwable>> entry : todo.entrySet())
            {
                String jobId = entry.getKey();
                _queuedUpdates.remove(jobId);
                // Retry the update to pipeline.statusfiles
                entry.getValue().first.run();

                PipelineJob job = PipelineJobService.get().getJobStore().getJob(jobId);
                if (job != null)
                {
                    // Fire a done event so that jobs can send notifications or do other finalization now that the DB
                    // is back online
                    job.done(entry.getValue().second);
                }
            }
        }
    }

    /**
     * Queue a deferred job status update attempt
     */
    public static void queue(@NotNull String jobId, @NotNull Runnable r, @NotNull Throwable failure)
    {
        // We only need to remember the most recent update for a given job
        _queuedUpdates.put(jobId, Pair.of(r, failure));
    }
}
