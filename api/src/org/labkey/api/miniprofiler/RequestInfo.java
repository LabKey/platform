/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.miniprofiler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.MemTrackable;
import org.labkey.api.util.MemTracker;

import java.security.Principal;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures basic profiling data for a single HTTP request.
 * User: kevink
 */
@JsonPropertyOrder({"id", "url", "date", "duration", "root", "objects"})
public class RequestInfo implements AutoCloseable
{
    private static final AtomicLong NEXT_ID = new AtomicLong(0);

    private final long _id = NEXT_ID.incrementAndGet(); // CONSIDER: use guid instead
    private final String _url;
    private final Date _date = new Date();
    private final Timing _root;
    private final Map<String, Integer> _objects = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // The name field is set after RequestInfo is created -- as soon as the REQUEST_CONTROLLER and REQUEST_ACTION attributes are set.
    private String _name;

    // User that initiated the request.
    private Principal _user;

    // The ignore flag may be set after profiling starts.  Timings won't be collected and will be marked as already viewed when the request is complete.
    private boolean _ignored = false;

    // The current Timing instance will collect child Timing, CustomTiming, and object allocations.
    // Will be null when the request is complete or cancelled.
    /*package*/ Timing _current;

    public RequestInfo(@Nullable String url, @Nullable Principal user, @Nullable String name)
    {
        _url = url;
        _current = _root = new Timing(this, null, name == null ? "root" : name);
        _name = name;
        _user = user;
    }

    @Override
    public void close()
    {
        MemTracker.getInstance().requestComplete(this);
        _root.close();
        assert _current == null;
    }

    public void cancel()
    {
        _ignored = true;
        _root.close();
        assert _current == null;
    }

    protected void addObject(Object object)
    {
        if (!_ignored && _current != null && object != null)
        {
            String s;
            if (object instanceof MemTrackable)
                s = ((MemTrackable)object).toMemTrackerString();
            else
                s = object.getClass().getName();
            Integer count = _objects.get(s);
            _objects.put(s, count == null ? 1 : count.intValue() + 1);
            _current.addObject(s);
        }
    }

    /** Create new Timing step and set it as the current step. */
    protected Timing step(String name)
    {
        return new Timing(this, _current, name);
    }

    /** Create new timed CustomTiming and add it to the current Timing step. */
    protected CustomTiming custom(String category, String msg)
    {
        if (!_ignored && _current != null)
        {
            CustomTiming custom = new CustomTiming(_current, category, msg, null);
            _current.addCustomTiming(category, custom);
            return custom;
        }
        else
        {
            return null;
        }
    }

    /** Create a completed CustomTiming and add it to the current Timing step. */
    protected void addCustomTiming(String category, long duration, String message, @Nullable String detailsUrl, @Nullable StackTraceElement[] stackTrace)
    {
        if (!_ignored && _current != null)
        {
            CustomTiming custom = new CustomTiming(_current, category, duration, message, detailsUrl, stackTrace);
            _current.addCustomTiming(category, custom);
        }
    }

    /**
     * Merges timings from the other RequestInfo into the current step, allowing other threads, remote calls
     * to be profiled and joined into the current session.
     */
    public void merge(RequestInfo other)
    {
        if (_ignored || _current == null || other == null)
            return;
        _current.addChild(other.getRoot());
    }

    public long getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getUrl()
    {
        return _url;
    }

    public boolean isIgnored()
    {
        return _ignored;
    }

    /**
     * Mark the current profiling session as ignored.  Timings won't be collected.
     */
    public void setIgnored(boolean ignored)
    {
        _ignored = ignored;
    }

    public Date getDate()
    {
        return _date;
    }

    @JsonIgnore
    public Principal getUser()
    {
        return _user;
    }

    public Map<String, Integer> getObjects()
    {
        return Collections.unmodifiableMap(_objects);
    }

    public long getDuration()
    {
        return _root.getDuration();
    }

    public Timing getRoot()
    {
        return _root;
    }

}
