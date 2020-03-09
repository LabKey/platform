package org.labkey.api.module;

import org.labkey.api.files.FileSystemDirectoryListener;

import java.nio.file.Path;

public interface ModuleResourceCacheListener extends FileSystemDirectoryListener
{
    /**
     * Called when a module is changed on this server. Allows a resource cache to clear itself and inform chained listeners
     * so they can invalidate any global state they might manage. Implementations will tend to follow the same general
     * pattern as their {@link FileSystemDirectoryListener#entryCreated(Path directory, Path entry)} implementation.
     * @param module The module that changed
     */
    void moduleChanged(Module module);
}
