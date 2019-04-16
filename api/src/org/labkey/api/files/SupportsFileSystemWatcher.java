package org.labkey.api.files;

import java.nio.file.WatchEvent;

public interface SupportsFileSystemWatcher
{
    void registerListener(FileSystemWatcher watcher, FileSystemDirectoryListener listener, WatchEvent.Kind<java.nio.file.Path>... events);
}
