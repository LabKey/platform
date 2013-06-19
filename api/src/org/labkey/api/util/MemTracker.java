/*
 * Copyright (c) 2005-2013 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.settings.AppProps;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: brittp
 * Date: Oct 27, 2005
 * Time: 10:33:00 AM
 */
public class MemTracker
{
    private static final MemTracker _instance = new MemTracker();
    private static final Logger _log = Logger.getLogger(MemTracker.class);
    private static final Object LOGGER_LOCK_OBJECT = new Object();

    private static long _lastLog = 0;

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


    public static synchronized boolean put(Object object)
    {
        assert _instance._put(object);
        return true;
    }

    public static synchronized boolean remove(Object object)
    {
        assert _instance._remove(object);
        return true;
    }

    // Work around Java 7 PriorityBlockingQueue bug, http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7161229
    public static void register(MemTrackerListener generator)
    {
        assert _instance._listeners.add(generator);
    }

    public static void unregister(MemTrackerListener queue)
    {
        assert _instance._listeners.remove(queue);
    }

    public static Set<Object> beforeReport()
    {
        Set<Object> ignorableReferences = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

        for (MemTrackerListener generator : _instance._listeners)
            generator.beforeReport(ignorableReferences);

        return ignorableReferences;
    }

    public static List<HeldReference> getReferences()
    {
        return _instance._getReferences();
    }

    private static class MemoryLogger implements Runnable
    {
        private boolean _showHeader;
        private int _requestNumber;
        
        public MemoryLogger(boolean showHeader, int requestNumber)
        {
            _showHeader = showHeader;
            _requestNumber = requestNumber;
        }

        public void run()
        {
            try
            {
                // Wait a little while so that we're less likely to cause a slowdown for the HTTP thread that initiated
                // this memory logging/GC event
                Thread.sleep(5000);
            }
            catch (InterruptedException ignored) {}
            if (_showHeader)
            {
                _log.debug("\t******************************************************");
                _log.debug("\t**************FIRST MEMORY LOGGING EVENT**************");
                _log.debug("\t******************************************************");
                StringBuilder sb = new StringBuilder("\tRequest Count");
                sb.append("\tHeap (Max=");
                sb.append(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax());
                sb.append(")\tNon Heap (Max=");
                sb.append(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getMax());
                sb.append(")");
                for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans())
                {
                    sb.append("\t");
                    sb.append(bean.getName());
                    sb.append(" (Max=");
                    sb.append(bean.getUsage().getMax());
                    sb.append(")");
                }
                _log.debug(sb.toString());
            }

            System.gc();
            StringBuilder sb = new StringBuilder();
            sb.append("\t");
            sb.append(_requestNumber);
            sb.append("\t");
            sb.append(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            sb.append("\t");
            sb.append(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());

            for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans())
            {
                sb.append("\t");
                sb.append(bean.getUsage().getUsed());
            }
            _log.debug(sb.toString());
        }
    }

    public static void logMemoryUsage(int requestNumber)
    {
        int interval = AppProps.getInstance().getMemoryUsageDumpInterval();
        long currentTime = System.currentTimeMillis();
        if (interval > 0)
        {
            synchronized(LOGGER_LOCK_OBJECT)
            {
                if (currentTime > _lastLog + interval * 60 * 1000)
                {
                    JobRunner.getDefault().execute(new MemoryLogger(_lastLog == 0, requestNumber));
                    _lastLog = currentTime;
                }
            }
        }
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
        return true;
    }

    private synchronized boolean _remove(Object object)
    {
        if (object != null)
            _references.remove(object);
        return true;
    }

    private synchronized List<HeldReference> _getReferences()
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


    public static class TestCase extends Assert
    {
        @Test
        public void testIdentity()
        {
            MemTracker t = new MemTracker();

            // test identity
            Object a = "I'm me";
            t._put(a);
            assertTrue(t._getReferences().size() == 1);
            t._put(a);
            assertTrue(t._getReferences().size() == 1);

            Object b = new Integer(1);
            Object c = new Integer(1);
            assertFalse(b == c);
            assertTrue(b.equals(c));
            t._put(b);
            assertTrue(t._getReferences().size() == 2);
            t._put(c);
            assertTrue(t._getReferences().size() == 3);

            List<HeldReference> list = t._getReferences();
            for (HeldReference o : list)
            {
                assertTrue(o._reference == a || o._reference == b || o._reference == c);
            }
        }
    }
}
