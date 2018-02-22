/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.webdav.WebdavResource;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * User: klum
 * Date: Dec 9, 2009
 */
public interface FileContentService
{
    String FILES_LINK = "@files";
    String FILE_SETS_LINK = "@filesets";
    String PIPELINE_LINK = "@pipeline";
    String CLOUD_LINK = "@cloud";

    String CLOUD_ROOT_PREFIX = "/@cloud";

    static @Nullable FileContentService get()
    {
        return ServiceRegistry.get(FileContentService.class);
    }

    static void setInstance(FileContentService impl)
    {
        ServiceRegistry.get().registerService(FileContentService.class, impl);
    }

    /**
     * Returns the file root of the specified container. If not explicitly defined,
     * it will default to a path relative to the first parent container with an override
     */
    @Nullable
    File getFileRoot(@NotNull Container c);
    @Nullable
    java.nio.file.Path getFileRootPath(@NotNull Container c);

    /**
     * Returns the file root of the specified content type for a container
     */
    @Nullable
    File getFileRoot(@NotNull Container c, @NotNull ContentType type);
    @Nullable
    java.nio.file.Path getFileRootPath(@NotNull Container c, @NotNull ContentType type);

    void setFileRoot(@NotNull Container c, @Nullable File root);
    void setFileRootPath(@NotNull Container c, @Nullable String root);
    void setCloudRoot(@NotNull Container c, String cloudRootName);
    boolean isCloudRoot(Container container);
    String getCloudRootName(Container c);

    void disableFileRoot(Container container);
    boolean isFileRootDisabled(Container container);

    /**
     * A file root can use a default root based on a single site wide root that mirrors the folder structure of
     * a project.
     */
    boolean isUseDefaultRoot(Container container);
    void setIsUseDefaultRoot(Container container, boolean useDefaultRoot);


    @NotNull
    File getSiteDefaultRoot();
    @NotNull
    Path getSiteDefaultRootPath();
    void setSiteDefaultRoot(File root, User user);

    @NotNull
    File getUserFilesRoot();
    void setUserFilesRoot(File root, User user);

    /**
     * Create an attachmentParent object that will allow storing files in the file system
     * @param c Container this will be attached to
     * @param name Name of the parent used in getMappedAttachmentDirectory
     * @param path Path to the file. If relative is true, this is the name of a subdirectory of the directory mapped to this c
     * container. If relative is false, this is a fully qualified path name
     * @param relative if true, path is a relative path from the directory mapped from the container
     * @return the created attachment parent
     */
    AttachmentDirectory registerDirectory(Container c, String name, String path, boolean relative);

    /**
     * Forget about a named directory
     * @param c Container for this attachmentParent
     * @param label Name of the parent used in registerDirectory
     */
    void unregisterDirectory(Container c, String label);

    /**
     * Return an AttachmentParent for files in the directory mapped to this container
     * @param c Container in the file system
     * @param createDir Create the mapped directory if it doesn't exist
     * @return AttachmentParent that can be passed to other methods of this interface
     */
    AttachmentDirectory getMappedAttachmentDirectory(Container c, boolean createDir) throws UnsetRootDirectoryException, MissingRootDirectoryException;

    /**
     * Return a named AttachmentParent for files in the directory mapped to this container
     * @param c Container in the file system
     * @return AttachmentParent that can be passed to other methods of this interface
     */
    AttachmentDirectory getRegisteredDirectory(Container c, String label);

    /**
     * Return a named AttachmentParent for files in the directory mapped to this container
     * @param c Container in the file system
     * @return AttachmentParent that can be passed to other methods of this interface
     */
    AttachmentDirectory getRegisteredDirectoryFromEntityId(Container c, String entityId);

    /**
     * Return true if the supplied string is a valid project root
     * @param root String to use as the file path
     * @return boolean
     */
    boolean isValidProjectRoot(String root);

    /**
     * Return all AttachmentParents for files in the directory mapped to this container
     * @param c Container in the file system
     * @return Collection of attachment directories that have previously been registered
     */
    @NotNull Collection<AttachmentDirectory> getRegisteredDirectories(Container c);

    enum ContentType {
        files,
        pipeline,
        assay,
    }

    String getFolderName(ContentType type);

    FilesAdminOptions getAdminOptions(Container c);

    void setAdminOptions(Container c, FilesAdminOptions options);

    /**
     * Returns the default file root of the specified container.  This will default to a path
     * relative to the first parent container with an override
     */
    File getDefaultRoot(Container c, boolean createDir);
    Path getDefaultRootPath(Container c, boolean createDir);

    class DefaultRootInfo
    {
        private final java.nio.file.Path _path;
        private final String _prettyStr;
        private final boolean _isCloud;
        private final String _cloudName;

        public DefaultRootInfo(java.nio.file.Path path, String prettyStr, boolean isCloud, String cloudName)
        {
            _path = path;
            _prettyStr = prettyStr;
            _isCloud = isCloud;
            _cloudName = cloudName;
        }

        public java.nio.file.Path getPath()
        {
            return _path;
        }

        public String getPrettyStr()
        {
            return _prettyStr;
        }

        public boolean isCloud()
        {
            return _isCloud;
        }

        public String getCloudName()
        {
            return _cloudName;
        }
    }

    DefaultRootInfo getDefaultRootInfo(Container container);

    String getDomainURI(Container c);

    String getDomainURI(Container c, FilesAdminOptions.fileConfig config);

    ExpData getDataObject(WebdavResource resource, Container c);
    QueryUpdateService getFilePropsUpdateService(TableInfo tinfo, Container container);

    void moveFileRoot(File prev, File dest, @Nullable User user, @Nullable Container container);

    /** Notifies all registered FileListeners that a file or directory has been created */
    void fireFileCreateEvent(@NotNull File created, @Nullable User user, @Nullable Container container);
    /** Notifies all registered FileListeners that a file or directory has moved */
    void fireFileMoveEvent(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container);
    /** Add a listener that will be notified when files are created or are moved */
    void addFileListener(FileListener listener);

    Map<String, Collection<File>> listFiles(@NotNull Container container);

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
    SQLFragment listFilesQuery(@NotNull User currentUser);

    void setWebfilesEnabled(boolean enabled, User user);

    /**
     * Return file's virtual folder path that's relative to container's file root. Roots are matched in order of @files, @pipeline and then each @filesets.
     * @param dataFileUrl The data file Url of file
     * @param container Container in the file system
     * @return folder relative to file root
     */
    String getDataFileRelativeFileRootPath(@NotNull String dataFileUrl, Container container);

    /**
     * Ensure an entry in the exp.data table exists for all files.
     * @param qus QueryUpdateService for exp.data
     * @param user user
     * @param container container
     */
    void ensureFileData(QueryUpdateService qus, @NotNull User user, @NotNull Container container);
}
