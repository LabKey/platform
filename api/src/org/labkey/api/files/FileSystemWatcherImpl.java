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

import javax.servlet.ServletContextEvent;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * User: adam
 * Date: 9/14/13
 * Time: 11:07 AM
 */
public class FileSystemWatcherImpl implements FileSystemWatcher
{
    private static final Logger LOG = Logger.getLogger(FileSystemWatcherImpl.class);

    private final String _name;
    private final WatchService _watcher;
    private final ConcurrentMap<WatchKey, FileSystemDirectoryListener> _listeners = new ConcurrentHashMap<>();

    private volatile boolean _continue = true;

    FileSystemWatcherImpl(String name) throws IOException
    {
        _name = name;
        _watcher = FileSystems.getDefault().newWatchService();
        FileSystemWatcherThread thread = new FileSystemWatcherThread();
        ContextListener.addShutdownListener(thread);
        thread.start();
    }

    @SafeVarargs
    public final void addListener(Path directory, FileSystemDirectoryListener listener, WatchEvent.Kind<Path>... events) throws IOException
    {
        //noinspection unchecked
        WatchKey watchKey = directory.register(_watcher, events);
        FileSystemDirectoryListener previous = _listeners.put(watchKey, listener);

        LOG.debug(_name + " registered a listener on " + directory.toString());

        if (null != previous)
            LOG.warn("Another listener is already registered with WatchKey " + watchKey.toString() + ", Path " + directory.toAbsolutePath() + ", previous FileSystemDirectoryListener " + _listeners.get(watchKey));
    }

    public void removeListener(Path directory)
    {
        throw new UnsupportedOperationException("We aren't remembering directories yet");
    }


    // Not a daemon thread because listeners could be doing I/O and other tasks that are dangerous to interrupt.
    private class FileSystemWatcherThread extends Thread implements ShutdownListener
    {
        private FileSystemWatcherThread()
        {
            setName(FileSystemWatcherThread.class.getSimpleName() + ": " + _name);
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

                    try
                    {
                        FileSystemDirectoryListener listener = _listeners.get(watchKey);
                        Path watchedPath = (Path)watchKey.watchable();

                        for (WatchEvent<?> watchEvent : watchKey.pollEvents())
                        {
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> event = (WatchEvent<Path>)watchEvent;
                            WatchEvent.Kind<?> kind = event.kind();

                            if (OVERFLOW == kind)
                            {
                                LOG.info("Overflow! File system watcher events may have been lost.");
                                listener.overflow();
                            }
                            else
                            {
                                Path entry = event.context();
                                LOG.debug(kind.name() + " event on " + watchedPath.resolve(entry));

                                if (ENTRY_CREATE == kind)
                                    listener.entryCreated(watchedPath, entry);
                                else if (ENTRY_DELETE == kind)
                                    listener.entryDeleted(watchedPath, entry);
                                else if (ENTRY_MODIFY == kind)
                                    listener.entryModified(watchedPath, entry);
                                else
                                    assert false : "Unknown event!";
                            }
                        }
                    }
                    catch (Throwable e)  // Make sure throwables don't kill the background thread
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                    finally
                    {
                        // Always reset the watchKey, even if a listener throws, otherwise we'll never see another event on this directory.
                        // If watch key is no longer valid, then I guess we should remove the listener
                        if (!watchKey.reset())
                            _listeners.remove(watchKey);        // TODO: create an event to notify listeners?
                    }
                }
            }
            catch (ClosedWatchServiceException | InterruptedException ignored)
            {
                // We were interrupted or we closed the service... time to terminate the thread
            }
            finally
            {
                LOG.info(_name + " is terminating");
                close();   // Make sure we're closed... shutdown case will close twice, but that's a no-op
            }
        }

        @Override
        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            _continue = false;
            close();
        }

        @Override
        public void shutdownStarted(ServletContextEvent servletContextEvent)
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
}
