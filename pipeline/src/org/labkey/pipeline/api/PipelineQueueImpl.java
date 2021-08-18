/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobData;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.JobRunner;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs all jobs in the web server with a simple thread pool.
 */
public class PipelineQueueImpl extends AbstractPipelineQueue
{
    private static final Logger LOG = LogManager.getLogger(PipelineQueueImpl.class);
    private static final int MAX_RUNNING_JOBS = 10;

    private final List<PipelineJob> _pending = new ArrayList<>();
    private final List<PipelineJob> _running = new ArrayList<>();

    // This is the list of jobs that have been submitted to JobRunner-- they
    // may be either running or pending.
    private final Set<PipelineJob> _submitted = new HashSet<>();

    private final JobRunner _runner = new JobRunner("Pipeline", MAX_RUNNING_JOBS);

    @Override
    protected synchronized void enqueue(PipelineJob job)
    {
        _pending.add(job);
        submitJobs();
    }

    @Override
    public boolean isLocal()
    {
        // Only place for this queue is local server memory.
        return true;
    }

    @Override
    public boolean isTransient()
    {
        // Only place for this queue is local server memory.
        return true;
    }


    @Override
    public synchronized void starting(PipelineJob job, Thread thread)
    {
        // WARNING: This method is for pipeline maintenance only.  Do not put
        //          important functionality side-effects in here, since this
        //          function is not supported in the Enterprise Pipeline.
        LOG.debug("RUNNING:   " + job.toString());
        boolean removed = _pending.remove(job);
        assert removed;
        _running.add(job);
        thread.setPriority(Thread.NORM_PRIORITY - 1);

        // Set centrally to avoid needing to set in each job. See PipelineJobRunner for equivalent functionality
        // when running through Enterprise Pipeline
        QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, job.getContainer());
        QueryService.get().setEnvironment(QueryService.Environment.USER, job.getUser());
    }


    @Override
    public synchronized void done(PipelineJob job)
    {
        // WARNING: This method is for pipeline maintenance only.  Do not put
        //          important functionality side-effects in here, since this
        //          function is not supported in the Enterprise Pipeline.
        try
        {
            LOG.debug("COMPLETED: " + job.toString());

            // Clear centrally to avoid needing to set in each job. See PipelineJobRunner for equivalent functionality
            // when running through Enterprise Pipeline
            QueryService.get().clearEnvironment();

            ConnectionWrapper.dumpLeaksForThread(Thread.currentThread());
            boolean removed = _running.remove(job);
            assert removed;
            removed = _submitted.remove(job);
            assert removed;
        }
        finally
        {
            submitJobs();
        }
    }

    /**
     * Look through the pending jobs and see if there are any that can be submitted to the runner right now.
     * Some jobs are single threaded.  There can be only one single threaded job in the queue at any time.
     * Multi-threaded jobs are allowed to be run one per container.
     *
     * We do not submit a job to the JobRunner unless it is ok to run it right now.
     * The JobRunner takes care of limiting the simultaneous jobs to {@link #MAX_RUNNING_JOBS}
     */
    private synchronized void submitJobs()
    {
        if (_pending.size() == 0)
            return;
        HashSet<String> containers = new HashSet<>();
        boolean singleThreadedJobFound = false;
        for (PipelineJob job : _submitted)
        {
            containers.add(job.getContainerId());
            if (!job.allowMultipleSimultaneousJobs())
            {
                singleThreadedJobFound = true;
            }
        }
        for (PipelineJob job : _pending.toArray(new PipelineJob[_pending.size()]))
        {
            if (_submitted.contains(job))
                continue;
            if (!job.allowMultipleSimultaneousJobs() && singleThreadedJobFound)
                continue;
            if (containers.contains(job.getContainerId()))
                continue;
            _submitted.add(job);
            containers.add(job.getContainerId());
            _runner.execute(job);
            job.setSubmitted();
            if (!job.allowMultipleSimultaneousJobs())
            {
                singleThreadedJobFound = true;
            }
        }
    }


    boolean inContainer(Container c, PipelineJob job)
    {
        // We use null to mean "all containers"
        return c == null || c.getId().equals(job.getContainerId());
    }

    @Override
    public synchronized boolean cancelJob(User user, Container c, PipelineStatusFile statusFile)
    {
        if (statusFile.getJobStore() != null)
        {
            PipelineJob job = PipelineJob.deserializeJob(statusFile.getJobStore());
            if (job != null)
            {
                job.getLogger().info("Attempting to cancel as requested by " + user);
                PipelineJob.logStartStopInfo("Attempting to cancel job ID " + job.getJobGUID() + ", " + statusFile.getFilePath() + " as requested by " + user);
            }
        }

        // Go through the list of queued (but not running jobs) and remove the requested job, if found
        for (ListIterator<PipelineJob> it = _pending.listIterator(); it.hasNext();)
        {
            PipelineJob job = it.next();
            if (job.getJobGUID().equalsIgnoreCase(statusFile.getJobId()) && inContainer(c, job))
            {
                job.cancel(false);
                it.remove();
                job.getLogger().info("Cancelling job by removing from job queue.");
                PipelineJob.logStartStopInfo("Cancelling job by removing from job queue. Job ID: " + job.getJobGUID() + ", " + statusFile.getFilePath());
                // It should already be set to CANCELLING. Set to CANCELLED to indicate that it's dead.
                statusFile.setStatus(PipelineJob.TaskStatus.cancelled.toString());
                statusFile.save();
                return true;
            }
        }
        for (ListIterator<PipelineJob> it = _running.listIterator(); it.hasNext();)
        {
            PipelineJob job = it.next();
            if (job.getJobGUID().equalsIgnoreCase(statusFile.getJobId()) && inContainer(c, job))
            {
                job.getLogger().info("Interrupting job by sending interrupt request.");
                PipelineJob.logStartStopInfo("Interrupting job by sending interrupt request. Job ID: " + job.getJobGUID() + ", " + statusFile.getFilePath());
                if (job.interrupt())
                {
                    return true;
                }
            }
        }
        submitJobs();
        return false;
    }

    @Override
    public List<PipelineJob> findJobs(String location)
    {
        String locationDefault = PipelineJobService.get().getDefaultExecutionLocation();

        // For the mini-pipeline the only location is the default location.
        // Just return an empty list for any other location that is requested.
        List<PipelineJob> result = new ArrayList<>();
        if (location.equals(locationDefault))
        {
            for (PipelineJob job : _pending)
                result.add(job);
            for (PipelineJob job : _running)
                result.add(job);
        }
        return result;
    }

    @Override
    public synchronized PipelineJobData getJobDataInMemory(Container c)
    {
        PipelineJobData ret = new PipelineJobData();

        for (PipelineJob job : _running)
        {
            if (inContainer(c, job))
                ret.addRunningJob(job);
        }
        for (PipelineJob job : _pending)
        {
            if (inContainer(c, job))
                ret.addPendingJob(job);
        }
        return ret;
    }

    @Override
    @NotNull
    public Map<String, Integer> getQueuePositions()
    {
        Map<String, Integer> result = new HashMap<>();
        synchronized (_running)
        {
            for (PipelineJob pipelineJob : _running)
            {
                result.put(pipelineJob.getJobGUID(), 1);
            }
        }
        int position = _running.isEmpty() ? 0 : 1;
        synchronized (_pending)
        {
            for (PipelineJob pipelineJob : _pending)
            {
                if (!result.containsKey(pipelineJob.getJobGUID()))
                {
                    result.put(pipelineJob.getJobGUID(), ++position);
                }
            }
        }
        return result;
    }

    //
    // JUNIT
    //

    private static class TestJob extends PipelineJob
    {
        AtomicInteger _counter;

        // For serialization
        protected TestJob() {}

        TestJob(Container c, AtomicInteger counter)
        {
            super(null, new ViewBackgroundInfo(c, null, null), PipelineService.get().findPipelineRoot(c));
            _counter = counter;
        }

        @Override
        public void run()
        {
            long til = System.currentTimeMillis() + 1000;
            double[] a = new double[10000];
            while (til > System.currentTimeMillis())
            {
                for (int i = 0; i < a.length; i++)
                    a[i] = Math.random();
                Arrays.sort(a);
                Thread.yield();
            }
            _counter.incrementAndGet();
        }

        @Override
        public String getDescription()
        {
            return "test job";
        }

        @Override
        public ActionURL getStatusHref()
        {
            return null;
        }
    }

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        @Test
        public void testPipeline() throws Exception
        {
            Container root = ContainerManager.createFakeContainer(null, null);
            Container containerA = ContainerManager.createFakeContainer("A", root);
            Container containerB = ContainerManager.createFakeContainer("B", root);

            PipelineQueueImpl queue = new PipelineQueueImpl();
            PipelineJobData data;
            AtomicInteger counter = new AtomicInteger();

            TestJob[] jobs = new TestJob[]
                    {
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                            new TestJob(containerA, counter),
                            new TestJob(containerB, counter),
                    };

            // Add four jobs
            for (int i = 0; i < 4; i++)
                queue.addJob(jobs[i]);
            Thread.sleep(1);
            data = queue.getJobDataInMemory(containerA);
            //assertEquals(2, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());
            data = queue.getJobDataInMemory(containerB);
            //assertEquals(2, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // wait a bit
            Thread.sleep(100);
            data = queue.getJobDataInMemory(null);
            //assertEquals(4, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // add remaining jobs
            for (int i = 4; i < jobs.length; i++)
                queue.addJob(jobs[i]);
            Thread.sleep(1);
            data = queue.getJobDataInMemory(null);
            //assertEquals(jobs.length, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // wait for last submitted job to finish
            PipelineJob last = jobs[jobs.length - 1];
            last.get();
            assertTrue(last.isDone());
            data = queue.getJobDataInMemory(null);
            //assertEquals(jobs.length, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());
            assertFalse(data.getPendingJobs().contains(last));
            //assertTrue(data.getCompletedJobs().contains(last) || data.getRunningJobs().contains(last));

            for (TestJob job : jobs)
            {
                job.get();
                assertTrue(job.isDone());
            }
            data = queue.getJobDataInMemory(null);
            Thread.sleep(10);
            data = queue.getJobDataInMemory(null);

            data = queue.getJobDataInMemory(containerA);
            data = queue.getJobDataInMemory(null);

            data = queue.getJobDataInMemory(null);


            assertEquals(0, queue._runner.getJobCount());
            assertEquals(jobs.length, counter.get());
        }
    }
}
