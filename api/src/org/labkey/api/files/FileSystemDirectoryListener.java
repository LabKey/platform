package org.labkey.api.files;

import java.nio.file.Path;

/**
 * User: adam
 * Date: 9/14/13
 * Time: 10:56 AM
 */

// Used to respond to file system events that occur in a registered directory.
public interface FileSystemDirectoryListener
{
    // A new entry (file or directory) was created in the watched directory. Invoked only if the event was listed at registration time.
    public void entryCreated(Path directory, Path entry);
    // An entry (file or directory) in the directory was deleted. Invoked only if the event was listed at registration time.
    public void entryDeleted(Path directory, Path entry);
    // An entry (file or directory) in the directory was changed. Invoked only if the event was listed at registration time.
    public void entryModified(Path directory, Path entry);

    // Indicates that events might have been lost or discarded. The method is always invoked when overflow occurs (regardless of registration).
    public void overflow();
}
