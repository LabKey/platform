/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.DbScope;

import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a simple Executor, that can be used to implement more advanced services
 * like PipelineQueue, or to simply run background tasks.
 * <p/>
 * ScheduledThreadPoolExecutor could, of course, be used directly.  One annoyance is
 * that is is hard to track when tasks start (except by wrapping the run method).
 * Another annoyance is that the object you use to track the task (Future
 * returned by submit()) is different than the object you submit.
 * <p/>
 * In short this is a ScheduledThreadPoolExecutor that lets you submit a Job,
 * in addition to a Runnable or Callable.  If you submit a Job, you can use
 * it to track you task status.
 * <p/>
 * CONSIDER: handle Callable
 */
public class JobRunner implements Executor
{
    static final Logger _log = Logger.getLogger(JobRunner.class);

    private static final JobRunner _defaultJobRunner = new JobRunner("Default", 1);

    private final ScheduledThreadPoolExecutor _executor;
    private final HashMap<Future, Job> _jobs = new HashMap<>();


    public JobRunner(String name, int max)
    {
        this(name, max, Thread.MIN_PRIORITY);
    }


    private JobRunner(String name, int max, int priority)
    {
        _executor = new JobThreadPoolExecutor(max);
        _executor.setThreadFactory(new JobThreadFactory(priority));
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return "Job Runner (" + name + ")";
            }

            public void shutdownPre()
            {
                _executor.shutdown();
            }

            public void shutdownStarted()
            {
            }
        });
    }


    public static JobRunner getDefault()
    {
        return _defaultJobRunner;
    }

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
                catch (InterruptedException e) {}
            }
        }
    }

    public void shutdown()
    {
        _executor.shutdown();
    }

    /**
     * This will schedule the runnable to execute immediately, with no delay
     * @param command
     */
    public void execute(Runnable command)
    {
        execute(command, 0);
    }

    /**
     * This will schedule the runnable using the provided delay
     * @param command
     * @param delay
     */
    public void execute(Runnable command, long delay)
    {
        synchronized (_jobs)
        {
            Future task = _executor.schedule(command, delay, TimeUnit.MILLISECONDS);
            if (command instanceof Job)
            {
                Job job = (Job) command;
                job._task = task;
                _jobs.put(task, job);
            }
        }
    }

    public Future submit(Runnable run)
    {
        if (run instanceof Job)
        {
            execute(run);
            return (Job) run;
        }
        return _executor.schedule(run, 0, TimeUnit.MILLISECONDS);
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

        protected void beforeExecute(Thread t, Runnable r)
        {
            super.beforeExecute(t, r);

            Job job;
            synchronized (_jobs)
            {
                job = _jobs.get(r);
            }
            if (null != job)
            {
                _logDebug("beforeExecute: " + job.toString());
                job.starting(t);
                job._startTime = System.currentTimeMillis();
            }
        }


        protected void afterExecute(Runnable r, Throwable t)
        {
            try
            {
                Job job;
                synchronized (_jobs)
                {
                    job = _jobs.remove(r);
                }
                if (null != job)
                {
                    job._finishTime = System.currentTimeMillis();
                    _logDebug("afterExecute: " + job.toString());
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
                }
                else
                {
                    if (r instanceof Future)
                    {
                        if (null == t)
                        {
                            try
                            {
                                ((Future)r).get();
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
                    }
                }

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


    static class JobThreadFactory implements ThreadFactory
    {
        static final AtomicInteger poolNumber = new AtomicInteger(1);
        final ThreadGroup group;
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;
        final int priority;

        JobThreadFactory(int priority)
        {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "JobThread-" + poolNumber.getAndIncrement() + ".";
            this.priority = priority;
        }

        public Thread newThread(Runnable r)
        {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
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
}
