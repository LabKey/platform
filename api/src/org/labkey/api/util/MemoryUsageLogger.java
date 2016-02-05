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
package org.labkey.api.util;

import org.apache.log4j.Logger;
import org.labkey.api.settings.AppProps;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

/**
 * Logs information about the various JVM memory pools to the primary server log file. Useful for tracking memory
 * use trends without needing to have a separate monitoring solution in place.
 * User: jeckels
 * Date: 1/15/14
 */
public class MemoryUsageLogger implements Runnable
{
    private static final Logger LOG = Logger.getLogger(MemTracker.class);

    private static final Object LOGGER_LOCK_OBJECT = new Object();

    private static long _lastLog = 0;

    private boolean _showHeader;
    private int _requestNumber;

    public MemoryUsageLogger(boolean showHeader, int requestNumber)
    {
        _showHeader = showHeader;
        _requestNumber = requestNumber;
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
                    JobRunner.getDefault().execute(new MemoryUsageLogger(_lastLog == 0, requestNumber));
                    _lastLog = currentTime;
                }
            }
        }
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
            LOG.debug("\t******************************************************");
            LOG.debug("\t**************FIRST MEMORY LOGGING EVENT**************");
            LOG.debug("\t******************************************************");
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
            LOG.debug(sb.toString());
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
        LOG.debug(sb.toString());
    }
}
