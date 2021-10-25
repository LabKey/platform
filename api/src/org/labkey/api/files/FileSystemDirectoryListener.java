/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

import java.nio.file.Path;

/*
  User: adam
  Date: 9/14/13
  Time: 10:56 AM
 */

/**
 *  Used to respond to file system events that occur in a registered directory.
 */
public interface FileSystemDirectoryListener
{
    /**
     * A new entry (file or directory) was created in the watched directory. Invoked only if the event was listed at registration time.
     */
    void entryCreated(Path directory, Path entry);

    default void entryCreated(Path directory, Path entry, Runnable callback)
    {
        entryCreated(directory, entry);
        if (callback != null)
        {
            callback.run();
        }
    }

    /**
     * An entry (file or directory) in the directory was deleted. Invoked only if the event was listed at registration time
     */
    void entryDeleted(Path directory, Path entry);

    /**
     * An entry (file or directory) in the directory was changed. Invoked only if the event was listed at registration time.
     */
    void entryModified(Path directory, Path entry);

    /**
     * The directory being watched has been deleted. This listener will be unregistered and never invoked again. Perform
     * any clean-up that might be needed (e.g., remove the directory from "ensure" lists so it can be re-registered if the
     * directory reappears).
     */
    default void directoryDeleted(Path directory)
    {
    }

    /**
     * Indicates that events might have been lost or discarded. The method is always invoked when overflow occurs (regardless of registration).
     */
    void overflow();
}
