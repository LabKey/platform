/*
 * Copyright (c) 2005-2014 LabKey Corporation
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

import org.apache.commons.collections15.map.AbstractReferenceMap;
import org.apache.commons.collections15.map.ReferenceIdentityMap;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.IgnoresAllocationTracking;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: brittp
 * Date: Oct 27, 2005
 * Time: 10:33:00 AM
 */
public class MemTracker
{
    private static final MemTracker _instance = new MemTracker();

    private final ThreadLocal<RequestInfo> _requestTracker = new ThreadLocal<>();
    private final List<RequestInfo> _recentRequests = new LinkedList<>();

    /** Only keep a short history of allocations for the most recent requests */
    private static final int MAX_TRACKED_REQUESTS = 500;

    public synchronized List<RequestInfo> getNewRequests(int requestId)
    {
        List<RequestInfo> result = new ArrayList<>(_recentRequests.size());
        for (RequestInfo recentRequest : _recentRequests)
        {
            if (recentRequest.getId() > requestId)
            {
                result.add(recentRequest);
            }
        }
        return Collections.unmodifiableList(result);
    }

    static class AllocationInfo
    {
        private final StackTraceElement[] _stackTrace;
        private final long _threadId;
        private final long _allocTime;

        AllocationInfo()
        {
            this(Thread.currentThread().getStackTrace(), Thread.currentThread().getId(), HeartBeat.currentTimeMillis());
        }

        AllocationInfo(StackTraceElement[] stackTrace, long threadId, long allocTime)
        {
            _stackTrace = stackTrace;
            _threadId = threadId;
            _allocTime = allocTime;
        }

        public String getHtmlStack()
        {
            StringBuilder builder = new StringBuilder();
            for (int i = 3; i < _stackTrace.length; i++)
            {
                String line = _stackTrace[i].toString();
                builder.append(PageFlowUtil.filter(line)).append("<br>\r\n");
                if (line.contains("org.labkey.api.view.ViewServlet.service"))
                    break;
            }
            return builder.toString();
        }

        public long getThreadId()
        {
            return _threadId;
        }

        public long getAllocationTime()
        {
            return _allocTime;
        }
    }


    public static class HeldReference extends AllocationInfo
    {
        private Object _reference;

        private HeldReference(Object held, AllocationInfo allocationInfo)
        {
            super(allocationInfo._stackTrace, allocationInfo._threadId, allocationInfo._allocTime);
            _reference = held;
        }
        

        public String getClassName()
        {
            if (_reference instanceof Class)
                return ((Class) _reference).getName();
            else
                return _reference.getClass().getName();
        }

        public String getObjectSummary()
        {
            String desc = getObjectDescription();
            return desc.length() > 50 ? desc.substring(0, 50) + "..." : desc;
        }

        public boolean hasShortSummary()
        {
            return getObjectDescription().length() > 50;
        }

        public String getObjectDescription()
        {
            try
            {
                String toString = _reference.toString();
                if (toString == null)
                    return "null";
                return toString;
            }
            catch (Throwable e)
            {
                return "toString() failed: " + e.getClass().getName() + (e.getMessage() == null ? "" : (" - " + e.getMessage()));
            }
        }

        public Object getReference()
        {
            return _reference;
        }
    }

    public static MemTracker getInstance()
    {
        return _instance;
    }

    public synchronized void startNewRequest(HttpServletRequest request)
    {
        _requestTracker.set(new RequestInfo(request));
    }

    public synchronized void requestComplete(HttpServletRequest request)
    {
        boolean shouldTrack = !Boolean.TRUE.equals(request.getAttribute(IgnoresAllocationTracking.class.getName()));
        if (shouldTrack)
        {
            RequestInfo requestInfo = _requestTracker.get();
            if (requestInfo != null)
            {
                // Now that we're done, move it into the set of recent requests
                _recentRequests.add(requestInfo);
                trimOlderRequests();
            }
        }
        _requestTracker.remove();
    }

