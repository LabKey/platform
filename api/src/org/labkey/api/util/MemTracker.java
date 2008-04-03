package org.labkey.api.util;

import org.apache.log4j.Logger;

import java.util.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

/**
 * User: brittp
 * Date: Oct 27, 2005
 * Time: 10:33:00 AM
 */
public class MemTracker
{
    private static final Logger _log = Logger.getLogger(MemTracker.class);

    private static long _lastLog = 0;
    private static final Object LOGGER_LOCK_OBJECT = new Object();

    static class AllocationInfo
    {
        AllocationInfo()
        {
            _exception = new Exception();
            _threadId = Thread.currentThread().getId();
            _allocTime = System.currentTimeMillis();
        }
        long _threadId;
        long _allocTime;
        Exception _exception;
    }

    public static class HeldReference
    {
        private Object  _reference;
        private long _threadId;
        private long _allocTime;
        private String _htmlStack;


        private HeldReference(Object held, AllocationInfo allocationInfo)
        {
            _reference = held;
            _threadId = allocationInfo._threadId;
            _allocTime = allocationInfo._allocTime;

            StackTraceElement[] stack = allocationInfo._exception.getStackTrace();
            StringBuilder builder = new StringBuilder();
            for (int i = 3; i < stack.length; i++)
            {
                String line = stack[i].toString();
                builder.append(PageFlowUtil.filter(line)).append("<br>\r\n");
                if (line.contains("org.labkey.api.view.ViewServlet.service"))
                    break;
            }
            _htmlStack = builder.toString();
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
                return "toString() failed: " + e.getClass().getName() + e.getMessage() == null ? "" : (" - " + e.getMessage());
            }
        }

        public String getHtmlStack()
        {
            return _htmlStack;
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

    private static Map<Object, AllocationInfo> _references = new WeakHashMap<Object, AllocationInfo>();

    public static synchronized boolean put(Object object)
    {
        assert putInternal(object);
        return true;
    }

    public static synchronized boolean remove(Object object)
    {
        assert removeInternal(object);
        return true;
    }

    private static synchronized boolean putInternal(Object object)
    {
        if (object != null)
            _references.put(object, new AllocationInfo());
        return true;
    }

    private static synchronized boolean removeInternal(Object object)
    {
        if (object != null)
            _references.remove(object);
        return true;
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

    private static long getMemoryUsed(String poolName)
    {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools)
        {
            if (poolName.equals(pool.getName()))
            {
                return pool.getUsage().getUsed();
            }
        }
        return -1;
    }

    public static synchronized List<HeldReference> getReferences()
    {
        List<HeldReference> refs = new ArrayList<HeldReference>(_references.size());
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
}
