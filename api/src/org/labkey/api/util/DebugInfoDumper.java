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

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.TransactionFilter;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.PrintWriters;
import org.labkey.vfs.FileLike;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Monitors the timestamp of the heapDumpRequest and threadDumpRequest files to see if an admin has requested
 * them via the file system. Writes thread dumps via Log4J, and heap dumps to a .hprof file.
 */
public class DebugInfoDumper
{
    private static final Logger LOG = LogHelper.getLogger(DebugInfoDumper.class, "Thread and heap dump errors");

    private final File _threadDumpFile;
    private final File _heapDumpFile;

    private long _threadDumpLastModified;
    private long _heapDumpLastModified;

    private static final int THREAD_DUMP_INTERVAL = 10000;

    public DebugInfoDumper(FileLike labkeyRoot)
    {
        // Ensure there's a thread dump request file
        _threadDumpFile = FileUtil.toFileForWrite(labkeyRoot.resolveChild("threadDumpRequest"));
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
                LOG.error("Failed to create file {}", _threadDumpFile.getAbsolutePath(), e);
            }
            _threadDumpFile.deleteOnExit();
        }

        // Ensure there's a heap dump request file
        _heapDumpFile = FileUtil.toFileForWrite(labkeyRoot.resolveChild("heapDumpRequest"));
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
                LOG.error("Failed to create file {}", _heapDumpFile.getAbsolutePath(), e);
            }
            _heapDumpFile.deleteOnExit();
        }

        _threadDumpLastModified = _threadDumpFile.lastModified();
        _heapDumpLastModified = _heapDumpFile.lastModified();

        try
        {
            //noinspection unchecked
            FileSystemWatchers.get().addListener(FileUtil.toFileForRead(labkeyRoot).toPath(), new FileSystemDirectoryListener()
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


    record ThreadExtraContext(String context, StackTraceElement[] stack, long startTime) {}

    /* Can't use class ThreadLocal, which is frustrating */
    static final Map<Thread,List<ThreadExtraContext>> _threadDumpExtraContext = Collections.synchronizedMap(new WeakHashMap<>());


    /**
     * push a String onto a stack in ThreadLocal storage for adding context to a thread dump
     * <br>
     * This is primarily intended to help understand deadlocks.  Developers are encouraged to
     * add information related to attaining locks or starting transactions.
     */
    public static _PopAutoCloseable pushThreadDumpContext(String context)
    {
        final var arr = _threadDumpExtraContext.computeIfAbsent(Thread.currentThread(), (_) -> Collections.synchronizedList(new ArrayList<>()));
        int size = arr.size();
        arr.add(new ThreadExtraContext(context, MiniProfiler.getTroubleshootingStackTrace(), System.currentTimeMillis()));
        return new _PopAutoCloseable(size);
    }

    public static void resetThreadDumpContext()
    {
        final var arr = _threadDumpExtraContext.get(Thread.currentThread());
        if (null != arr)
            arr.clear();
    }


    public static class _PopAutoCloseable implements AutoCloseable
    {
        final int _size;

        _PopAutoCloseable(int size)
        {
            _size = size;
        }

        @Override
        public void close()
        {
            var arr = _threadDumpExtraContext.get(Thread.currentThread());
            assert null != arr;
            while (arr.size() > _size)
                arr.remove(arr.size() - 1);
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
                LOG.info("Dumped server heap to {}", destination);
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

        File destDir = ModuleLoader.getInstance().getWebappDir().toNioPathForRead().toFile().getParentFile();

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

        File destination = FileUtil.appendName(destDir, "HeapDump_" + DateUtil.formatDateTime(new Date(), "yyyy-MM-dd_HH-mm-ss-SSS") + ".hprof");
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

    static boolean justWaiting(StackTraceElement[] stack)
    {
        if (stack.length > 20)
            return false;
        if (stack.length < 1)
            return true;

        var top = stack[0];
        var topMethod = top.getMethodName();
        var bottom = stack[stack.length-1];
        var isLabKeyThread = bottom.getClassName().startsWith("org.labkey");

        if (isLabKeyThread && stack.length > 12)
            return false;

        return "park".equals(topMethod) || "wait".equals(topMethod) || "poll".equals(topMethod) || "accept".equals(topMethod) || "sleep".equals(topMethod) || "waitForReferencePendingList".equals(topMethod);
        // OK probably just waiting for work.  We could check for common tomcat/labkey patterns here to be more conservative.
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

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean != null)
        {
            DecimalFormat f3 = new DecimalFormat("0.000");

            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean)
            {
                logWriter.debug("Total OS memory (bytes): " + DecimalFormat.getInstance().format(sunOsBean.getTotalMemorySize()));
                logWriter.debug("Free OS memory (bytes): " + DecimalFormat.getInstance().format(sunOsBean.getFreeMemorySize()));
                logWriter.debug("OS CPU load: " + f3.format(sunOsBean.getCpuLoad()));
                logWriter.debug("JVM CPU load: " + f3.format(sunOsBean.getProcessCpuLoad()));
            }
            logWriter.debug("CPU count: " + osBean.getAvailableProcessors());
        }

        logWriter.debug("*********************************************");

        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        var spidsByThread = ConnectionWrapper.getSPIDsForThreads();

        ArrayList<Thread> threadsToDump = new ArrayList<>();
        ArrayList<Thread> boringThreads = new ArrayList<>();

        for (Thread thread : stackTraces.keySet())
        {
            Set<Integer> spids = Objects.requireNonNullElse(spidsByThread.get(thread), Set.of());
            var stack = stackTraces.get(thread);

            if (spids.isEmpty() && justWaiting(stack))
                boringThreads.add(thread);
            else
                threadsToDump.add(thread);
        }

        if (!threadsToDump.isEmpty())
        {
            logWriter.debug("");
            logWriter.debug("  ----- active threads -----");

            threadsToDump.sort(Comparator.comparing(Thread::getName, String.CASE_INSENSITIVE_ORDER));
            for (Thread thread : threadsToDump)
            {
                dumpOneThread(thread, logWriter, stackTraces, spidsByThread);
            }
        }

        if (!boringThreads.isEmpty())
        {
            logWriter.debug("");
            logWriter.debug("  ----- waiting threads -----");

            boringThreads.sort(Comparator.comparing(Thread::getName, String.CASE_INSENSITIVE_ORDER));
            for (Thread thread : boringThreads)
            {
                dumpOneThread(thread, logWriter, stackTraces, spidsByThread);
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
            ConnectionWrapper.dumpOpenConnections(logWriter, null);
            logWriter.debug("*********************************************");
            logWriter.debug("Completed dump of all open connections");
            logWriter.debug("*********************************************");
        }

        // GitHib Issue 713: Automatically include PG locks and active queries in thread dumps
        UserSchema schema = QueryService.get().getUserSchema(User.getAdminServiceUser(), ContainerManager.getRoot(), BasePostgreSqlDialect.POSTGRES_SCHEMA_NAME);
        // Schema won't exist on SQLServer
        if (schema != null)
        {
            try
            {
                writeTable(logWriter, schema, BasePostgreSqlDialect.POSTGRES_LOCKS_TABLE_NAME, "Postgres locks");
            }
            catch (RuntimeException e)
            {
                logWriter.debug("Failed to write Postgres locks table:" + e);
            }
            try
            {
                writeTable(logWriter, schema, BasePostgreSqlDialect.POSTGRES_STAT_ACTIVITY_TABLE_NAME, "Postgres activity");
            }
            catch (RuntimeException e)
            {
                logWriter.debug("Failed to write Postgres activity table:" + e);
            }
        }
    }

    private static void writeTable(LoggerWriter logWriter, UserSchema schema, String tableName, String header)
    {
        QueryForm form = new QueryForm();
        try (var _ = ViewContext.pushMockViewContext(schema.getUser(), schema.getContainer(), new ActionURL()))
        {
            form.setViewContext(HttpView.currentContext());
            form.setSchemaName(schema.getName());
            form.setQueryName(tableName);
            QueryView view = QueryView.create(form, new NullSafeBindException(new Object(), "form"));
            logWriter.debug("Starting dump of " + header);
            logWriter.debug("*********************************************");
            try (TSVWriter writer = view.getTsvWriter())
            {
                StringWriter stringWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stringWriter);
                writer.write(printWriter);
                printWriter.flush();
                logWriter.debug("\n" + stringWriter);
            }
            catch (IOException e)
            {
                logWriter.error("Failed to write " + header, e);
            }
            logWriter.debug("*********************************************");
            logWriter.debug("Completed dump of " + header);
            logWriter.debug("*********************************************");
        }
    }

    static private final Set<String> skipMethods = Set.of("pushThreadDumpContext", "beginTransaction", "ensureTransaction", "execute", "getTroubleshootingStackTrace");

    private static void dumpOneThread(Thread thread, LoggerWriter logWriter, Map<Thread, StackTraceElement[]> stackTraces, HashSetValuedHashMap<Thread, Integer> spidsByThread)
    {
        Set<Integer> spids = Objects.requireNonNullElse(spidsByThread.get(thread), Set.of());
        var stack = stackTraces.get(thread);

        StringBuilder threadInfo = new StringBuilder(thread.getName());
        logWriter.debug("");
        threadInfo.append(" (");
        threadInfo.append(thread.getState());
        threadInfo.append("), daemon: ");
        threadInfo.append(thread.isDaemon());
        if (!spids.isEmpty())
        {
            threadInfo.append(", Database Connection SPIDs = ");
            threadInfo.append(spids);
        }
        logWriter.debug(threadInfo.toString());

        TransactionFilter.RequestTracker uri = TransactionFilter.getRequestSummary(thread);
        if (null != uri)
            logWriter.debug(uri.toLogString());

        for (var i=0 ; i<stack.length ; i++)
        {
            // subtract 1 because dumpThreads includes Thread.run() in the stack trace
            logWriter.debug(String.format("%3d\t\t%s", stack.length-i-1, stack[i].toString()));
        }
        var extraInfo = _threadDumpExtraContext.get(thread);
        if (null != extraInfo && !extraInfo.isEmpty())
        {
            logWriter.debug("extra stack context (may not match stacktrace if thread is not blocked)");
            var messages = extraInfo.toArray(new ThreadExtraContext[0]);
            for (var i = messages.length-1 ; i>= 0 ; i--)
            {
                logWriter.debug("\t" + messages[i].context.replace('\n',' ') + "\tage: " + (System.currentTimeMillis() - messages[i].startTime) + "ms");
                var messageStack = messages[i].stack();
                if (null != messageStack)
                {
                    for (int j=0, count=0 ; j<messageStack.length && count < 4; j++)
                    {
                        if (skipMethods.contains(messageStack[j].getMethodName()))
                            continue;
                        logWriter.debug(String.format("%3d\t\t%s", messageStack.length - j, messageStack[j].toString()));
                        count++;
                    }
                }
            }
        }

        if (ConnectionWrapper.getProbableLeakCount() > 0)
        {
            ConnectionWrapper.dumpLeaksForThread(thread, logWriter);
        }
    }

    /**
     * Returns formatted extra stack context lines for the given thread, suitable for display in thread dumps.
     * Returns an empty list if the thread has no extra context.
     */
    public static List<String> getFormattedExtraContext(Thread thread)
    {
        var extraInfo = _threadDumpExtraContext.get(thread);
        if (extraInfo == null || extraInfo.isEmpty())
            return List.of();

        List<String> result = new ArrayList<>();
        result.add("extra stack context (may not match stacktrace if thread is not blocked)");
        var messages = extraInfo.toArray(new ThreadExtraContext[0]);
        for (int i = messages.length - 1; i >= 0; i--)
        {
            result.add("\t" + messages[i].context.replace('\n', ' ') + "\tage: " + (System.currentTimeMillis() - messages[i].startTime) + "ms");
            var messageStack = messages[i].stack();
            if (messageStack != null)
            {
                for (int j = 0, count = 0; j < messageStack.length && count < 4; j++)
                {
                    if (skipMethods.contains(messageStack[j].getMethodName()))
                        continue;
                    result.add(String.format("%3d\t\t%s", messageStack.length - j, messageStack[j].toString()));
                    count++;
                }
            }
        }
        return result;
    }

    /**
     * Writes the current set of thread stacks once to the supplied logger.
     */
    public static synchronized void dumpThreads(Logger log)
    {
        dumpThreads(new SimpleLoggerWriter(log));
    }
}
