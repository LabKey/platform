/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents a pipeline root directory, from which the server will look for files for import and analysis. May
 * span multiple root directories on the actual file systems.
 *
 * User: Nick
 * Date: Jul 7, 2007
 */
public interface PipeRoot extends SecurableResource
{
    Container getContainer();

    @NotNull
    URI getUri();

    @NotNull
    File getRootPath();

    @NotNull
    Path getRootNioPath();

    @NotNull
    File getLogDirectory();

    @Nullable
    Path resolveToNioPath(String path);

    @Nullable
    Path resolveToNioPathFromUrl(String url);

    /**
     * @return the file that's at the given relativePath from the pipeline root. Will be null if the relative path
     * attempts to reference something that's not under the root (such as "../../etc/passwd". When the root
     * is configured with an alternative file path, we'll check to see if the file exists there. If not, we'll return
     * a path relative to the root's primary path.
     */
    @Nullable
    File resolvePath(String relativePath);

    @NotNull
    File getImportDirectory();

    /**
     * Create File object for the import directory and ensure that the directory does not exist
     * @return File object for import directory
     * @throws DirectoryNotDeletedException if import directory exists and cannot be deleted
     * @throws FileNotFoundException if import directory path cannot be resolved
     */
    File getImportDirectoryPathAndEnsureDeleted() throws DirectoryNotDeletedException, FileNotFoundException;

    /**
     * Delete the import directory and its contents
     * @throws DirectoryNotDeletedException if import directory exists and cannot be deleted
     */
    void deleteImportDirectory() throws DirectoryNotDeletedException;

    /** @return relative path to the file from the root. null if the file isn't under the root. Does not include a leading slash */
    String relativePath(File file);

    /** @return whether the file specified is a child of the pipeline root */
    boolean isUnderRoot(File file);
    boolean isUnderRoot(Path file);

    boolean hasPermission(Container container, User user, Class<? extends Permission> perm);

    void requiresPermission(Container container, User user, Class<? extends Permission> perm);

    /** Creates a .labkey directory if it's not present and returns it. Used for things like protocol definition files,
     * log files for some upgrade tasks, etc. Its contents are generally not exposed directly to the user */
    @NotNull
    File ensureSystemDirectory();

    @NotNull
    Path ensureSystemDirectoryPath();

    /** @return the entityId for this pipeline root, used to store permissions */
    String getEntityId();

    /** @return whether this root's contents should be indexed by the crawler */
    boolean isSearchable();

    String getWebdavURL();

    /** @return a list of any problems found with this pipeline root */
    List<String> validate();

    /** @return true if this root exists on disk and is a directory */
    boolean isValid();

    void configureForm(SetupForm form);

    /** @return true if this root is based on the site level root */
    boolean isDefault();

    /** @return true if this root is based on cloud storage */
    boolean isCloudRoot();
}
