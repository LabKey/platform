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

package org.labkey.api.util;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope;
import org.labkey.api.util.logging.LogHelper;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This is a simple Executor that can be used to implement more advanced services
 * like PipelineQueue, or to simply run background tasks.
 * <p/>
 * ScheduledThreadPoolExecutor could, of course, be used directly.  One annoyance is
 * that it is hard to track when tasks start (except by wrapping the run method).
 * Another annoyance is that the object you use to track the task (Future
 * returned by submit()) is different from the object you submit.
 * <p/>
 * In short, this is a ScheduledThreadPoolExecutor that lets you submit a Job,
 * in addition to a Runnable or Callable.  If you submit a Job, you can use
 * it to track your task's status.
 * <p/>
 * CONSIDER: handle Callable
 */
public class JobRunner implements Executor
{
    static final Logger _log = LogHelper.getLogger(JobRunner.class, "JobRunner status and errors");

    private static final JobRunner _defaultJobRunner = new JobRunner("Default", 1);

    private final ScheduledThreadPoolExecutor _executor;
    private final Map<Future<?>, Job> _jobs = new HashMap<>();

    public JobRunner(String name, int max)
    {
        this(name, max, null);
    }

    public JobRunner(String name, int max, @Nullable Supplier<String> threadNameFactory)
    {
        this(name, max, threadNameFactory, Thread.MIN_PRIORITY);
    }

    private JobRunner(String name, int max, @Nullable Supplier<String> threadNameFactory, int priority)
    {
        _executor = new JobThreadPoolExecutor(max);
        _executor.setThreadFactory(new JobThreadFactory(threadNameFactory, priority));
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return "Job Runner (" + name + ")";
            }

