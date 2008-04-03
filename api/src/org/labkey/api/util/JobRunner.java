/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import javax.servlet.ServletContextEvent;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a simple Executor, that can be used to implement more advanced services
 * like PipelineQueue, or to simply run background tasks.
 * <p/>
 * ScheduledThreadPoolExecutor could, of course, be used directly.  One annoyance is
 * that is is hard to track when tasks start (except by wrapping the run method).
 * Another annoyance is that the object you use the track the task (Future
 * returned by submit()) is different than the object you submit.
 * <p/>
 * In short this is a ScheduledTheadPoolExecutor that lets you submit a Job,
 * in addition to a Runnable or Callable.  If you submit a Job, you can use
 * it to track you task status.
 * <p/>
 * CONSIDER: handle Callable
 */
public class JobRunner implements Executor
{
    static Logger _log = Logger.getLogger(JobRunner.class);
    static JobRunner _defaultJobRunner = new JobRunner(1);

    private ScheduledThreadPoolExecutor _executor = null;
    private final HashMap<Future, Job> _jobs = new HashMap<Future, Job>();


    public JobRunner()
    {
        this(1);
    }


    public JobRunner(int max)
    {
        this(max, Thread.MIN_PRIORITY);
    }


    public JobRunner(int max, int priority)
    {
        _executor = new JobThreadPoolExecutor(max);
        _executor.setThreadFactory(new JobThreadFactory(priority));
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                _executor.shutdown();
            }
        });
    }


    public static JobRunner getDefault()
    {
        return _defaultJobRunner;
    }


    public void execute(Runnable command)
    {
        synchronized (_jobs)
        {
            Future task = _executor.schedule(command, 0, TimeUnit.MILLISECONDS);
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
                job = _jobs.get((Future) r);
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
            Job job;
            synchronized (_jobs)
            {
                job = _jobs.remove((Future) r);
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
        }
    }

    //
    //  Job
    //

    public static abstract class Job implements Future, Runnable
    {
        private static AtomicInteger _counter = new AtomicInteger(0);

        transient Future _task = null;
        transient int _jobId;
        transient long _startTime = 0;
        transient long _finishTime = 0;

        public Job()
        {
            _jobId = _counter.incrementAndGet();
            //assert MemTracker.put(this);
        }

        protected void starting(Thread t)
        {
        }

        protected void done(Throwable t)
        {
            if (null != t)
                _log.error("Uncaught exception in Job: " + this.toString(), t);
        }

        public int getJobId()
        {
            return _jobId;
        }

        //
        // Future
        //
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            if (_task == null) throw new IllegalStateException("job has not been submitted");
            return _task.cancel(mayInterruptIfRunning);
        }

        public boolean isCancelled()
        {
            if (_task == null) throw new IllegalStateException("job has not been submitted");
            return _task.isCancelled();
        }

        public boolean isDone()
        {
            if (_task == null) throw new IllegalStateException("job has not been submitted");
            return _task.isDone();
        }

        public Object get() throws InterruptedException, ExecutionException
        {
            if (_task == null) throw new IllegalStateException("job has not been submitted");
            return _task.get();
        }

        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            if (_task == null) throw new IllegalStateException("job has not been submitted");
            return _task.get();
        }

        //
        // Object
        //

        public String toString()
        {
            return "" + getJobId() + "(" + _stateString(this) + ")";
        }
    }


    private static String _stateString(Job job)
    {
        if (null == job._task) return "NOT SUBMITTED";
        if (job.isDone() && job.isCancelled()) return "DONE+CANCELLED";
        if (job.isCancelled()) return "CANCELLED";
        if (job.isDone()) return "DONE";
        return "RUNNABLE";
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
