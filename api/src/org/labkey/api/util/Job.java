/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * User: jeckels
* Date: Apr 16, 2008
*/
public abstract class Job implements Future, Runnable
{
    transient Future _task = null;
    transient long _startTime = 0;
    transient long _finishTime = 0;

    public Job()
    {
        //MemTracker.getInstance().put(this);
    }

    protected void starting(Thread t)
    {
    }

    protected void done(Throwable t)
    {
        if (null != t)
            JobRunner._log.error("Uncaught exception in Job: " + this.toString(), t);
    }

    //
    // Future
    //
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if (_task == null)
        {
            return false;
        }
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
        return "(" + stateString(this) + ")";
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
