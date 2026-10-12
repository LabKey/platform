/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.core;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.core.JobRunShell;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Shuts down a Quartz scheduler, logging running jobs and waiting a bounded time for them to finish. */
public class QuartzShutdown
{
    private static final Logger LOG = LogHelper.getLogger(QuartzShutdown.class, "Quartz scheduler shutdown");

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(15);

    /** @return true if all running jobs finished before the timeout */
    public static boolean shutdown(@NotNull Scheduler scheduler, @NotNull Duration timeout) throws SchedulerException
    {
        scheduler.standby();

        boolean finished = true;
        List<JobExecutionContext> running = scheduler.getCurrentlyExecutingJobs();
        if (!running.isEmpty())
        {
            LOG.info("Waiting up to {} seconds for {} running Quartz job(s) to finish", timeout.toSeconds(), running.size());
            logJobs(running);

            long deadline = System.nanoTime() + timeout.toNanos();
            long nextProgress = System.nanoTime() + PROGRESS_INTERVAL.toNanos();
            while (!running.isEmpty() && System.nanoTime() < deadline)
            {
                try
                {
                    Thread.sleep(POLL_INTERVAL);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
                running = scheduler.getCurrentlyExecutingJobs();
                if (!running.isEmpty() && System.nanoTime() >= nextProgress)
                {
                    LOG.info("Still waiting for {} running Quartz job(s)", running.size());
                    logJobs(running);
                    nextProgress += PROGRESS_INTERVAL.toNanos();
                }
            }

            if (!running.isEmpty())
            {
                LOG.warn("{} Quartz job(s) still running after waiting {} seconds; shutting down without them", running.size(), timeout.toSeconds());
                logJobs(running);
                logWorkerStacks(scheduler.getSchedulerName());
                finished = false;
            }
        }

        // Never shutdown(true): triggers acquired before standby() still fire, and that wait is unbounded
        scheduler.shutdown(false);
        return finished;
    }

    private static void logJobs(List<JobExecutionContext> jobs)
    {
        long now = System.currentTimeMillis();
        for (JobExecutionContext context : jobs)
        {
            LOG.info("Running Quartz job {} ({}), trigger {}, running for {} seconds",
                context.getJobDetail().getKey(),
                context.getJobDetail().getJobClass().getName(),
                context.getTrigger().getKey(),
                (now - context.getFireTime().getTime()) / 1000);
        }
    }

    // Idle workers have no JobRunShell frame
    private static void logWorkerStacks(String schedulerName)
    {
        String prefix = schedulerName + "_Worker";
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
        {
            StackTraceElement[] stack = entry.getValue();
            if (entry.getKey().getName().startsWith(prefix) && Arrays.stream(stack).anyMatch(frame -> frame.getClassName().equals(JobRunShell.class.getName())))
            {
                LOG.warn("Stack for Quartz worker thread \"{}\":\n{}", entry.getKey().getName(),
                    Arrays.stream(stack).map(frame -> "\tat " + frame).collect(Collectors.joining("\n")));
            }
        }
    }

    public static class TestCase extends Assert
    {
        private static volatile CountDownLatch _started;

        private Scheduler _scheduler;

        public static class BlockingJob implements Job
        {
            @Override
            public void execute(JobExecutionContext context)
            {
                _started.countDown();
                try
                {
                    Thread.sleep(context.getMergedJobDataMap().getLong("sleepMillis"));
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Before
        public void setUp() throws SchedulerException
        {
            _started = new CountDownLatch(1);

            Properties props = new Properties();
            props.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "QuartzShutdownTest-" + GUID.makeGUID());
            props.setProperty("org.quartz.threadPool.threadCount", "2");
            props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
            _scheduler = new StdSchedulerFactory(props).getScheduler();
            _scheduler.start();
        }

        @After
        public void tearDown() throws SchedulerException
        {
            if (!_scheduler.isShutdown())
                _scheduler.shutdown(false);
        }

        private void startBlockingJob(long sleepMillis) throws Exception
        {
            _scheduler.scheduleJob(JobBuilder.newJob(BlockingJob.class).usingJobData("sleepMillis", sleepMillis).build(), TriggerBuilder.newTrigger().startNow().build());
            assertTrue("Job didn't start", _started.await(10, TimeUnit.SECONDS));
        }

        @Test
        public void testNoRunningJobs() throws SchedulerException
        {
            assertTrue(shutdown(_scheduler, Duration.ofSeconds(5)));
            assertTrue(_scheduler.isShutdown());
        }

        @Test
        public void testJobFinishesBeforeTimeout() throws Exception
        {
            startBlockingJob(500);

            assertTrue(shutdown(_scheduler, Duration.ofSeconds(20)));
            assertTrue(_scheduler.isShutdown());
        }

        @Test
        public void testStuckJobTimesOut() throws Exception
        {
            startBlockingJob(5_000);

            long start = System.nanoTime();
            assertFalse(shutdown(_scheduler, Duration.ofSeconds(1)));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("Shutdown took " + elapsedMillis + "ms", elapsedMillis < 10_000);
            assertTrue(_scheduler.isShutdown());
        }
    }
}
