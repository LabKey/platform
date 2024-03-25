package org.labkey.pipeline.api;

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
    private static final Map<String, Runnable> _queuedUpdates = Collections.synchronizedMap(new HashMap<>());

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
            Map<String, Runnable> todo;
            synchronized (_queuedUpdates)
            {
                // Copy so we can iterate and modify the map
                todo = new HashMap<>(_queuedUpdates);
            }

            for (Map.Entry<String, Runnable> entry : todo.entrySet())
            {
                _queuedUpdates.remove(entry.getKey());
                // Retry the update
                entry.getValue().run();
            }
        }
    }

    /**
     * Queue a deferred job status update attempt
     */
    public static void queue(String jobId, Runnable r)
    {
        // We only need to remember the most recent update for a given job
        _queuedUpdates.put(jobId, r);
    }
}
