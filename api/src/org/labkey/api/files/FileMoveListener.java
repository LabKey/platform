package org.labkey.api.files;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.io.File;

/**
 * Listener that gets notified when the server moves files or directories on the file system. Method is invoked
 * once at the root level of the move, not recursively for every child file.
 * User: jeckels
 * Date: 11/7/12
 */
public interface FileMoveListener
{
    /**
     * Called AFTER the file has already been moved on disk
     * @param srcFile original location of the file
     * @param destFile new location of the file
     * @param user if available, the user who initiated the move
     * @param container if available, the container in which the move was initiated
     */
    public void fileMoved(@NotNull File srcFile, @NotNull File destFile, @Nullable User user, @Nullable Container container);
}
