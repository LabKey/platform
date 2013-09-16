package org.labkey.api.resource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * User: adam
 * Date: 9/14/13
 * Time: 11:02 AM
 */
public interface FileSystemListenerService
{
    public void addListener(Path directory, FileSystemDirectoryListener listener, WatchEvent.Kind<Path>... events) throws IOException;
    public void removeListener(Path directory);
}
