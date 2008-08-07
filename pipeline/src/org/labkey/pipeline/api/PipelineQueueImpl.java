/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JobRunner;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ActionURL;
import org.labkey.api.pipeline.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import java.sql.SQLException;

/**
 */
public class PipelineQueueImpl implements PipelineQueue
{
    private static Logger _log = Logger.getLogger(PipelineQueueImpl.class);
    static private int PRIORITY = Thread.NORM_PRIORITY - 1;
    private int MAX_RUNNING_JOBS = 10;

    List<PipelineJob> _pending = new ArrayList<PipelineJob>();
    List<PipelineJob> _running = new ArrayList<PipelineJob>();

    // This is the list of jobs that have been submitted to JobRunner-- they
    // may be either running or pending.
    HashSet<PipelineJob> _submitted = new HashSet<PipelineJob>();

    JobRunner _runner = new JobRunner(MAX_RUNNING_JOBS);

    public synchronized void addJob(PipelineJob job) throws IOException
    {
        addJob(job, PipelineJob.WAITING_STATUS);
    }

    public synchronized void addJob(PipelineJob job, String initialState)
    {
        if (null == job)
            throw new NullPointerException();
        _logDebug("PENDING:   " + job.toString());

        try
        {
            // Make sure status file path and Job ID are in synch.
            File statusFile = job.getStatusFile();
            if (statusFile != null)
                PipelineStatusManager.resetJobId(job.getStatusFile().getAbsolutePath(), job.getJobGUID());
        }
        catch (SQLException e)
        {
            _log.warn(e);  // This is not currently a hard dependency.
        }

        _pending.add(job);
        job.setQueue(this, initialState);
        submitJobs();
    }

    public boolean isLocal()
    {
        // Only place for this queue is local server memory.
        return true;
    }

    public boolean isTransient()
    {
        // Only place for this queue is local server memory.
        return true;
    }


    public synchronized void starting(PipelineJob job, Thread thread)
    {
        _logDebug("RUNNING:   " + job.toString());
        boolean removed = _pending.remove(job);
        assert removed;
        _running.add(job);
        thread.setPriority(PRIORITY);
    }


