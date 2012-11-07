/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.module.ModuleLoader;

import javax.servlet.ServletContextEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

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
        File threadDumpFile = new File(labkeyRoot, "threadDumpRequest");
        if (!threadDumpFile.exists())
        {
            FileOutputStream fOut = null;
            try
            {
                fOut = new FileOutputStream(threadDumpFile);
                PrintWriter writer = new PrintWriter(fOut);
                writer.println("Touch this file while LabKey Server is running and within 10 seconds the server will");
                writer.println("dump all of its threads to its standard log file, including database connection SPIDs");
                writer.flush();
            }
            catch (IOException e)
            {
                _log.error("Failed to create file " + threadDumpFile.getAbsolutePath(), e);
            }
            finally
            {
                if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
            }
        }

        File heapDumpFile = new File(labkeyRoot, "heapDumpRequest");
        if (!heapDumpFile.exists())
        {
            FileOutputStream fOut = null;
            try
            {
                fOut = new FileOutputStream(heapDumpFile);
                PrintWriter writer = new PrintWriter(fOut);
                writer.println("Touch this file while LabKey Server is running and within 10 seconds the server will");
                writer.println("dump its heap to disk. The log file will contain information on where the dump was written,");
                writer.println("but in most cases it will be this directory.");
                writer.flush();
            }
            catch (IOException e)
            {
                _log.error("Failed to create file " + heapDumpFile.getAbsolutePath(), e);
            }
            finally
            {
                if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
            }
        }

        long threadDumpLastModified = threadDumpFile.lastModified();
        long heapDumpLastModified = heapDumpFile.lastModified();
        while (!_shutdown)
        {
            try
            {
                Thread.sleep(10000);
                long threadDumpModified = threadDumpFile.lastModified();
                boolean threadDumpRequested = threadDumpModified != threadDumpLastModified;
                if (threadDumpRequested)
                {
                    dumpThreads();
                }
                threadDumpLastModified = threadDumpModified;

                long heapDumpModified = heapDumpFile.lastModified();
                boolean heapDumpRequested = heapDumpModified != heapDumpLastModified;
                if (heapDumpRequested)
                {
                    try
                    {
                        File destination = dumpHeap();
                        _log.error("Dumped server heap to " + destination);
                    }
                    catch (Exception e)
                    {
                        _log.error("Failed to dump heap", e);
                    }
                }
                heapDumpLastModified = heapDumpModified;
            }
            catch (InterruptedException e) {}
        }
    }


    private void dumpThreads()
    {
        dumpThreads(_log);
    }


    public static void dumpThreads(Logger log)
    {
        Map<Thread,StackTraceElement[]> threads = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> threadEntry : threads.entrySet())
        {
            log.debug("");
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

            log.debug(threadInfo);
            for (StackTraceElement stackTraceElement : threadEntry.getValue())
            {
                log.debug("\t" + stackTraceElement.toString());
            }
        }
    }


    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
        _shutdown = true;
        interrupt();
    }

    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        try
        {
            join(2000);
        }
        catch (InterruptedException e) {}
    }

    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

    public static File dumpHeap() throws Exception
    {
        // Use reflection so that we don't have to build directly against the com.sun. class
        Class hotspotClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
        Object bean = ManagementFactory.newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(), HOTSPOT_BEAN_NAME, hotspotClass);
        Method method = hotspotClass.getMethod("dumpHeap", String.class, boolean.class);
        File destination = new File(ModuleLoader.getInstance().getWebappDir().getParentFile(), "HeapDump_" + DateUtil.formatDateTime(new Date(), "yyyy-MM-dd_HH-mm-ss-SSS") + ".hprof");
        try
        {
            method.invoke(bean, destination.getAbsolutePath(), false);
        }
        catch (InvocationTargetException e)
        {
            if (e.getTargetException() instanceof IOException)
            {
                // Rethrow a with a more helpful error message
                throw new IOException("Failed to write heap dump to " + destination, e.getTargetException());
            }
            throw e;
        }
        return destination;
    }
}
