/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
package org.labkey.api.files;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.imca_cat.pollingwatchservice.PathWatchService;
import org.imca_cat.pollingwatchservice.PollingWatchService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.cloud.CloudWatcherConfig;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Wraps the low-level Java WatchService API with a simple listener-based interface. Unlike WatchService, which is limited to one
 * registration per file system directory, a single instance of this class should be sufficient to handle all file system listener
 * needs of the entire server.
 *
 * Callers register a FileSystemDirectoryListener on a directory Path, specifying the desired WatchEvent.Kinds (CREATE, DELETE,
 * and/or MODIFY); whenever an event occurs on any file or child directory in that directory, the appropriate method is invoked
 * on all listeners registered on that directory that have requested notification of that event.
 *
 * This class is thread-safe, for both listener registration and event invocation. Implementations of FileSystemDirectoryListener
 * must be thread-safe. Listener methods must return quickly since they are all invoked by a single thread.
 *
 * User: adam
 * Date: 9/14/13
 * Time: 11:07 AM
 */
public class FileSystemWatcherImpl implements FileSystemWatcher
{
    private static final Logger LOG = LogHelper.getLogger(FileSystemWatcherImpl.class, "Open file system handlers and listeners");
    private static final long POLLING_PERIOD_SECONDS = 10L;

    private final WatchService _watcher;
    private final ConcurrentMap<Path, PathListenerManager> _listenerMap = new ConcurrentHashMap<>(1000);
    private final PathWatchService _pollingWatcher;

    FileSystemWatcherImpl() throws IOException
    {
        _watcher = FileSystems.getDefault().newWatchService();
        FileSystemWatcherThread thread = new FileSystemWatcherThread("DefaultFileWatcher", _watcher);
        ContextListener.addShutdownListener(thread);
        thread.start();

        // for files system that do not support registering file watchers we will use a service that polls that watched
        // directory looking for changes since last polling. See: https://www.imca.aps.anl.gov/~jlmuir/sw/pollingwatchservice.html
        _pollingWatcher = new PollingWatchService(4, POLLING_PERIOD_SECONDS, TimeUnit.SECONDS);
        _pollingWatcher.start();
        FileSystemWatcherThread pollingThread = new FileSystemWatcherThread("PollingFileWatcher", _pollingWatcher);
        ContextListener.addShutdownListener(pollingThread);
        pollingThread.start();

        initCloudWatcher();
    }

    private void initCloudWatcher()
    {
        //Can't do Cloud module check here as it likely hasn't loaded yet.
        CloudWatcherThread thread = new CloudWatcherThread("CloudWatcherPrimaryThread");
        ContextListener.addShutdownListener(thread);
        thread.start();
    }

    @Override
    @SafeVarargs
    public final void addCloudListener(Path directory, FileSystemDirectoryListener listener, final CloudWatcherConfig config, Kind<Path>... events) throws IOException
    {
        //Ensure a trailing '/' by push/pop a directory
        Path dir = directory.resolve("a").getParent();

        //The cloud service doesn't actually watch the supplied target path directly, and instead watches the notification
        // queue from the config. Any received notification keys (paths) are then checked later during processing.
        addListener(dir, listener, (path, plm) -> registerWithCloudService(plm, config), events);
    }

    private void registerWithCloudService( @NotNull PathListenerManager plm, final CloudWatcherConfig config)
    {
        CloudStoreService.get().registerCloudWatcher(config, (Path filePath, Runnable callback) -> {
            LOG.debug("Processing watch event for filePath: " + FileUtil.getAbsolutePath(filePath)); //Strip off any user info that may be in path URI.
            Path watchedPath = filePath.getParent();

            CloudWatchEvent cwe = new CloudWatchEvent(filePath);

            //TODO Could do a validation sanity check before firing event?
            plm.fireEvents(cwe, watchedPath, callback);
        });
    }

    @Override
    @SafeVarargs
    public final void addListener(Path directory, FileSystemDirectoryListener listener, Kind<Path>... events) throws IOException
    {
        addListener(directory, listener, this::registerWithWatchService, events);
    }

