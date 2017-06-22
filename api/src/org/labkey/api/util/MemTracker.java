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

import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ReferenceIdentityMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Tracks objects that may be expensive, commonly allocated so that we know that they're not being held and creating
 * a memory leak. Will not prevent the tracked objects from being garbage collected.
 * User: brittp
 * Date: Oct 27, 2005
 */
public class MemTracker
{
    private static final MemTracker _instance = new MemTracker();

    private final ThreadLocal<RequestInfo> _requestTracker = new ThreadLocal<>();
    private final List<RequestInfo> _recentRequests = new LinkedList<>();

    private static final String UNVIEWED_KEY = "memtracker-unviewed-requests";
    private static final int MAX_UNVIEWED = 100;

    /** Only keep a short history of allocations for the most recent requests */
    private static final int MAX_TRACKED_REQUESTS = 500;

    public synchronized List<RequestInfo> getNewRequests(long requestId)
    {
        List<RequestInfo> result = new ArrayList<>(_recentRequests.size());
        result.addAll(_recentRequests.stream().filter(recentRequest -> recentRequest.getId() > requestId).collect(Collectors.toList()));
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

    public static MemTracker get()
    {
        return _instance;
    }

    public static MemTracker getInstance()
    {
        return _instance;
    }

    /**
     * Create new RequestInfo for the current thread and request.
     */
    @NotNull
    public RequestInfo startProfiler(HttpServletRequest request, @Nullable String name)
    {
        String url = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        return startProfiler(url, request.getUserPrincipal(), name);
    }

    /**
     * Create new RequestInfo for the current thread.
     * Used for profiling background requests that will be merged into a parent profiler.
     * @see #merge(RequestInfo)
     */
    @NotNull
    public RequestInfo startProfiler(@Nullable String name)
    {
        return startProfiler(null, null, name);
    }

    /**
     * Create new RequestInfo for the current thread and request.
     */
    @NotNull
    public synchronized RequestInfo startProfiler(String url, Principal user, @Nullable String name)
    {
        RequestInfo req = new RequestInfo(url, user, name);
        if ((user instanceof User) && ((User) user).isSearchUser())
            req.setIgnored(true);
        _requestTracker.set(req);
        return req;
    }

    @Nullable
    public RequestInfo current()
    {
        return _requestTracker.get();
    }

    /**
     * Finish the current profiling session and merge its results into the <code>to</code> RequestInfo.
     * Unlike <code>requestComplete</code>, the current timing will not be added to the list of recent requests.
     */
    public void merge(@NotNull RequestInfo to)
    {
        RequestInfo requestInfo = _requestTracker.get();
        if (requestInfo != null)
        {
            requestInfo.getRoot().stop();
            to.merge(requestInfo);
        }
        _requestTracker.remove();
    }

    /**
     * Mark the current profiling session as ignored.  Timings won't be collected.
     */
    public synchronized void ignore()
    {
        RequestInfo requestInfo = _requestTracker.get();
        if (requestInfo != null)
            requestInfo.setIgnored(true);
    }

    /**
     * Finish the current profiling session.
     */
    public synchronized void requestComplete(RequestInfo req)
    {
        RequestInfo requestInfo = _requestTracker.get();
        _requestTracker.remove();
        if (req != requestInfo)
            _complete(requestInfo);
        _complete(req);
    }

    private void _complete(RequestInfo requestInfo)
    {
        boolean shouldTrack = requestInfo != null && !requestInfo.isIgnored();
        if (requestInfo != null)
        {
            if (shouldTrack)
            {
                // Now that we're done, move it into the set of recent requests
                _recentRequests.add(requestInfo);
                trimOlderRequests();
                if (requestInfo.getUser() != null)
                    addUnviewed(requestInfo.getUser(), requestInfo.getId());
            }
            else
            {
                // Remove it from the list of unviewed requests
                if (requestInfo.getUser() != null)
                    setViewed(requestInfo.getUser(), requestInfo.getId());
            }
        }
    }

    private void trimOlderRequests()
    {
        if (_recentRequests.size() > MAX_TRACKED_REQUESTS)
        {
            List<RequestInfo> reqs = _recentRequests.subList(0, _recentRequests.size() - MAX_TRACKED_REQUESTS);
            for (RequestInfo r : reqs)
                r.cancel();
            reqs.clear();
        }
    }

    private void addUnviewed(Principal user, long id)
    {
        ViewContext context = HttpView.getRootContext();
        if (context == null)
            return;

        HttpSession session = context.getSession();
        if (session == null)
            return;

        synchronized (SessionHelper.getSessionLock(session))
        {
            List<Long> unviewed = (List<Long>)session.getAttribute(UNVIEWED_KEY);
            if (unviewed == null)
                session.setAttribute(UNVIEWED_KEY, unviewed = new ArrayList<>());
            unviewed.add(id);
            if (unviewed.size() > MAX_UNVIEWED)
            {
                unviewed.subList(0, unviewed.size() - MAX_UNVIEWED).clear();
            }
        }
    }

    public List<Long> getUnviewed(Principal user)
    {
        ViewContext context = HttpView.getRootContext();
        if (context == null)
            return Collections.emptyList();

        HttpSession session = context.getSession();
        if (session == null)
            return Collections.emptyList();

        synchronized (SessionHelper.getSessionLock(session))
        {
            List<Long> unviewed = (List<Long>)session.getAttribute(UNVIEWED_KEY);
            if (unviewed == null)
                session.setAttribute(UNVIEWED_KEY, unviewed = new ArrayList<>());

            return new ArrayList<>(unviewed);
        }
    }

    public void setViewed(Principal user, long id)
    {
        ViewContext context = HttpView.getRootContext();
        if (context == null)
            return;

        HttpSession session = context.getSession();
        if (session == null)
            return;

        synchronized (SessionHelper.getSessionLock(session))
        {
            List<Long> unviewed = (List<Long>)session.getAttribute(UNVIEWED_KEY);
            if (unviewed == null)
                session.setAttribute(UNVIEWED_KEY, unviewed = new ArrayList<>());

            unviewed.remove(id);
        }
    }

    @Nullable
    public synchronized RequestInfo getRequest(long id)
    {
        // search recent requests backwards looking for the matching id
        for (int i = _recentRequests.size() - 1; i > 0; i--)
        {
            RequestInfo req = _recentRequests.get(i);
            if (req.getId() == id)
                return req;
        }

        return null;
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

    private final Map<Object, AllocationInfo> _references = new ReferenceIdentityMap<>(ReferenceStrength.WEAK, ReferenceStrength.HARD, true);
    private final List<MemTrackerListener> _listeners = new CopyOnWriteArrayList<>();

    private synchronized boolean _put(Object object)
    {
        if (object != null)
            _references.put(object, new AllocationInfo());
        MiniProfiler.addObject(object);
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
        refs.sort(Comparator.comparing(HeldReference::getClassName, String.CASE_INSENSITIVE_ORDER));
        return refs;
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
