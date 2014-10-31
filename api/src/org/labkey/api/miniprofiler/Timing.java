/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
* User: kevink
* Date: 9/26/14
*/
@JsonPropertyOrder({"name", "id", "duration", "durationExclusive", "children"})
public class Timing implements AutoCloseable
{
    private final GUID _id;
    private final RequestInfo _req;
    private final Timing _parent;
    private final String _name;

    private final CPUTimer _timer;
    private final long _startOffset;
    private long _duration;

    private final Map<String, Integer> _objects = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private ArrayList<Timing> _children;

    private Map<String, List<CustomTiming>> _customTimings;

    public Timing(RequestInfo req, Timing parent, String name)
    {
        _id = new GUID();
        _req = req;
        _req._current = this;

        _parent = parent;
        _name = name;

        if (parent != null)
        {
            parent.addChild(this);
            _startOffset = System.currentTimeMillis() - req.getDate().getTime();
        }
        else
        {
            // root offset is 0
            _startOffset = 0L;
        }

        _timer = new CPUTimer(name);
        _timer.start();
    }

    public String getName()
    {
        return _name;
    }

    public GUID getId()
    {
        return _id;
    }

    /* package */ RequestInfo getRequest()
    {
        return _req;
    }

    @Override
    public void close()
    {
        stop();
    }

    public void stop()
    {
        if (_timer.started())
        {
            _timer.stop();
            _duration = _timer.getTotalMilliseconds();
            _req._current = _parent;
        }
    }

    protected void addChild(Timing timing)
    {
        assert timing._parent == this;
        if (_children == null)
            _children = new ArrayList<>();

        _children.add(timing);
    }

    protected void addCustomTiming(String category, CustomTiming custom)
    {
        assert custom._parent == this;
        if (_customTimings == null)
            _customTimings = new HashMap<>();
        List<CustomTiming> timings = _customTimings.get(category);
        if (timings == null)
            _customTimings.put(category, timings = new ArrayList<>());

        timings.add(custom);
    }

    public Map<String, List<CustomTiming>> getCustomTimings()
    {
        if (_customTimings == null)
            return null;
        return Collections.unmodifiableMap(_customTimings);
    }

    public long getStartOffset()
    {
        return _startOffset;
    }

    public long getDuration()
    {
        return _duration;
    }

    public long getDurationExclusive()
    {
        long duration = getDuration();
        if (_children != null)
        {
            for (Timing child : _children)
                duration -= child.getDuration();
        }
        return duration;
    }

    @JsonIgnore
    public boolean isTrivial()
    {
        return getDurationExclusive() <= MiniProfiler.getSettings().getTrivialMillis();
    }

    @JsonIgnore
    public boolean isRoot()
    {
        return _req.getRoot() == this;
    }

    @JsonIgnore
    public int getDepth()
    {
        int depth = 0;
        Timing parent = _parent;

        while (parent != null)
        {
            depth++;
            parent = parent._parent;
        }

        return depth;
    }

    public List<Timing> getChildren()
    {
        if (_children == null)
            return null;
        return Collections.unmodifiableList(_children);
    }

    protected void addObject(String s)
    {
        Integer count = _objects.get(s);
        _objects.put(s, count == null ? 1 : count.intValue() + 1);
    }

    public Map<String, Integer> getObjects()
    {
        if (_objects.isEmpty())
            return null;
        return Collections.unmodifiableMap(_objects);
    }
}