            @Override
            public void shutdownPre()
            {
                _executor.shutdown();
            }
        });
    }


    public static JobRunner getDefault()
    {
        return _defaultJobRunner;
    }

    /**
     * Waits for all submitted jobs to complete. Does not require shutdown.
     */
    public void waitForCompletion()
    {
        synchronized (_jobs)
        {
            while (!_jobs.isEmpty())
            {
                try
                {
                    _jobs.wait();
                }
                catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * @see java.util.concurrent.ExecutorService#shutdown()
     */
    public void shutdown()
    {
        _executor.shutdown();
    }

    /**
     * @see java.util.concurrent.ExecutorService#awaitTermination(long, TimeUnit)
     */
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException
    {
        return _executor.awaitTermination(timeout, unit);
    }

    /**
     * This will schedule the runnable to execute immediately, with no delay
     */
    @Override
    public void execute(@NotNull Runnable command)
    {
        execute(command, 0);
    }

    /**
     * This will schedule the runnable using the provided delay
     */
    public Future<?> execute(Runnable command, long delay)
    {
        Job job = command instanceof Job j ? j :  new RunnableJob(command);

        synchronized (_jobs)
        {
            Future<?> task = _executor.schedule(command, delay, TimeUnit.MILLISECONDS);

            job._task = task;
            _jobs.put(task, job);

            return task;
        }
    }

    public int getJobCount()
    {
        synchronized (_jobs)
        {
            return _jobs.size();
        }
    }


    class JobThreadPoolExecutor extends ScheduledThreadPoolExecutor
    {
        JobThreadPoolExecutor(int max)
        {
            super(max);
            setMaximumPoolSize(max);
        }

        private Job toJob(Runnable r, boolean remove)
        {
            if (!(r instanceof Future<?> f))
            {
                throw new IllegalArgumentException("Runnable must also be a Future");
            }
            synchronized (_jobs)
            {
                Job job = remove ? _jobs.remove(f) : _jobs.get(f);
                if (null == job)
                {
                    throw new IllegalArgumentException("Future is not associated with a Job");
                }
                return job;
            }
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r)
        {
            super.beforeExecute(t, r);

            Job job = toJob(r, false);
            _logDebug("beforeExecute: " + job);
            job.starting(t);
            job._startTime = System.currentTimeMillis();
        }


        @Override
        protected void afterExecute(Runnable r, Throwable t)
        {
            try
            {
                Job job = toJob(r, true);

                job._finishTime = System.currentTimeMillis();
                _logDebug("afterExecute: " + job);
                if (null == t)
                {
                    try
                    {
                        job._task.get();
                    }
                    catch (ExecutionException x)
                    {
                        t = x.getCause();
                    }
                    catch (Throwable x)
                    {
                        t = x;
                    }
                }
                job.done(t);

                if (t != null)
                {
                    ExceptionUtil.logExceptionToMothership(null, t);
                }

                super.afterExecute(r, t);

                synchronized (_jobs)
                {
                    _jobs.notifyAll();
                }
            }
            finally
            {
                DbScope.finishedWithThread();
            }
        }
    }


    private static class JobThreadFactory implements ThreadFactory
    {
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);

        private final ThreadGroup _group;
        private final AtomicInteger _threadNumber = new AtomicInteger(1);
        private final Supplier<String> _threadNameFactory;
        private final int priority;

        JobThreadFactory(@Nullable Supplier<String> threadNameFactory, int priority)
        {
            _group = Thread.currentThread().getThreadGroup();
            _threadNameFactory = threadNameFactory != null ? threadNameFactory : () -> "JobThread-" + POOL_NUMBER.getAndIncrement() + "." + _threadNumber.getAndIncrement();
            this.priority = priority;
        }

        @Override
        public Thread newThread(@NotNull Runnable r)
        {
            Thread t = new Thread(_group, r, _threadNameFactory.get(), 0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != priority)
                t.setPriority(priority);
            return t;
        }
    }


    private void _logDebug(String s)
    {
        _log.debug(s);
    }

    private static class RunnableJob extends Job
    {
        private final Runnable _runnable;

        private RunnableJob(Runnable runnable)
        {
            _runnable = runnable;
        }

        @Override
        public void run()
        {
            _runnable.run();
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testJobCallbacks() throws Exception
        {
            JobRunner runner = new JobRunner("testJobCallbacks", 1);
            try
            {
                AtomicBoolean startingCalled = new AtomicBoolean(false);
                AtomicBoolean doneCalled = new AtomicBoolean(false);
                CountDownLatch latch = new CountDownLatch(1);

                Job job = new Job()
                {
                    @Override
                    protected void starting(Thread t)
                    {
                        startingCalled.set(true);
                    }

                    @Override
                    protected void done(Throwable t)
                    {
                        doneCalled.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void run()
                    {
                    }
                };

                runner.execute(job);
                assertTrue("Timed out waiting for job to complete", latch.await(5, TimeUnit.SECONDS));
                assertTrue("starting() should have been called", startingCalled.get());
                assertTrue("done() should have been called", doneCalled.get());
            }
            finally
            {
                runner.shutdown();
            }
        }

        @Test
        public void testRunnableCallbacks() throws Exception
        {
            JobRunner runner = new JobRunner("testRunnableCallbacks", 1);
            try
            {
                AtomicBoolean startingCalled = new AtomicBoolean(false);
                AtomicBoolean doneCalled = new AtomicBoolean(false);
                CountDownLatch latch = new CountDownLatch(1);

                runner.execute(new RunnableJob(() -> {
                })
                {
                    @Override
                    protected void starting(Thread t)
                    {
                        startingCalled.set(true);
                    }

                    @Override
                    protected void done(Throwable t)
                    {
                        doneCalled.set(true);
                        latch.countDown();
                    }
                }, 0);
                assertTrue("Timed out waiting for runnable to complete", latch.await(5, TimeUnit.SECONDS));
                assertTrue("starting() should have been called", startingCalled.get());
                assertTrue("done() should have been called", doneCalled.get());
            }
            finally
            {
                runner.shutdown();
            }
        }

        @Test
        public void testWaitForCompletion() throws InterruptedException
        {
            JobRunner runner = new JobRunner("testWaitForCompletion", 2);
            try
            {
                int jobCount = 5;
                CountDownLatch startLatch = new CountDownLatch(1);
                AtomicInteger completedCount = new AtomicInteger(0);

                for (int i = 0; i < jobCount; i++)
                {
                    runner.execute(() -> {
                        try
                        {
                            startLatch.await();
                            completedCount.incrementAndGet();
                        }
                        catch (InterruptedException e)
                        {
                            throw new RuntimeException(e);
                        }
                    });
                }

                assertEquals("Jobs should not be completed yet", 0, completedCount.get());
                startLatch.countDown();
                runner.waitForCompletion();
                assertEquals("All jobs should be completed", jobCount, completedCount.get());
            }
            finally
            {
                runner.shutdown();
            }
        }
    }

}
