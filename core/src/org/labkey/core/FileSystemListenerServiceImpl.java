package org.labkey.core;

import org.apache.log4j.Logger;
import org.labkey.api.resource.FileSystemDirectoryListener;
import org.labkey.api.resource.FileSystemListenerService;
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
public class FileSystemListenerServiceImpl implements FileSystemListenerService
{
    private static final Logger LOG = Logger.getLogger(FileSystemListenerServiceImpl.class);

    private final WatchService _watcher;
    private final ConcurrentMap<WatchKey, FileSystemDirectoryListener> _listeners = new ConcurrentHashMap<>();

    public FileSystemListenerServiceImpl() throws IOException
    {
        _watcher = FileSystems.getDefault().newWatchService();
        new FileSystemListenerThread().start();
    }

    @SafeVarargs
    @Override
    public final void addListener(Path directory, FileSystemDirectoryListener listener, WatchEvent.Kind<Path>... events) throws IOException
    {
        //noinspection unchecked
        WatchKey watchKey = directory.register(_watcher, events);
        FileSystemDirectoryListener previous = _listeners.put(watchKey, listener);

        assert null == previous : "Another listener is already registered with that WatchKey";
    }

    @Override
    public void removeListener(Path directory)
    {
        throw new UnsupportedOperationException("We aren't remembering directories yet");
    }


    // Not a daemon thread because listeners could be doing I/O and other tasks that are dangerous to interrupt.
    private class FileSystemListenerThread extends Thread implements ShutdownListener
    {
        private FileSystemListenerThread()
        {
            setName(FileSystemListenerThread.class.getSimpleName());
            ContextListener.addShutdownListener(this);
        }

        @Override
        public void run()
        {
            try
            {
                //noinspection InfiniteLoopStatement
                while (!interrupted())
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
                                listener.overflow();
                            }
                            else
                            {
                                Path entry = event.context();

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

                        // If it's no longer valid, then I guess we should remove the listener
                        if (!watchKey.reset())
                            _listeners.remove(watchKey);        // TODO: create an event for this to notify listeners?
                    }
                    catch (ClosedWatchServiceException ignored)
                    {
                        // We closed the service... loop will be interrupted and terminate soon enough
                    }
                    catch (Throwable e)  // Make sure throwables don't kill the background thread
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                }
            }
            catch (InterruptedException e)
            {
                LOG.info(getName() + " is terminating due to interruption");
            }
        }

        @Override
        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            interrupt();

            try
            {
                _watcher.close();
            }
            catch (IOException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        @Override
        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
        }
    }
}
