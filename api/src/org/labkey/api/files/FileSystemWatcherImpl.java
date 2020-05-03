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

import org.apache.log4j.Logger;
import org.imca_cat.pollingwatchservice.PathWatchService;
import org.imca_cat.pollingwatchservice.PollingWatchService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ShutdownListener;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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
    private static final Logger LOG = Logger.getLogger(FileSystemWatcherImpl.class);

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
        _pollingWatcher = new PollingWatchService(4, 10, TimeUnit.SECONDS);
        _pollingWatcher.start();
        FileSystemWatcherThread pollingThread = new FileSystemWatcherThread("PollingFileWatcher", _pollingWatcher);
        ContextListener.addShutdownListener(pollingThread);
        pollingThread.start();
    }

    @Override
    @SafeVarargs
    public final void addListener(Path directory, FileSystemDirectoryListener listener, Kind<Path>... events) throws IOException
    {
        addListener(directory, listener, true, events);
    }

    @SafeVarargs
    private void addListener(Path directory, FileSystemDirectoryListener listener, boolean ensureDirectoryDeleteListenerOnParent, Kind<Path>... events) throws IOException
    {
        // Associate a new PathListenerManager with this directory, if one doesn't already exist
        PathListenerManager plm = new PathListenerManager();
        PathListenerManager previous = _listenerMap.putIfAbsent(directory, plm);     // Atomic operation

        // Register directory with the WatchService, if it's new
        if (null == previous)
        {
            registerWithWatchService(directory, plm);
        }
        else
        {
            plm = previous;
            LOG.debug("Detected previously registered file watcher service for file system of type '" + plm.getFileStoreType() + "'. for directory: " + directory.toAbsolutePath().toString());
        }

        // Add the listener and its requested events
        plm.addListener(listener, events);
//        if (ensureDirectoryDeleteListenerOnParent)
//        {
//            ensureDeleteDirectoryListener(plm, directory);
//        }

        LOG.debug("Registered a file listener on " + directory.toString());
    }

    private void registerWithWatchService(Path directory, PathListenerManager plm) throws IOException
    {
        String fileStoreType = Files.getFileStore(directory).type();
        if (null != fileStoreType)
            fileStoreType = fileStoreType.toLowerCase();

        final WatchKey watchKey;

        // ensure we catch variations such as both nfs and nfs4
        if (null != fileStoreType && (fileStoreType.startsWith("cifs") || fileStoreType.startsWith("smbfs") || fileStoreType.startsWith("nfs")))
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
        plm.setWatchKey(watchKey);
    }

    // This could be used to ensure we see directories where we have listeners getting deleted. But at least on Windows,
    // WatchKey.reset() indicates when directories have been deleted, so this may not be necessary. Need to test on other
    // file systems.
    private void ensureDeleteDirectoryListener(PathListenerManager plm, Path directory) throws IOException
    {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (plm)
        {
            if (!plm.isDirectoryDeleteListenerOnParent())
            {
                Path parent = directory.getParent();
                if (null != parent)
                {
                    LOG.debug("Registering a directory delete listener for " + directory.toString() + " on " + parent);
                    Path fileName = directory.getFileName();
                    addListener(parent, new FileSystemDirectoryListener()
                    {
                        @Override
                        public void entryCreated(Path directory, Path entry)
                        {
                        }

                        @Override
                        public void entryDeleted(Path directory, Path entry)
                        {
                            if (entry.equals(fileName))
                            {
                                LOG.debug("Directory deleted. Need to cancel its WatchKey and inform all its listeners.");
                                PathListenerManager plm = handleDeletedDirectory(directory.resolve(entry));

                                if (null != plm)
                                    plm.getWatchKey().cancel();
                            }
                        }

                        @Override
                        public void entryModified(Path directory, Path entry)
                        {
                        }

                        @Override
                        public void overflow()
                        {
                        }
                    }, false, ENTRY_DELETE);
                }
                plm.setDirectoryDeleteListenerOnParent();
            }
            else
            {
                LOG.debug("Already have a directory delete listener for " + directory.toString());
            }
        }
    }

    @Override
    public void removeListener(Path directory, FileSystemDirectoryListener listener)
    {
        PathListenerManager plm = _listenerMap.get(directory);
        if (plm != null)
        {
            plm.removeListener(listener);
            LOG.debug("Removed a file listener on " + directory.toString());
        }
    }

    // Not a daemon thread because listeners could be performing I/O and other tasks that are dangerous to interrupt.
    private class FileSystemWatcherThread extends Thread implements ShutdownListener
    {
        private final WatchService _watcher;

        private FileSystemWatcherThread(String name, WatchService watcher)
        {
            super(name);
            _watcher = watcher;
        }

        @Override
        public void run()
        {
            try
            {
                while (!isInterrupted())
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
                        if (!watchKey.reset() && null != watchedPath)
                        {
                            LOG.debug("WatchKey is invalid: " + watchedPath);
                            handleDeletedDirectory(watchedPath);
                        }
                    }
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
        }

        private void close()
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

    private @Nullable PathListenerManager handleDeletedDirectory(Path deletedDirectory)
    {
        PathListenerManager plm = _listenerMap.remove(deletedDirectory);
        if (null != plm)
        {
            plm.fireDirectoryDeleted(deletedDirectory);
            if (Files.exists(deletedDirectory))
                LOG.error("WatchKey is invalid but directory still exists: " + deletedDirectory);
        }
        return plm;
    }

    // Manages all the listeners associated with a specific path
    private static class PathListenerManager
    {
        // CopyOnWriteArrayList is thread-safe for write and iteration, and reasonably efficient for small lists with high read/write ratio
        private final List<ListenerContext> _list = new CopyOnWriteArrayList<>();

        private volatile String _fileStoreType;
        private volatile WatchKey _watchKey;

        private boolean _directoryDeleteListenerOnParent = false;

        private void addListener(FileSystemDirectoryListener listener, Kind<Path>[] events)
        {
            // Track the listener and its requested events (the only ones we'll fire)
            _list.add(new ListenerContext(listener, events));
        }

        private void removeListener(FileSystemDirectoryListener listener)
        {
            _list.removeIf(listenerContext -> listenerContext.getListener().equals(listener));
        }

        private String getFileStoreType()
        {
            return _fileStoreType;
        }

        private void setFileStoreType(String fileStoreType)
        {
            _fileStoreType = fileStoreType;
        }

        private WatchKey getWatchKey()
        {
            return _watchKey;
        }

        private void setWatchKey(WatchKey watchKey)
        {
            _watchKey = watchKey;
        }

        private boolean isDirectoryDeleteListenerOnParent()
        {
            return _directoryDeleteListenerOnParent;
        }

        private void setDirectoryDeleteListenerOnParent()
        {
            _directoryDeleteListenerOnParent = true;
        }

        private void fireEvents(WatchEvent<Path> event, Path watchedPath)
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
                    listenerContext.fireEvent(kind, watchedPath, entry);
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

        private void fireEvent(Kind<Path> kind, Path watchedPath, Path entry)
        {
            if (_events.contains(kind))
            {
                if (ENTRY_CREATE == kind)
                    _listener.entryCreated(watchedPath, entry);
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
}
