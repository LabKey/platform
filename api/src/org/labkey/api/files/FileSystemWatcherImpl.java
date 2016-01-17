/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ShutdownListener;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * User: adam
 * Date: 9/14/13
 * Time: 11:07 AM
 */

/**
 * Wraps the low-level Java WatchService API with a simple listener-based interface. Callers register a FileSystemDirectoryListener
 * on a directory Path, specifying the desired WatchEvent.Kinds (CREATE, DELETE, and/or MODIFY); the appropriate listener method is
 * then invoked whenever a requested event occurs on any file in that directory.
 *
 * A single instance of this class should be sufficient to handle all the file system listener needs of the entire server. (The
 * initial implementation required one FileSystemWatcherImpl per resource type, due to WatchService's limit of one registration per
 * directory plus our need to monitor a single directory for multiple types; that approach required one FileSystemWatcherThread per
 * resource type, which was undesirable. Newer implementation adds bookkeeping to associate multiple listeners with a single
 * WatchService directory registration.)
 *
 * This class is thread-safe, for both listener registration and event invocation. FileSystemDirectoryListeners must be thread-safe.
 */
public class FileSystemWatcherImpl implements FileSystemWatcher
{
    private static final Logger LOG = Logger.getLogger(FileSystemWatcherImpl.class);

    private final WatchService _watcher;
    private final ConcurrentMap<Path, PathListenerManager> _listenerMap = new ConcurrentHashMap<>(1000);

    FileSystemWatcherImpl() throws IOException
    {
        _watcher = FileSystems.getDefault().newWatchService();
        FileSystemWatcherThread thread = new FileSystemWatcherThread();
        ContextListener.addShutdownListener(thread);
        thread.start();
    }

    public final void addListener(Path directory, FileSystemDirectoryListener listener, Kind<Path>... events) throws IOException
    {
        // Associate a new PathListenerManager with this directory, if one doesn't already exist
        PathListenerManager plm = new PathListenerManager();
        PathListenerManager previous = _listenerMap.putIfAbsent(directory, plm);     // Atomic operation

        // Register directory with the WatchService, if it's new
        if (null == previous)
            directory.register(_watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);  // Register all events (future listener might events that current listener doesn't)
        else
            plm = previous;

        // Add the listener and its requested events
        plm.addListener(listener, events);

        LOG.debug("Registered a file listener on " + directory.toString());
    }

    public void removeListener(Path directory, FileSystemDirectoryListener listener)
    {
        throw new UnsupportedOperationException();
    }


    // Not a daemon thread because listeners could be performing I/O and other tasks that are dangerous to interrupt.
    private class FileSystemWatcherThread extends Thread implements ShutdownListener
    {
        private volatile boolean _continue = true;

        private FileSystemWatcherThread()
        {
            ContextListener.addShutdownListener(this);
        }

        @Override
        public void run()
        {
            try
            {
                while (_continue)
                {
                    WatchKey watchKey = _watcher.take();
                    Path watchedPath = null;

                    try
                    {
                        watchedPath = (Path)watchKey.watchable();
                        PathListenerManager plm = _listenerMap.get(watchedPath);

                        for (WatchEvent<?> watchEvent : watchKey.pollEvents())
                        {
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> event = (WatchEvent<Path>)watchEvent;
                            plm.fireEvents(event, watchedPath);
                        }
                    }
                    catch (Throwable e)  // Make sure throwables don't kill the background thread
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                    finally
                    {
                        // Always reset the watchKey, even if a listener throws, otherwise we'll never see another event on this directory.
                        // If watch key is no longer valid, then I guess we should remove the listener manager.
                        if (!watchKey.reset() && null != watchedPath)
                            _listenerMap.remove(watchedPath);        // TODO: create an event to notify listeners?
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
                close();   // Make sure we're closed... shutdown case will close twice, but that's a no-op
            }
        }

        @Override
        public void shutdownPre()
        {
            _continue = false;
            close();
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

    // Manages all the listeners associated with a specific path
    private static class PathListenerManager
    {
        // CopyOnWriteArrayList is thread-safe for write and iteration, and reasonably efficient for small lists with high read/write ratio
        private final List<ListenerContext> _list = new CopyOnWriteArrayList<>();

        private void addListener(FileSystemDirectoryListener listener, Kind<Path>[] events)
        {
            // Track the listener and its requested events (the only ones we'll fire)
            _list.add(new ListenerContext(listener, events));
        }

        private void fireEvents(WatchEvent<Path> event, Path watchedPath)
        {
            Kind<Path> kind = event.kind();

            if (OVERFLOW == (Kind<?>)kind)
            {
                LOG.info("Overflow! File system watcher events may have been lost.");

                for (ListenerContext listenerContext : _list)
                    listenerContext.fireOverflow();
            }
            else
            {
                Path entry = event.context();
                LOG.debug(kind.name() + " event on " + watchedPath.resolve(entry));

                for (ListenerContext listenerContext : _list)
                    listenerContext.fireEvent(kind, watchedPath, entry);
            }
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
            _events = new HashSet<>(Arrays.asList(events));
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
    }
}