    private void trimOlderRequests()
    {
        while (_recentRequests.size() > MAX_TRACKED_REQUESTS)
        {
            _recentRequests.remove(0);
        }
    }

    public boolean put(Object object)
    {
        assert _put(object);
        return true;
    }

    public boolean remove(Object object)
    {
        assert _remove(object);
        return true;
    }

    // Work around Java 7 PriorityBlockingQueue bug, http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7161229
    public void register(MemTrackerListener generator)
    {
        assert _listeners.add(generator);
    }

    public void unregister(MemTrackerListener queue)
    {
        assert _listeners.remove(queue);
    }

    public Set<Object> beforeReport()
    {
        Set<Object> ignorableReferences = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

        for (MemTrackerListener generator : _instance._listeners)
            generator.beforeReport(ignorableReferences);

        return ignorableReferences;
    }

    //
    // reference tracking impl
    //

    private final Map<Object, AllocationInfo> _references = new ReferenceIdentityMap<>(AbstractReferenceMap.WEAK, AbstractReferenceMap.HARD, true);
    private final List<MemTrackerListener> _listeners = new CopyOnWriteArrayList<>();

    private synchronized boolean _put(Object object)
    {
        if (object != null)
            _references.put(object, new AllocationInfo());
        RequestInfo requestInfo = _requestTracker.get();
        if (requestInfo != null)
        {
            requestInfo.addObject(object);
        }
        return true;
    }

    private synchronized boolean _remove(Object object)
    {
        if (object != null)
            _references.remove(object);
        return true;
    }

    public synchronized List<HeldReference> getReferences()
    {
        List<HeldReference> refs = new ArrayList<>(_references.size());
        for (Map.Entry<Object, AllocationInfo> entry : _references.entrySet())
        {
            // get a hard reference so we know that we're placing an actual object into our list:
            Object obj = entry.getKey();
            if (obj != null)
                refs.add(new HeldReference(entry.getKey(), entry.getValue()));
        }
        Collections.sort(refs, new Comparator<HeldReference>()
        {
            public int compare(HeldReference o1, HeldReference o2)
            {
                String class1 = o1.getClassName();
                String class2 = o2.getClassName();
                return class1.compareToIgnoreCase(class2);
            }
        });
        return refs;
    }

    public static class RequestInfo
    {
        private static final AtomicLong NEXT_ID = new AtomicLong(0);

        private final long _id = NEXT_ID.incrementAndGet();
        private final String _url;
        private final Date _date = new Date();
        private Map<String, Integer> _objects = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        public RequestInfo(HttpServletRequest request)
        {
            _url = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        }

        public void addObject(Object object)
        {
            if (object != null)
            {
                String s;
                if (object instanceof MemTrackable)
                {
                    s = ((MemTrackable)object).toMemTrackerString();
                }
                else
                {
                    s = object.getClass().getName();
                }
                Integer count = _objects.get(s);
                _objects.put(s, count == null ? 1 : count.intValue() + 1);
            }
        }

        public long getId()
        {
            return _id;
        }

        public String getUrl()
        {
            return _url;
        }

        public Date getDate()
        {
            return _date;
        }

        public Map<String, Integer> getObjects()
        {
            return Collections.unmodifiableMap(_objects);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testIdentity()
        {
            MemTracker t = new MemTracker();

            // test identity
            Object a = "I'm me";
            t._put(a);
            assertTrue(t.getReferences().size() == 1);
            t._put(a);
            assertTrue(t.getReferences().size() == 1);

            Object b = new Integer(1);
            Object c = new Integer(1);
            assertFalse(b == c);
            assertTrue(b.equals(c));
            t._put(b);
            assertTrue(t.getReferences().size() == 2);
            t._put(c);
            assertTrue(t.getReferences().size() == 3);

            List<HeldReference> list = t.getReferences();
            for (HeldReference o : list)
            {
                assertTrue(o._reference == a || o._reference == b || o._reference == c);
            }
        }
    }
}