    public synchronized void done(PipelineJob job)
    {
        _logDebug("COMPLETED: " + job.toString());
        ConnectionWrapper.dumpLeaksForThread(Thread.currentThread());
        File serializedFile = PipelineJob.getSerializedFile(job.getStatusFile());
        if (serializedFile != null && serializedFile.exists())
        {
            serializedFile.delete();
        }
        boolean removed = _running.remove(job);
        assert removed;
        removed = _submitted.remove(job);
        assert removed;
        submitJobs();

        try
        {
            File statusFile = job.getStatusFile();
            if (statusFile != null)
            {
                PipelineStatusFileImpl sf = PipelineStatusManager.getStatusFile(statusFile.getAbsolutePath());
                if (sf != null)
                    PipelineManager.sendNotificationEmail(sf, job.getContainer());
            }
        }
        catch (Exception e)
        {
            _log.error("Failed trying to send email notification for a pipeline job", e);
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
        HashSet<String> containers = new HashSet();
        boolean singleThreadedJobFound = false;
        for (PipelineJob job : _submitted)
        {
            containers.add(job.getContainerId());
            if (!job.allowMultipleSimultaneousJobs())
            {
                singleThreadedJobFound = true;
            }
        }
        for (PipelineJob job : _pending.toArray(new PipelineJob[0]))
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
        if (c == null)
            return true;
        return c.getId().equals(job.getContainerId());
    }

    public synchronized void cancelPendingJobs(Container c)
    {
        for (ListIterator<PipelineJob> it = _pending.listIterator(); it.hasNext();)
        {
            PipelineJob job = it.next();
            if (inContainer(c, job))
            {
                if (job.cancel(false))
                {
                    if (job.getLogFile() != null)
                    {
                        job.setStatus(PipelineJob.CANCELLED_STATUS);
                    }
                    it.remove();
                }
            }
        }
    }

    public synchronized boolean cancelJob(Container c, int jobId)
    {
        for (ListIterator<PipelineJob> it = _pending.listIterator(); it.hasNext();)
        {
            PipelineJob job = it.next();
            if (job.getJobId() == jobId && inContainer(c, job))
            {
                if (job.cancel(false))
                {
                    it.remove();
                    return true;
                }
            }
        }
        for (ListIterator<PipelineJob> it = _running.listIterator(); it.hasNext();)
        {
            PipelineJob job = it.next();
            if (job.getJobId() == jobId && inContainer(c, job))
            {
                if (job.interrupt())
                {
                    return true;
                }
            }
        }
        submitJobs();
        return false;
    }

    public List<PipelineJob> findJobs(String location)
    {
        List<PipelineJob> result = new ArrayList<PipelineJob>();
        for (PipelineJob job : _pending)
        {
            if (job.getActiveTaskFactory().getExecutionLocation().equals(location))
            {
                result.add(job);
            }
        }
        for (PipelineJob job : _running)
        {
            if (job.getActiveTaskFactory().getExecutionLocation().equals(location))
            {
                result.add(job);
            }
        }
        return result;
    }

    private boolean statusFileMatches(PipelineJob job, String statusFile)
    {
        File fileCompare = job.getStatusFile();
        if (fileCompare == null)
            return false;
        String compare = PipelineJobService.statusPathOf(fileCompare.toString());
        return new File(compare).equals(new File(statusFile));
    }

    public PipelineJob findJob(Container c, String statusFile)
    {
        PipelineJobData jd = getJobData(c);
        statusFile = PipelineJobService.statusPathOf(statusFile);
        for (PipelineJob job : jd.getRunningJobs())
        {
            if (statusFileMatches(job, statusFile))
                return job;
        }
        for (PipelineJob job : jd.getPendingJobs())
        {
            if (statusFileMatches(job, statusFile))
                return job;
        }
        return null;
    }

    public synchronized PipelineJobData getJobData(Container c)
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

    public synchronized PipelineJob findJob(String jobGUID)
    {
        for (PipelineJob job : _running)
        {
            if (jobGUID.equals(job.getJobGUID()))
                return job;
        }
        for (PipelineJob job : _pending)
        {
            if (jobGUID.equals(job.getJobGUID()))
                return job;
        }
        return null;
    }


    private void _logDebug(String s)
    {
        _log.debug(s);
        //System.err.println(s);
    }

    //
    // JUNIT
    //

    private static class TestJob extends PipelineJob
    {
        AtomicInteger _counter;

        TestJob(Container c, AtomicInteger counter) throws SQLException
        {
            super(null, new ViewBackgroundInfo(c, null, null));
            _counter = counter;
        }

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

        public String getDescription()
        {
            return "task " + getJobId();
        }

        public ActionURL getStatusHref()
        {
            return null;
        }
    }

    static class FakeContainer extends Container
    {
        FakeContainer(String path, Container parent)
        {
            super(path, parent, GUID.makeGUID(), 1, 0, new Date());
        }
    }

    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super("PipelineQueue");
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testPipeline()
                throws Exception
        {
            Container root = new FakeContainer("/", null);
            Container containerA = new FakeContainer("/A", root);
            Container containerB = new FakeContainer("/B", root);

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
            data = queue.getJobData(containerA);
            //assertEquals(2, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());
            data = queue.getJobData(containerB);
            //assertEquals(2, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // wait a bit
            Thread.sleep(100);
            data = queue.getJobData(null);
            //assertEquals(4, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // add remaining jobs
            for (int i = 4; i < jobs.length; i++)
                queue.addJob(jobs[i]);
            Thread.sleep(1);
            data = queue.getJobData(null);
            //assertEquals(jobs.length, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());

            // wait for last submitted job to finish
            PipelineJob last = jobs[jobs.length - 1];
            last.get();
            assertTrue(last.isDone());
            data = queue.getJobData(null);
            //assertEquals(jobs.length, data.getPendingJobs().size() + data.getRunningJobs().size() + data.getCompletedJobs().size());
            assertFalse(data.getPendingJobs().contains(last));
            //assertTrue(data.getCompletedJobs().contains(last) || data.getRunningJobs().contains(last));

            for (TestJob job : jobs)
            {
                job.get();
                assertTrue(job.isDone());
            }
            data = queue.getJobData(null);
            Thread.sleep(10);
            data = queue.getJobData(null);

            data = queue.getJobData(containerA);
            data = queue.getJobData(null);

            data = queue.getJobData(null);


            assertEquals(0, queue._runner.getJobCount());
            assertEquals(jobs.length, counter.get());
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
