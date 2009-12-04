/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.search.SearchService;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;

public abstract class AbstractIndexTask implements SearchService.IndexTask
{
    final private String _description;
    protected long _start;
    protected long _complete = 0;
    protected boolean _cancelled = false;
    protected boolean _isReady = false;
    final private AtomicInteger _estimate = new AtomicInteger();
    final private AtomicInteger _indexed = new AtomicInteger();
    final private AtomicInteger _failed = new AtomicInteger();
    final protected Map<Object,Object> _subtasks = Collections.synchronizedMap(new IdentityHashMap<Object,Object>());
    final StringWriter _sw = new StringWriter();
    final PrintWriter _out = new PrintWriter(_sw);


    public AbstractIndexTask(String description)
    {
        _description = description;
        _start = System.currentTimeMillis();
    }


    public String getDescription()
    {
        return _description;
    }


    public void cancel()
    {
        _cancelled = true;
        _complete = System.currentTimeMillis();
    }


    public boolean isCancelled()
    {
        return _cancelled;
    }


    public int getDocumentCountEstimate()
    {
        return _estimate.get();
    }


    public int getIndexedCount()
    {
        return _indexed.get();
    }


    public int getFailedCount()
    {
        return _failed.get();
    }


    public long getStartTime()
    {
        return _start;
    }


    public long getCompleteTime()
    {
        return _complete;
    }


    protected void addItem(Object item)
    {
        _subtasks.put(item,item);
    }


    public void log(String message)
    {
        synchronized (_sw)
        {
            _out.println(message);
        }
    }


    public Reader getLog()
    {
        synchronized (_sw)
        {
            return new StringReader(_sw.getBuffer().toString());
        }
    }


    public void addToEstimate(int i)
    {
        _estimate.addAndGet(i);
    }


    // indicates that caller is done adding Resources to this task
    public void setReady()
    {
        _isReady = true;
        checkDone();
    }


    protected void completeItem(Object item, boolean success)
    {
        if (_cancelled)
            return;
        if (success)
            _indexed.incrementAndGet();
        else
            _failed.incrementAndGet();
        Object remove =  _subtasks.remove(item);
        assert null != remove;
        assert remove == item;
        checkDone();
    }


    //
    // add items to index
    //

    protected abstract void checkDone();
}