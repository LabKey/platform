/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.data.ConnectionWrapper;

import javax.servlet.ServletContextEvent;
import java.io.*;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Oct 27, 2006
 */
public class BreakpointThread extends Thread implements ShutdownListener
{
    private boolean _shutdown = false;

    private static final Logger _log = Logger.getLogger(BreakpointThread.class);
    private final File _modulesDir;

    public BreakpointThread(File modulesDir)
    {
        setDaemon(true);
        setName("BreakpointThread");
        ContextListener.addShutdownListener(this);
        _modulesDir = modulesDir;
    }

    public void run()
    {
        File labkeyRoot = _modulesDir.getParentFile();
        File requestFile = new File(labkeyRoot, "threadDumpRequest");
        if (!requestFile.exists())
        {
            FileOutputStream fOut = null;
            try
            {
                fOut = new FileOutputStream(requestFile);
                PrintWriter writer = new PrintWriter(fOut);
                writer.println("Touch this file while LabKey Server is running and within 10 seconds the server will");
                writer.println("dump all of its threads to its standard log file, including database connection SPIDs");
                writer.flush();
            }
            catch (IOException e)
            {
                _log.error("Failed to create file " + requestFile.getAbsolutePath(), e);
            }
            finally
            {
                if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
            }
        }

        long lastModified = requestFile.lastModified();
        while (!_shutdown)
        {
            try
            {
                Thread.sleep(10000);
                long modified = requestFile.lastModified();
                boolean threadDumpRequested = modified != lastModified;
                if (threadDumpRequested)
                {
                    dumpThreads();
                }
                lastModified = modified;
            }
            catch (InterruptedException e) {}
        }
    }

    private void dumpThreads()
    {
        Map<Thread,StackTraceElement[]> threads = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> threadEntry : threads.entrySet())
        {
            _log.debug("");
            Thread thread = threadEntry.getKey();
            StringBuilder threadInfo = new StringBuilder(thread.getName());
            threadInfo.append(" (");
            threadInfo.append(thread.getState());
            threadInfo.append(")");
            Set<Integer> spids = ConnectionWrapper.getSPIDsForThread(thread);
            if (!spids.isEmpty())
            {
                threadInfo.append(", Database Connection SPIDs = ");
                threadInfo.append(spids);
            }

            _log.debug(threadInfo);
            for (StackTraceElement stackTraceElement : threadEntry.getValue())
            {
                _log.debug("\t" + stackTraceElement.toString());
            }
        }
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        _shutdown = true;
        interrupt();
        try
        {
            join(2000);
        }
        catch (InterruptedException e) {}
    }
}
