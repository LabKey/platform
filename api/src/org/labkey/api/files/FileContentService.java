/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.webdav.WebdavResource;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * User: klum
 * Date: Dec 9, 2009
 */
public interface FileContentService
{
    public static final String FILES_LINK = "@files";
    public static final String FILE_SETS_LINK = "@filesets";
    public static final String PIPELINE_LINK = "@pipeline";

    /**
     * Returns the file root of the specified container.  It not explicitly defined,
     * it will default to a path relative to the first parent container with an override
     */
    File getFileRoot(Container c);

    /**
     * Returns the file root for a container of the specified content type
     */
    File getFileRoot(Container c, ContentType type);

    void setFileRoot(Container c, File root);

    void disableFileRoot(Container container);
    boolean isFileRootDisabled(Container container);

    /**
     * A file root can use a default root based on a single site wide root that mirrors the folder structure of
     * a project.
     */
    boolean isUseDefaultRoot(Container container);
    void setIsUseDefaultRoot(Container container, boolean useDefaultRoot);


    File getSiteDefaultRoot();
    void setSiteDefaultRoot(File root);

    /**
     * Create an attachmentParent object that will allow storing files in the file system
     * @param c Container this will be attached to
     * @param name Name of the parent used in getMappedAttachmentDirectory
     * @param path Path to the file. If relative is true, this is the name of a subdirectory of the directory mapped to this c
     * container. If relative is false, this is a fully qualified path name
     * @param relative if true, path is a relative path from the directory mapped from the container
     * @return the created attachment parent
     */
    public AttachmentDirectory registerDirectory(Container c, String name, String path, boolean relative);

    /**
     * Forget about a named directory
     * @param c Container for this attachmentParent
     * @param label Name of the parent used in registerDirectory
     */
    public void unregisterDirectory(Container c, String label);

    /**
     * Return an AttachmentParent for files in the directory mapped to this container
     * @param c Container in the file system
     * @param createDir Create the mapped directory if it doesn't exist
     * @return AttachmentParent that can be passed to other methods of this interface
     */
    public AttachmentDirectory getMappedAttachmentDirectory(Container c, boolean createDir) throws UnsetRootDirectoryException, MissingRootDirectoryException;

    /**
     * Return a named AttachmentParent for files in the directory mapped to this container
     * @param c Container in the file system
     * @return AttachmentParent that can be passed to other methods of this interface
     */
    public AttachmentDirectory getRegisteredDirectory(Container c, String label);

    /**
     * Return a named AttachmentParent for files in the directory mapped to this container
     * @param c Container in the file system
     * @return AttachmentParent that can be passed to other methods of this interface
     */
    public AttachmentDirectory getRegisteredDirectoryFromEntityId(Container c, String entityId);

    /**
     * Return true if the supplied string is a valid project root
     * @param root String to use as the file path
     * @return boolean
     */
    public boolean isValidProjectRoot(String root);

    /**
     * Return a named AttachmentParent for files in the directory mapped to this container
     * @param c Container in the file system
     * @return Array of attachment directories that have previously been registered
     */
    public AttachmentDirectory[] getRegisteredDirectories(Container c);

    enum ContentType {
        files,
        pipeline,
        assay,
    }

    public String getFolderName(ContentType type);

    public FilesAdminOptions getAdminOptions(Container c);

    public void setAdminOptions(Container c, FilesAdminOptions options);

    /**
     * Returns the default file root of the specified container.  This will default to a path
     * relative to the first parent container with an override
     */
    public File getDefaultRoot(Container c, boolean createDir);

    public String getDomainURI(Container c);

    public String getDomainURI(Container c, FilesAdminOptions.fileConfig config);

    public ExpData getDataObject(WebdavResource resource, Container c);
    public QueryUpdateService getFilePropsUpdateService(TableInfo tinfo, Container container);

    public void moveFileRoot(File prev, File dest, @Nullable User user, @Nullable Container container);

    /** Notifies all registered FileListeners that a file or directory has been created */
    public void fireFileCreateEvent(@NotNull File created, @Nullable User user, @Nullable Container container);
    /** Notifies all registered FileListeners that a file or directory has moved */
    public void fireFileMoveEvent(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container);
    /** Add a listener that will be notified when files are created or are moved */
    public void addFileListener(FileListener listener);

    public Map<String, Collection<File>> listFiles(@NotNull Container container);

    /**
     * Returns a SQLFragment for file paths that this FileListener is aware of when the user is a site admin, or empty
     * results otherwise.
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
    public SQLFragment listFilesQuery(@NotNull User currentUser);
}