    @FunctionalInterface
    public interface BiConsumer_WithExceptions<T, U, E extends Exception> {
        void accept(T t, U u) throws E;
    }

    private void addListener(Path directory, FileSystemDirectoryListener listener, BiConsumer_WithExceptions<Path, PathListenerManager, IOException> registrar, Kind<Path>[] events) throws IOException
    {
        // Associate a new PathListenerManager with this directory, if one doesn't already exist
        PathListenerManager plm = new PathListenerManager();
        PathListenerManager previous = _listenerMap.putIfAbsent(directory, plm);     // Atomic operation

        // Register directory with the WatchService, if it's new
        if (null == previous)
        {
            registrar.accept(directory, plm);
        }
        else
        {
            plm = previous;
            LOG.debug("Detected previously registered file watcher service for file system of type '" + plm.getFileStoreType() + "'. for directory: " + directory.toAbsolutePath());
        }

        // Add the listener and its requested events
        plm.addListener(listener, events);
//        if (ensureDirectoryDeleteListenerOnParent)
//        {
//            ensureDeleteDirectoryListener(plm, directory);
//        }

        LOG.debug("Registered a file listener on " + directory);
    }

    private void registerWithWatchService(Path directory, PathListenerManager plm) throws IOException
    {
        String fileStoreType = Files.getFileStore(directory).type();
        if (null != fileStoreType)
            fileStoreType = fileStoreType.toLowerCase();

        final WatchKey watchKey;

        // ensure we catch variations such as both nfs and nfs4
        boolean pollingWatcher = null != fileStoreType && (fileStoreType.startsWith("cifs") || fileStoreType.startsWith("smbfs") || fileStoreType.startsWith("nfs"));
        if (pollingWatcher)
        {
            LOG.debug("Detected network file system type '" + fileStoreType + "'. Create polling file watcher service and register this directory there for directory: " + directory.toAbsolutePath().toString());
            watchKey = _pollingWatcher.register(directory, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        }
        else
        {
            LOG.debug("Detected local file system type '" + fileStoreType + "'. Register path with standard watcher service for directory: " + directory.toAbsolutePath().toString());
            watchKey = directory.register(_watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);  // Register all events (future listener might request events that current listener doesn't)
        }

        plm.setFileStoreType(fileStoreType);
        plm.setPollingWatcher(pollingWatcher);
        plm.setWatchKey(watchKey);
    }

    @Override
    public void removeListener(@NotNull Path directory, FileSystemDirectoryListener listener, @Nullable Integer listenerId)
    {
        //Ensure a trailing '/' if needed, otherwise some keys won't be found. Path::normalize method doesn't seem to address this
        Path dir = directory.resolve("a").getParent();
        PathListenerManager plm = _listenerMap.get(dir);
        if (plm != null)
        {
            plm.removeListener(listener);
            LOG.debug("Removed a file listener on " + directory);

            if (FileUtil.hasCloudScheme(dir) && CloudStoreService.get() != null)
            {
                CloudStoreService.get().unregisterCloudWatcher(listenerId);
            }
        }
    }

    private abstract static class BaseWatcherThread extends Thread implements ShutdownListener
    {
        private BaseWatcherThread(String name)
        {
            super(name);
        }

        @Override
        public void run()
        {
            try
            {
                while (!isInterrupted())
                {
                    watch();
                }
            }
            catch (ClosedWatchServiceException | InterruptedException ignored)
            {
                // We were interrupted or we closed the service... time to terminate the thread
            }
            finally
            {
                LOG.info(getClass().getSimpleName() + " is terminating");
                close();
            }
        }

        /* NOTE: this method is being called by the shutdown thread but interrupts the file watcher thread */
        @Override
        public void shutdownPre()
        {
            this.interrupt();
        }

        @Override
        public void shutdownStarted()
        {
            this.close();
        }

        abstract void close();
        abstract void watch() throws InterruptedException;
    }

    // Not a daemon thread because listeners could be performing I/O and other tasks that are dangerous to interrupt.
    private class FileSystemWatcherThread extends BaseWatcherThread
    {
        private final WatchService _watcher;

        private FileSystemWatcherThread(String name, WatchService watcher)
        {
            super(name);
            _watcher = watcher;
        }

        @Override
        protected void watch() throws InterruptedException
        {
            WatchKey watchKey = _watcher.take();
            Path watchedPath = null;

            try
            {
                watchedPath = (Path)watchKey.watchable();
                PathListenerManager plm = _listenerMap.get(watchedPath);

                // Not sure how this would happen (_listenerMap.remove() below is likely involved... could it be
                // multiple threads racing with the same WatchKey which also simultaneously becomes invalid?),
                // but #26934 shows occasional examples.
                if (null != plm)
                {
                    for (WatchEvent<?> watchEvent : watchKey.pollEvents())
                    {
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> event = (WatchEvent<Path>) watchEvent;
                        plm.fireEvents(event, watchedPath);
                    }
                }
            }
            catch (Throwable e)  // Make sure throwables don't kill the background thread
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
            finally
            {
                // Always reset the watchKey, even if a listener throws, otherwise we'll never see another event
                // on this directory. If watch key is no longer valid, then it's probably been deleted.
                if (!watchKey.reset() && null != watchedPath )
                {
                    LOG.debug("WatchKey is invalid: " + watchedPath);
                    if (!isInterrupted())
                        handleDeletedDirectory(watchedPath);
                }

                if (isInterrupted())
                {
                    LOG.debug("File watcher interrupted: " + watchedPath);
                }
            }
        }

        @Override
        protected void close()
        {
            try
            {
                _watcher.close();
            }
            catch (IOException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    private class CloudWatcherThread extends BaseWatcherThread
    {
        private Timer _timer;
        protected CloudWatcherThread(String name)
        {
            super(name);
        }

        @Override
        protected void watch() throws InterruptedException
        {
            try
            {
                if (_timer == null)
                {
                    LOG.info("Initiating a new timer job for cloud watchers.");
                    _timer = new Timer();
                    _timer.scheduleAtFixedRate(scheduleWatchTask(), POLLING_PERIOD_SECONDS * 1000, POLLING_PERIOD_SECONDS * 1000);
                }
            }
            catch (Throwable t)
            {
                if (_timer != null)
                {
                    LOG.debug("Caught error in CloudWatcherThread. Cancelling running jobs");
                    _timer.cancel();
                    _timer = null;
                }

                throw t;
            }
        }

        private TimerTask scheduleWatchTask()
        {
            return new TimerTask()
            {
                @Override
                public void run()
                {
                    try
                    {
                        final CloudStoreService css = CloudStoreService.get();
                        if (css != null)
                        {
                            Collection<Integer> watchers = css.getWatcherJobs();
                            LOG.debug("Watcher count: " + watchers);
                            watchers.parallelStream().forEach(css::pollWatcher);
                        }
                        else if (ModuleLoader.getInstance().isStartupComplete())
                        {
                            //No reason to watch if the module isn't available
                            LOG.info("Shutting down cloud watcher, module not loaded.");
                            CloudWatcherThread.this.interrupt();
                        }
                    }
                    catch (Throwable e)  // Make sure throwables don't kill the background thread
                    {
                        LOG.debug("Error running cloud watcher task: ", e);
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                }
            };
        }

        @Override
        protected void close()
        {
            LOG.info("Shutting down the CloudWatcher thread: " + this.getId());
            if (_timer != null)
            {
                LOG.debug("CloudWatcher timer job cancelled.");
                _timer.cancel();
                _timer = null;
            }

            if (CloudStoreService.get() != null)
                CloudStoreService.get().shutdownWatchers();
        }
    }

    private static class CloudWatchEvent implements WatchEvent<Path>
    {
        private final Path cloudPath;
        private CloudWatchEvent(Path path)
        {
            cloudPath = path;
        }

        @Override
        public Kind<Path> kind()
        {
            return ENTRY_CREATE;
        }

        @Override
        public int count()
        {
            return 1;
        }

        @Override
        public Path context()
        {
            return cloudPath;
        }
    }

    private void handleDeletedDirectory(Path deletedDirectory)
    {
        PathListenerManager plm = _listenerMap.remove(deletedDirectory);
        if (null != plm)
        {
            plm.fireDirectoryDeleted(deletedDirectory);
        }
    }

    // Manages all the listeners associated with a specific path
    private static class PathListenerManager
    {
        // CopyOnWriteArrayList is thread-safe for write and iteration, and reasonably efficient for small lists with high read/write ratio
        private final List<ListenerContext> _list = new CopyOnWriteArrayList<>();

        private volatile String _fileStoreType;
        private volatile WatchKey _watchKey;
        private volatile boolean _pollingWatcher;

        private void addListener(FileSystemDirectoryListener listener, Kind<Path>[] events)
        {
            // Track the listener and its requested events (the only ones we'll fire)
            _list.add(new ListenerContext(listener, events));
            if (_watchKey != null)
                _watchKey.reset();
        }

        private void removeListener(FileSystemDirectoryListener listener)
        {
            _list.removeIf(listenerContext -> listenerContext.getListener().equals(listener));
            if (_watchKey != null)
                _watchKey.reset();
        }

        private String getFileStoreType()
        {
            return _fileStoreType;
        }

        private void setFileStoreType(String fileStoreType)
        {
            _fileStoreType = fileStoreType;
        }

        public boolean isPollingWatcher()
        {
            return _pollingWatcher;
        }

        public void setPollingWatcher(boolean pollingWatcher)
        {
            _pollingWatcher = pollingWatcher;
        }

        private WatchKey getWatchKey()
        {
            return _watchKey;
        }

        private void setWatchKey(WatchKey watchKey)
        {
            _watchKey = watchKey;
            _watchKey.reset();
        }

        private void fireEvents(WatchEvent<Path> event, Path watchedPath)
        {
            fireEvents(event, watchedPath, null);
        }


        private void fireEvents(WatchEvent<Path> event, Path watchedPath, Runnable callback)
        {
            Kind<Path> kind = event.kind();

            if (OVERFLOW == (Kind<?>) kind)
            {
                LOG.info("Overflow! File system watcher events may have been lost.");

                _list.forEach(ListenerContext::fireOverflow);
            }
            else
            {
                Path entry = event.context();
                LOG.debug(kind.name() + " event on " + watchedPath.resolve(entry));

                for (ListenerContext listenerContext : _list)
                    listenerContext.fireEvent(kind, watchedPath, entry, callback);
            }
        }

        private void fireDirectoryDeleted(Path directory)
        {
            _list.forEach(lc->lc.fireDirectoryDeleted(directory));
        }
    }

    // Keeps track of a single listener and the events it requested
    private static class ListenerContext
    {
        private final FileSystemDirectoryListener _listener;
        private final Set<Kind<Path>> _events;

        private ListenerContext(FileSystemDirectoryListener listener, Kind<Path>[] events)
        {
            _listener = listener;
            _events = Set.of(events);
        }

        private void fireEvent(Kind<Path> kind, Path watchedPath, Path entry, Runnable callback)
        {
            if (_events.contains(kind))
            {
                if (ENTRY_CREATE == kind)
                    _listener.entryCreated(watchedPath, entry, callback);
                else if (ENTRY_DELETE == kind)
                    _listener.entryDeleted(watchedPath, entry);
                else if (ENTRY_MODIFY == kind)
                    _listener.entryModified(watchedPath, entry);
                else
                    assert false : "Unknown event!";
            }
        }

        private void fireOverflow()
        {
            _listener.overflow();
        }

        private void fireDirectoryDeleted(Path directory)
        {
            _listener.directoryDeleted(directory);
        }

        public FileSystemDirectoryListener getListener()
        {
            return _listener;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testFileWatchers() throws IOException, InterruptedException
        {
            File root = FileUtil.createTempDirectory("fileWatcherTest").toFile(); //Conversion should be safe as temp dir should be on a local URI //TODO should convert to Path for consistencies sake
            File testFolder = new File(root, "test");

            // Do it twice to ensure that everything gets cleaned up on directory delete and a new watcher can be added
            // to a new version of the same directory
            test(testFolder);
            test(testFolder);
        }

        private void test(File testFolder) throws IOException, InterruptedException
        {
            assertTrue(testFolder.mkdir());
            Path testPath = testFolder.toPath();
            FileSystemWatcher fsw = FileSystemWatchers.get();
            assertTrue(fsw instanceof FileSystemWatcherImpl);
            FileSystemWatcherImpl watcher = (FileSystemWatcherImpl)fsw;

            Set<String> created = new ConcurrentHashSet<>();
            Set<String> deleted = new ConcurrentHashSet<>();
            Set<String> modified = new ConcurrentHashSet<>();
            Set<String> directoryDeleted = new ConcurrentHashSet<>();
            AtomicInteger overflow = new AtomicInteger(0);
            AtomicInteger events = new AtomicInteger(0);

            watcher.addListener(testPath, new FileSystemDirectoryListener()
            {
                @Override
                public void entryCreated(Path directory, Path entry)
                {
                    created.add(entry.toString());
                    events.incrementAndGet();
                }

                @Override
                public void entryDeleted(Path directory, Path entry)
                {
                    deleted.add(entry.toString());
                    events.incrementAndGet();
                }

                @Override
                public void entryModified(Path directory, Path entry)
                {
                    modified.add(entry.toString());
                    events.incrementAndGet();
                }

                @Override
                public void directoryDeleted(Path directory)
                {
                    directoryDeleted.add(directory.toString());
                    events.incrementAndGet();
                }

                @Override
                public void overflow()
                {
                    overflow.incrementAndGet();
                }
            }, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

            PathListenerManager plm = watcher._listenerMap.get(testPath);
            assertNotNull(plm);
            assertEquals(1, plm._list.size());

            File a = new File(testFolder, "a");
            File b = new File(testFolder, "b");
            File c = new File(testFolder, "c");

            assertTrue(a.createNewFile());
            assertTrue(b.createNewFile());
            assertTrue(c.createNewFile());

            waitForEvents(events, 3);

            try (PrintWriter pw = PrintWriters.getPrintWriter(a))
            {
                pw.println("Hello World");
            }

            try (PrintWriter pw = PrintWriters.getPrintWriter(c))
            {
                pw.println("Hello World");
            }

            waitForEvents(events, SystemUtils.IS_OS_WINDOWS ? 7 : 5);

            assertTrue(a.delete());
            assertTrue(b.delete());

            waitForEvents(events, SystemUtils.IS_OS_WINDOWS ? 11 : 8);

            FileUtil.deleteDir(testFolder);

            waitForEvents(events, SystemUtils.IS_OS_WINDOWS ? 14 : 10);

            assertEquals(3, created.size());
            assertTrue(created.containsAll(Set.of("a", "b", "c")));
            // Note: Modified events occur on delete on Windows, but not Linux
            Set<String> expectedModified = SystemUtils.IS_OS_WINDOWS ? Set.of("a", "b", "c") : Set.of("a", "c");
            assertEquals(expectedModified.size(), modified.size());
            assertTrue(created.containsAll(expectedModified));
            int deletedCount = deleted.size();
            assertTrue("Unexpected number of deleted events: " + deletedCount, deletedCount >= 2 && deletedCount <= 3);
            assertTrue(created.containsAll(Set.of("a", "b", "c")));
            assertEquals(1, directoryDeleted.size());
            assertEquals(0, overflow.get());

            plm = watcher._listenerMap.get(testPath);
            assertNull(plm);
        }

        private void waitForEvents(AtomicInteger events, int targetCount) throws InterruptedException
        {
            int i = 2;
            Thread.sleep(i * 1000);
            int maxSleepSeconds = 10;

            while (events.get() < targetCount && i < maxSleepSeconds)
            {
                //noinspection BusyWait
                Thread.sleep(1000);
                i++;
            }

            LOG.info("Waiting for file watcher events took " + StringUtilsLabKey.pluralize(i, "second"));

            LOG.info("Actual event count: " + events.get() + " vs. target: " + targetCount);
            //assertEquals(expectedEventCount, events.get());
        }
    }
}
