/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.security.User;

import java.io.File;
import java.util.Collection;

/**
 * Listener that gets notified when the server moves files or directories on the file system. Method is invoked
 * once at the root level of the move, not recursively for every child file.
 * User: jeckels
 * Date: 11/7/12
 */
public interface FileListener
{
    public String getSourceName();
    
    /**
     * Called AFTER the file (or directory) has already been created on disk
     * @param created newly created resource
     * @param user if available, the user who initiated the create
     * @param container if available, the container in which the create was initiated
     */
    public void fileCreated(@NotNull File created, @Nullable User user, @Nullable Container container);

    /**
     * Called AFTER the file (or directory) has already been moved on disk
     * @param src the original file path
     * @param dest the new file path
     * @param user if available, the user who initiated the move
     * @param container if available, the container in which the move was initiated
     */
    public void fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container);

    /**
     * List file paths in the database this FileListener is aware of.
     * @param container If not null, list files in the given container, otherwise from all containers.
     */
    public Collection<File> listFiles(@Nullable Container container);

    /**
     * Returns a SQLFragment for file paths that this FileListener is aware of.
     * The expected columns are:
     * <ul>
     *     <li>Container</li>
     *     <li>Created</li>
     *     <li>CreatedBy</li>
     *     <li>Modified</li>
     *     <li>ModifiedBy</li>
     *     <li>FilePath</li>
     *     <li>SourceKey</li>
     *     <li>SourceName</li>
     * </ul>
     */
    public SQLFragment listFilesQuery();
}
