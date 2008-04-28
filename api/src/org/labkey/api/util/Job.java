package org.labkey.api.util;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: jeckels
* Date: Apr 16, 2008
*/
public abstract class Job implements Future, Runnable
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
            JobRunner._log.error("Uncaught exception in Job: " + this.toString(), t);
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
        return "" + getJobId() + "(" + stateString(this) + ")";
    }

    private static String stateString(Job job)
    {
        if (null == job._task) return "NOT SUBMITTED";
        if (job.isDone() && job.isCancelled()) return "DONE+CANCELLED";
        if (job.isCancelled()) return "CANCELLED";
        if (job.isDone()) return "DONE";
        return "RUNNABLE";
    }
}
