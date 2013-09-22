package org.labkey.api.files;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * User: adam
 * Date: 9/19/13
 * Time: 9:50 AM
 */
public class NoopSystemWatcher implements FileSystemWatcher
{
    @SafeVarargs
    public final void addListener(Path directory, FileSystemDirectoryListener listener, WatchEvent.Kind<Path>... events) throws IOException
    {
    }

    public void removeListener(Path directory)
    {
    }
}
