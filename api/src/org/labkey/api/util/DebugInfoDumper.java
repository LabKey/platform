/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.DbScope;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monitors the timestamp of the heapDumpRequest and threadDumpRequest files to see if an admin has requested
 * them via the file system.
 *
 * Writes thread dumps via Log4J, and heap dumps to a .hprof file.
 *
 * Created by: jeckels
 * Date: 1/16/16
 */
public class DebugInfoDumper
{
    private static final Logger LOG = LogManager.getLogger(DebugInfoDumper.class);

    private final File _threadDumpFile;
    private final File _heapDumpFile;

    private long _threadDumpLastModified;
    private long _heapDumpLastModified;

    private static final int THREAD_DUMP_INTERVAL = 10000;

    public DebugInfoDumper(File modulesDir)
    {
        // Ensure there's a thread dump request file
        File labkeyRoot = modulesDir.getParentFile();
        _threadDumpFile = new File(labkeyRoot, "threadDumpRequest");
        if (!_threadDumpFile.exists())
        {
            try (PrintWriter writer = PrintWriters.getPrintWriter(_threadDumpFile))
            {
                writer.println("Touch this file while LabKey Server is running and within 10 seconds the server will");
                writer.println("dump all of its threads to its standard log file, including database connection SPIDs");
                writer.flush();
            }
            catch (IOException e)
            {
                LOG.error("Failed to create file " + _threadDumpFile.getAbsolutePath(), e);
            }
        }

        // Ensure there's a heap dump request file
        _heapDumpFile = new File(labkeyRoot, "heapDumpRequest");
        if (!_heapDumpFile.exists())
        {
            try (PrintWriter writer = PrintWriters.getPrintWriter(_heapDumpFile))
            {
                writer.println("Touch this file while LabKey Server is running and within 10 seconds the server will");
                writer.println("dump its heap to disk. The log file will contain information on where the dump was written,");
                writer.println("but in most cases it will be this directory.");
                writer.flush();
            }
            catch (IOException e)
            {
                LOG.error("Failed to create file " + _heapDumpFile.getAbsolutePath(), e);
            }
        }

        _threadDumpLastModified = _threadDumpFile.lastModified();
        _heapDumpLastModified = _heapDumpFile.lastModified();

        try
        {
            //noinspection unchecked
            FileSystemWatchers.get().addListener(labkeyRoot.toPath(), new FileSystemDirectoryListener()
            {
                @Override
                public void entryCreated(Path directory, Path entry) {}

                @Override
                public void entryDeleted(Path directory, Path entry) {}

                @Override
                public void entryModified(Path directory, Path entry)
                {
                    checkForRequests();
                }

                @Override
                public void overflow()
                {
                    checkForRequests();
                }
            }, StandardWatchEventKinds.ENTRY_MODIFY);
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    private void checkForRequests()
    {
        long threadDumpModified = _threadDumpFile.lastModified();
        boolean threadDumpRequested = threadDumpModified != _threadDumpLastModified;
        if (threadDumpRequested)
        {
            dumpThreads(3);
            _threadDumpLastModified = threadDumpModified;
        }

        long heapDumpModified = _heapDumpFile.lastModified();
        boolean heapDumpRequested = heapDumpModified != _heapDumpLastModified;
        if (heapDumpRequested)
        {
            try
            {
                File destination = dumpHeap();
                LOG.info("Dumped server heap to " + destination);
            }
            catch (Exception e)
            {
                LOG.error("Failed to dump heap", e);
            }
            _heapDumpLastModified = heapDumpModified;
        }
    }

    /**
     * Writes the requested number of thread dumps, separated by 10 seconds, to the primary log file. All requests
     * beyond the first are written asynchronously.
     */
    public static void dumpThreads(final int totalCount)
    {
        if (totalCount <= 0)
        {
            throw new IllegalArgumentException("Count must be greater than 0, but was " + totalCount);
        }
        dumpThreads(LOG);
        if (totalCount > 1)
        {
            Thread t = new Thread(() -> {
                try
                {
                    int remainingCount = totalCount - 1;
                    while (remainingCount > 0)
                    {
                        Thread.sleep(THREAD_DUMP_INTERVAL);
                        dumpThreads(LOG);
                        remainingCount--;
                    }
                }
                catch (InterruptedException ignored)
                {
                }
            }, "Thread dump thread");
            t.setDaemon(true);
            t.start();
        }
    }

    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

    public static File dumpHeap() throws Exception
    {
        // Use reflection so that we don't have to build directly against the com.sun. class
        Class<?> hotspotClass = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
        Object bean = ManagementFactory.newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(), HOTSPOT_BEAN_NAME, hotspotClass);
        Method method = hotspotClass.getMethod("dumpHeap", String.class, boolean.class);

        File destDir = ModuleLoader.getInstance().getWebappDir().getParentFile();

        //defer to -XX:HeapDumpPath
        String prefix = "-XX:HeapDumpPath=";
        for (final String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments())
        {
            if (jvmArg.startsWith(prefix))
            {
                destDir = new File(jvmArg.substring(prefix.length()));
                break;
            }
        }

        File destination = new File(destDir, "HeapDump_" + DateUtil.formatDateTime(new Date(), "yyyy-MM-dd_HH-mm-ss-SSS") + ".hprof");
        try
        {
            method.invoke(bean, destination.getAbsolutePath(), false);
        }
        catch (InvocationTargetException e)
        {
            if (e.getTargetException() instanceof IOException)
            {
                // Rethrow a with a more helpful error message
                throw new IOException("Failed to write heap dump to " + destination + ", possibly due to a permissions issue", e.getTargetException());
            }
            throw e;
        }
        return destination;
    }

    /**
     * Writes the thread dump into threads.txt
     * */
    public static synchronized void dumpThreads(LoggerWriter logWriter)
    {
        logWriter.debug("*********************************************");
        logWriter.debug("Starting thread dump - " + LocalDateTime.now());
        long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long max = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        logWriter.debug("Heap usage at " + DecimalFormat.getPercentInstance().format(((double)used / (double)max)) + " - " +
                FileUtils.byteCountToDisplaySize(used) + " from a max of " +
                FileUtils.byteCountToDisplaySize(max) + " (" + DecimalFormat.getInstance().format(used) + " / " + DecimalFormat.getInstance().format(max) + " bytes)");
        logWriter.debug("*********************************************");
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        List<Thread> threads = new ArrayList<>(stackTraces.keySet());
        threads.sort(Comparator.comparing(Thread::getName, String.CASE_INSENSITIVE_ORDER));

        for (Thread thread : threads)
        {
            logWriter.debug("");
            StringBuilder threadInfo = new StringBuilder(thread.getName());
            threadInfo.append(" (");
            threadInfo.append(thread.getState());
            threadInfo.append("), daemon: ");
            threadInfo.append(thread.isDaemon());
            Set<Integer> spids = ConnectionWrapper.getSPIDsForThread(thread);
            if (!spids.isEmpty())
            {
                threadInfo.append(", Database Connection SPIDs = ");
                threadInfo.append(spids);
            }
            logWriter.debug(threadInfo.toString());
            ViewServlet.RequestSummary uri = ViewServlet.getRequestSummary(thread);
            if (null != uri)
                logWriter.debug(uri.toString());
            for (StackTraceElement stackTraceElement : stackTraces.get(thread))
            {
                logWriter.debug("\t" + stackTraceElement.toString());
            }

            if (ConnectionWrapper.getProbableLeakCount() > 0)
            {
                ConnectionWrapper.dumpLeaksForThread(thread, logWriter);
            }
        }

        logWriter.debug("*********************************************");
        logWriter.debug("Completed thread dump");
        logWriter.debug("*********************************************");

        for (DbScope dbScope : DbScope.getDbScopes())
        {
            dbScope.logCurrentConnectionState(logWriter);
        }

        if (ConnectionWrapper.getActiveConnectionCount() > 0)
        {
            logWriter.debug("*********************************************");
            logWriter.debug("Start dump of all open connections");
            logWriter.debug("*********************************************");
            ConnectionWrapper.dumpOpenConnections(logWriter);
            logWriter.debug("*********************************************");
            logWriter.debug("Completed dump of all open connections");
            logWriter.debug("*********************************************");
        }
    }

    /**
     * Writes the current set of thread stacks once to the supplied logger.
     */
    public static synchronized void dumpThreads(Logger log)
    {
        dumpThreads(new SimpleLoggerWriter(log));
    }
}
