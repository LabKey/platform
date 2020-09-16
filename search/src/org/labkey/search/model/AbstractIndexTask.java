/*
 * Copyright (c) 2009-2018 LabKey Corporation
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
package org.labkey.search.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.search.SearchService;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractIndexTask implements SearchService.IndexTask
{
    final private String _description;
    final protected SearchService.TaskListener _listener;
    protected long _start;
    protected long _complete = 0;
    protected boolean _cancelled = false;
    protected boolean _isReady = false;
    final private AtomicInteger _estimate = new AtomicInteger();
    final private AtomicInteger _indexed = new AtomicInteger();
    final private AtomicInteger _failed = new AtomicInteger();
    final protected Map<Object,Object> _subtasks = Collections.synchronizedMap(new IdentityHashMap<>());
    final StringWriter _sw = new StringWriter();
    final PrintWriter _out = new PrintWriter(_sw);

    final Object _completeEvent = new Object(){public String toString() {return "complete event";}};

    
    public AbstractIndexTask(String description, @Nullable SearchService.TaskListener l)
    {
        _description = description;
        _start = System.currentTimeMillis();
        _listener = l;
    }


    @Override
    public String getDescription()
    {
        return _description;
    }


    @Override
    public int getDocumentCountEstimate()
    {
        return _estimate.get();
    }


    @Override
    public int getIndexedCount()
    {
        return _indexed.get();
    }


    @Override
    public int getFailedCount()
    {
        return _failed.get();
    }


    @Override
    public long getStartTime()
    {
        return _start;
    }


    @Override
    public long getCompleteTime()
    {
        return _complete;
    }


    protected void addItem(Object item)
    {
        _subtasks.put(item,item);
    }


    @Override
    public void log(String message)
    {
        synchronized (_sw)
        {
            _out.println(message);
        }
    }


    @Override
    public Reader getLog()
    {
        synchronized (_sw)
        {
            return new StringReader(_sw.getBuffer().toString());
        }
    }


    @Override
    public void addToEstimate(int i)
    {
        _estimate.addAndGet(i);
    }


    // indicates that caller is done adding Resources to this task
    @Override
    public void setReady()
    {
        synchronized (_completeEvent)
        {
            _isReady = true;
            if (_subtasks.isEmpty())
                if (checkDone())
                    fireDone();
        }
    }


    protected void completeItem(AbstractSearchService.Item item, boolean success)
    {
        if (_cancelled)
            return;
        if (success)
        {
            if (null != item._res)
                _indexed.incrementAndGet();
        }
        else
        {
            _failed.incrementAndGet();
        }
        boolean empty;
        synchronized (_subtasks)
        {
            Object remove =  _subtasks.remove(item);
            assert null == remove || remove == item;
            empty = _subtasks.isEmpty();
        }
        if (!empty)
            return;

        synchronized (_completeEvent)
        {
            if (checkDone())
                fireDone();
        }
    }


    @Override
    public boolean isCancelled()
    {
        synchronized (_completeEvent)
        {
            return _cancelled;
        }
    }


    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        synchronized (_completeEvent)
        {
            _cancelled = true;
            _completeEvent.notify();
            return true;
        }
    }


    @Override
    public boolean isDone()
    {
        synchronized (_completeEvent)
        {
            return _complete != 0 || _cancelled;
        }
    }


    @Override
    public SearchService.IndexTask get() throws InterruptedException
    {
        synchronized (_completeEvent)
        {
            if (!isDone())
                _completeEvent.wait();
        }
        return this;
    }


    @Override
    public SearchService.IndexTask get(long timeout, TimeUnit unit) throws InterruptedException
    {
        synchronized (_completeEvent)
        {
            if (!isDone())
                _completeEvent.wait(unit.toMillis(timeout));
        }
        return this;
    }


    protected void fireDone()
    {
        assert Thread.holdsLock(_completeEvent);
        
        _complete = System.currentTimeMillis();
        _completeEvent.notifyAll();
        if (null != _listener && !isCancelled())
        {
            _listener.success();
        }
    }

    
    protected abstract boolean checkDone();
}