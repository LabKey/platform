/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.filecontent;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.ContainerListener;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TabContainerType;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.files.DirectoryPattern;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileListener;
import org.labkey.api.files.FileRoot;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * User: klum
 * Date: Dec 9, 2009
 */
public class FileContentServiceImpl implements FileContentService
{
    private static final Logger _log = LogManager.getLogger(FileContentServiceImpl.class);
    private static final String UPLOAD_LOG = ".upload.log";
    private static final String SCOPE_SITE_ROOT_SETTINGS = "SiteRootSettings";
    private static final FileContentServiceImpl INSTANCE = new FileContentServiceImpl();

    private final ContainerListener _containerListener = new FileContentServiceContainerListener();
    private final List<FileListener> _fileListeners = new CopyOnWriteArrayList<>();

    private final List<DirectoryPattern> _ziploaderPattern = new CopyOnWriteArrayList<>();

    private volatile boolean _fileRootSetViaStartupProperty = false;

    enum Props
    {
        root,
        rootDisabled,
    }

    enum FileAction
    {
        UPLOAD,
        DELETE
    }

    static FileContentServiceImpl getInstance()
    {
        return INSTANCE;
    }

    private FileContentServiceImpl()
    {
    }

    @Override
    @NotNull
    public List<Container> getContainersForFilePath(java.nio.file.Path path)
    {
        // Ignore cloud files for now
        if (FileUtil.hasCloudScheme(path))
            return Collections.emptyList();

        // If the path is under the default root, do optimistic simple match for containers under the default root
        File defaultRoot = getSiteDefaultRoot();
        java.nio.file.Path defaultRootPath = defaultRoot.toPath();
        if (path.startsWith(defaultRootPath))
        {
            java.nio.file.Path rel = defaultRootPath.relativize(path);
            if (rel.getNameCount() > 0)
            {
                Container root = ContainerManager.getRoot();
                Container next = root;
                while (rel.getNameCount() > 0)
                {
                    // check if there exists a child container that matches the next path segment
                    java.nio.file.Path top = rel.subpath(0, 1);
                    assert top != null;
                    Container child = root.getChild(top.getFileName().toString());
                    if (child == null)
                        break;

                    next = child;
                    rel = rel.subpath(1, rel.getNameCount() - 1);
                }

                if (next != null && !next.equals(root))
                {
                    // verify our naive file path is correct for the container -- it may have a file root other than the default
                    java.nio.file.Path fileRoot = getFileRootPath(next);
                    if (fileRoot != null && path.startsWith(fileRoot))
                        return Collections.singletonList(next);
                }
            }
        }

        // TODO: Create cache of file root and pipeline root paths -> list of containers

        return Collections.emptyList();
    }

    @Override
    public @Nullable File getFileRoot(@NotNull Container c, @NotNull ContentType type)
    {
        switch (type)
        {
            case files:
                String folderName = getFolderName(type);
                if (folderName == null)
                    folderName = "";

                java.nio.file.Path dir = getFileRootPath(c);
                return dir != null ? dir.resolve(folderName).toFile() : null;

            case pipeline:
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                return root != null ? root.getRootPath() : null;
        }
        return null;
    }

    @Override
    public @Nullable java.nio.file.Path getFileRootPath(@NotNull Container c, @NotNull ContentType type)
    {
        switch (type)
        {
            case files:
                java.nio.file.Path fileRootPath = getFileRootPath(c);
                if (null != fileRootPath && !FileUtil.hasCloudScheme(fileRootPath))  // Don't add @files when we're in the cloud
                    fileRootPath = fileRootPath.resolve(getFolderName(type));
                return fileRootPath;

            case pipeline:
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                return root != null ? root.getRootNioPath() : null;
        }
        return null;
    }

    // Returns full uri to file root for this container.  filePath is optional relative path to a file under the file root
    @Override
    public @Nullable URI getFileRootUri(@NotNull Container c, @NotNull ContentType type, @Nullable String filePath)
    {
        java.nio.file.Path root = FileContentService.get().getFileRootPath(c, FileContentService.ContentType.files);
        if (root != null)
        {
            String path = root.toString();
            if (filePath != null) {
                path += filePath;
            }

            // non-unix needs a leading slash
            if (!path.startsWith("/") && !path.startsWith("\\"))
            {
                path = "/" + path;
            }
            return FileUtil.createUri(path);
        }

        return null;
    }

    @Override
    public @Nullable File getFileRoot(@NotNull Container c)
    {
        java.nio.file.Path path = getFileRootPath(c);
        throwIfPathNotFile(path);
        return path.toFile();
    }

    @Override
    public @Nullable java.nio.file.Path getFileRootPath(@NotNull Container c)
    {
        if (c == null)
            return null;

        if (c.isRoot())
        {
            return getSiteDefaultRootPath();
        }

        if (!isFileRootDisabled(c))
        {
            FileRoot root = FileRootManager.get().getFileRoot(c);

            // check if there is a site wide file root
            if (root.getPath() == null || isUseDefaultRoot(c))
            {
                return getDefaultRootPath(c, true);
            }
            else
                return getNioPath(c, root.getPath());
        }
        return null;
    }

    @Override
    public File getDefaultRoot(Container c, boolean createDir)
    {
        return getDefaultRootPath(c, createDir).toFile();
    }

    @Override
    public java.nio.file.Path getDefaultRootPath(Container c, boolean createDir)
    {
        Container firstOverride = getFirstAncestorWithOverride(c);

        java.nio.file.Path parentRoot;
        if (firstOverride == null)
        {
            parentRoot = getSiteDefaultRoot().toPath();
            firstOverride = ContainerManager.getRoot();
        }
        else
        {
            parentRoot = getFileRootPath(firstOverride);
        }

        if (parentRoot != null && c != null && firstOverride != null)
        {
            java.nio.file.Path fileRootPath;
            if (FileUtil.hasCloudScheme(parentRoot))
            {
                // For cloud root, we don't have to create directories for this path
                fileRootPath = CloudStoreService.get().getPathForOtherContainer(firstOverride, c, FileUtil.pathToString(parentRoot), new Path(""));
            }
            else
            {
                // For local, the path may be several directories deep (since it matches the LK folder path), so we should create the directories for that path
                fileRootPath = new File(parentRoot.toFile(), getRelativePath(c, firstOverride)).toPath();

                try
                {
                    if (createDir && !Files.exists(fileRootPath))
                        Files.createDirectories(fileRootPath);
                }
                catch (IOException e)
                {
                    return null; //  throw new RuntimeException(e);    TODO: does returning null make certain tests, like TargetedMSQCGuideSetTest pass on Windows?
                }
            }

            return fileRootPath;
        }
        return null;
    }

    // Return pretty path string for defaultFileRoot and boolean true if defaultFileRoot is cloud
    @Override
    public DefaultRootInfo getDefaultRootInfo(Container container)
    {
        String defaultRoot = "";
        boolean isDefaultRootCloud = false;
        java.nio.file.Path defaultRootPath = getDefaultRootPath(container, false);
        String cloudName = null;
        if (defaultRootPath != null)
        {
            isDefaultRootCloud = FileUtil.hasCloudScheme(defaultRootPath);
            if (isDefaultRootCloud && !container.isProject())
            {
                FileRoot fileRoot = getDefaultFileRoot(container);
                if (null != fileRoot)
                    defaultRoot = fileRoot.getPath();
                if (null != defaultRoot)
                    cloudName = getCloudRootName(defaultRoot);
            }
            else
            {
                defaultRoot = FileUtil.getAbsolutePath(container, defaultRootPath.toUri());
            }
        }
        return new DefaultRootInfo(defaultRootPath, defaultRoot, isDefaultRootCloud, cloudName);
    }

    @Nullable
    // Get FileRoot associated with path returned form getDefaultRootPath()
    public FileRoot getDefaultFileRoot(Container c)
    {
        Container firstOverride = getFirstAncestorWithOverride(c);

        if (firstOverride == null)
            firstOverride = ContainerManager.getRoot();

        if (null != firstOverride)
            return FileRootManager.get().getFileRoot(firstOverride);
        return null;
    }

    private String getRelativePath(Container c, Container ancestor)
    {
        return c.getPath().replaceAll("^" + Pattern.quote(ancestor.getPath()), "");
    }

    //returns the first parent container that has a custom file root, or NULL if none have overrides
    private Container getFirstAncestorWithOverride(Container c)
    {
        Container toTest = c.getParent();
        if (toTest == null)
            return null;

        while (isUseDefaultRoot(toTest))
        {
            if (toTest == null || toTest.equals(ContainerManager.getRoot()))
                return null;

            toTest = toTest.getParent();
        }

        return toTest;
    }

    private java.nio.file.Path getNioPath(Container c, @NotNull String fileRootPath)
    {
        if (isCloudFileRoot(fileRootPath))
            return CloudStoreService.get().getPath(c, getCloudRootName(fileRootPath), new org.labkey.api.util.Path(""));

        return FileUtil.stringToPath(c, fileRootPath, false);       // fileRootPath is unencoded
    }

    private boolean isCloudFileRoot(String fileRootPseudoPath)
    {
        return StringUtils.startsWith(fileRootPseudoPath, FileContentService.CLOUD_ROOT_PREFIX);
    }

    private String getCloudRootName(@NotNull String fileRootPseudoPath)
    {
        return fileRootPseudoPath.substring(fileRootPseudoPath.indexOf(FileContentService.CLOUD_ROOT_PREFIX) + FileContentService.CLOUD_ROOT_PREFIX.length() + 1);
    }

    @Override
    public boolean isCloudRoot(Container c)
    {
        if (null != c)
        {
            java.nio.file.Path fileRootPath = getFileRootPath(c);
            return null != fileRootPath && FileUtil.hasCloudScheme(fileRootPath);
        }
        return false;
    }

    @Override
    @NotNull
    public String getCloudRootName(Container c)
    {
        if (null != c)
        {
            if (isCloudRoot(c))
            {
                FileRoot root = FileRootManager.get().getFileRoot(c);
                if (null == root.getPath() || isUseDefaultRoot(c))
                {
                    Container firstOverride = getFirstAncestorWithOverride(c);
                    if (null == firstOverride)
                        firstOverride = ContainerManager.getRoot();
                    root = FileRootManager.get().getFileRoot(firstOverride);
                    if (null == root.getPath())
                        return "";
                }
                return getCloudRootName(root.getPath());
            }
        }
        return "";
    }

    @Override
    public void setCloudRoot(@NotNull Container c, String cloudRootName)
    {
        _setFileRoot(c, FileContentService.CLOUD_ROOT_PREFIX + "/" + cloudRootName);
    }

    @Override
    public void setFileRoot(@NotNull Container c, @Nullable File path)
    {
        _setFileRoot(c, (null != path ? FileUtil.getAbsoluteCaseSensitiveFile(path).getAbsolutePath() : null));
    }

    @Override
    public void setFileRootPath(@NotNull Container c, @Nullable String strPath)
    {
        String absolutePath = null;
        if (strPath != null)
        {
            URI uri = FileUtil.createUri(strPath, false);      // strPath is unencoded
            if (FileUtil.hasCloudScheme(uri))
                absolutePath = FileUtil.getAbsolutePath(c, uri);
            else
                absolutePath = FileUtil.getAbsoluteCaseSensitiveFile(new File(uri)).getAbsolutePath();
        }
        _setFileRoot(c, absolutePath);
    }

    private void _setFileRoot(@NotNull Container c, @Nullable String absolutePath)
    {
        if (!c.isContainerFor(ContainerType.DataType.fileRoot))
            throw new IllegalArgumentException("File roots cannot be set for containers of type " + c.getContainerType().getName());
        
        FileRoot root = FileRootManager.get().getFileRoot(c);
        root.setEnabled(true);

        String oldValue = root.getPath();
        String newValue = null;

        // clear out the root
        if (absolutePath == null)
            root.setPath(null);
        else
        {
            root.setPath(absolutePath);
            newValue = root.getPath();
        }

        FileRootManager.get().saveFileRoot(null, root);
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.WebRoot, oldValue, newValue);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    @Override
    public void disableFileRoot(Container container)
    {
        if (container == null || container.isRoot())
            throw new IllegalArgumentException("Disabling either a null project or the root project is not allowed.");

        Container effective = container.getContainerFor(ContainerType.DataType.fileRoot);
        if (effective != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(effective);
            String oldValue = root.getPath();
            root.setEnabled(false);
            FileRootManager.get().saveFileRoot(null, root);

            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    container, ContainerManager.Property.WebRoot, oldValue, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
    }

    @Override
    public boolean isFileRootDisabled(Container c)
    {
        Container effective = c.getContainerFor(ContainerType.DataType.fileRoot);
        if (null == effective)
            return false;

        FileRoot root = FileRootManager.get().getFileRoot(effective);
        return !root.isEnabled();
    }

    @Override
    public boolean isUseDefaultRoot(Container c)
    {
        if (c == null)
            return true;
        
        Container effective = c.getContainerFor(ContainerType.DataType.fileRoot);
        if (null == effective)
            return true;

        FileRoot root = FileRootManager.get().getFileRoot(effective);
        return root.isUseDefault() || StringUtils.isEmpty(root.getPath());
    }

    @Override
    public void setIsUseDefaultRoot(Container c, boolean useDefaultRoot)
    {
        Container effective = c.getContainerFor(ContainerType.DataType.fileRoot);
        if (effective != null)
        {
            FileRoot root = FileRootManager.get().getFileRoot(effective);
            String oldValue = root.getPath();
            root.setEnabled(true);
            root.setUseDefault(useDefaultRoot);
            if (useDefaultRoot)
                root.setPath(null);
            FileRootManager.get().saveFileRoot(null, root);

            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    effective, ContainerManager.Property.WebRoot, oldValue, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
    }

    @Override
    public @NotNull java.nio.file.Path getSiteDefaultRootPath()
    {
        return getSiteDefaultRoot().toPath();
    }

    @Override
    public @NotNull File getSiteDefaultRoot()
    {
        // Site default is always on file system
        File root = AppProps.getInstance().getFileSystemRoot();

        if (root == null || !root.exists())
            root = getDefaultRoot();

        if (!root.exists())
            root.mkdirs();

        return root;
    }

    @Override
    public @NotNull File getUserFilesRoot()
    {
        // Always on the file system
        File root = AppProps.getInstance().getUserFilesRoot();

        if (root == null || !root.exists())
            root = getDefaultRoot();

        if (!root.exists())
            root.mkdirs();

        return root;
    }

    private @NotNull File getDefaultRoot()
    {
        File explodedPath = ModuleLoader.getInstance().getCoreModule().getExplodedPath();

        File root = explodedPath.getParentFile();
        if (root != null)
        {
            if (root.getParentFile() != null)
                root = root.getParentFile();
        }
        File defaultRoot = new File(root, "files");
        if (!defaultRoot.exists())
            defaultRoot.mkdirs();

        return defaultRoot;
    }

    @Override
    public void setSiteDefaultRoot(File root, User user)
    {
        if (root == null)
            throw new IllegalArgumentException("Invalid site root: specified root is null");

        if (!root.exists())
            throw new IllegalArgumentException("Invalid site root: " + root.getAbsolutePath() + " does not exist");

        File prevRoot = getSiteDefaultRoot();
        WriteableAppProps props = AppProps.getWriteableInstance();

        props.setFileSystemRoot(root.getAbsolutePath());
        props.save(user);

        FileRootManager.get().clearCache();
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                ContainerManager.getRoot(), ContainerManager.Property.SiteRoot, prevRoot, root);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    @Override
    public void setUserFilesRoot(File root, User user)
    {
        if (root == null || !root.exists())
            throw new IllegalArgumentException("Invalid site root: does not exist");

        File prevRoot = getUserFilesRoot();
        WriteableAppProps props = AppProps.getWriteableInstance();

        props.setUserFilesRoot(root.getAbsolutePath());
        props.save(user);

        FileRootManager.get().clearCache();
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                ContainerManager.getRoot(), ContainerManager.Property.UserFilesRoot, prevRoot, root);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    @Override
    public void setWebfilesEnabled(boolean enabled, User user)
    {
        WriteableAppProps props = AppProps.getWriteableInstance();
        props.setWebfilesEnabled(enabled);
        props.save(user);
    }

    @Override
    public FileSystemAttachmentParent registerDirectory(Container c, String name, String path, boolean relative)
    {
        FileSystemAttachmentParent parent = new FileSystemAttachmentParent();
        parent.setContainer(c);
        if (null == name)
            name = path;
        parent.setName(name);
        parent.setPath(path);
        parent.setRelative(relative);
        //We do this because insert does not return new fields
        parent.setEntityid(GUID.makeGUID());

        FileSystemAttachmentParent ret = Table.insert(HttpView.currentContext().getUser(), CoreSchema.getInstance().getMappedDirectories(), parent);
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.AttachmentDirectory, null, ret);
        ContainerManager.firePropertyChangeEvent(evt);
        return ret;
    }

    @Override
    public void unregisterDirectory(Container c, String name)
    {
        FileSystemAttachmentParent parent = getRegisteredDirectory(c, name);
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Name"), name);
        Table.delete(CoreSchema.getInstance().getMappedDirectories(), filter);
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.AttachmentDirectory, parent, null);
        ContainerManager.firePropertyChangeEvent(evt);
    }

    @Override
    @Nullable
    public AttachmentDirectory getMappedAttachmentDirectory(Container c, boolean createDir) throws UnsetRootDirectoryException
    {
        try
        {
            if (createDir) //force create
                getMappedDirectory(c, true);
            else if (null == getMappedDirectory(c, false))
                return null;

            return new FileSystemAttachmentParent(c, ContentType.files);
        }
        catch (IOException e)
        {
            _log.error("Cannot get mapped directory for " + c.getPath(), e);
            return null;
        }
    }

    public java.nio.file.Path getMappedDirectory(Container c, boolean create) throws UnsetRootDirectoryException, IOException
    {
        java.nio.file.Path root = getFileRootPath(c);
        if (!FileUtil.hasCloudScheme(root))
        {
            if (null == root)
            {
                if (create)
                    throw new UnsetRootDirectoryException(c.isRoot() ? c : c.getProject());
                else
                    return null;
            }

            if (!Files.exists(root))
            {
                if (create)
                    throw new MissingRootDirectoryException(c.isRoot() ? c : c.getProject(), root);
                else
                    return null;

            }
        }
        return root;
    }

    @Override
    public FileSystemAttachmentParent getRegisteredDirectory(Container c, String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Name"), name);

        return new TableSelector(CoreSchema.getInstance().getMappedDirectories(), filter, null).getObject(FileSystemAttachmentParent.class);
    }

    @Override
    public FileSystemAttachmentParent getRegisteredDirectoryFromEntityId(Container c, String entityId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("EntityId"), entityId);

        return new TableSelector(CoreSchema.getInstance().getMappedDirectories(), filter, null).getObject(FileSystemAttachmentParent.class);
    }

    @Override
    public @NotNull Collection<AttachmentDirectory> getRegisteredDirectories(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);

        return Collections.unmodifiableCollection(new TableSelector(CoreSchema.getInstance().getMappedDirectories(), filter, null).getCollection(FileSystemAttachmentParent.class));
    }

    private class FileContentServiceContainerListener implements ContainerListener
    {
        @Override
        public void containerCreated(Container c, User user)
        {
            try
            {
                // Will create directory if it's a default dir
                getMappedDirectory(c, false);
            }
            catch (IOException ex)
            {
            /* */
            }
        }

        @Override
        public void containerDeleted(Container c, User user)
        {
            java.nio.file.Path dir = null;
            try
            {
                // don't delete the file contents if they have a project override
                if (isUseDefaultRoot(c) && !isCloudRoot(c))         // Don't do anything for cloud root here. CloudContainerListener will handle
                    dir = getMappedDirectory(c, false);

                if (null != dir)
                {
                    FileUtil.deleteDir(dir);
                }
            }
            catch (Exception e)
            {
                _log.error("containerDeleted", e);
            }

            ContainerUtil.purgeTable(CoreSchema.getInstance().getMappedDirectories(), c, null);
        }

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {
            /* **** Cases:
                SRC                         DEST
                specific local path         same -- no work
                specific cloud path         same -- no work
                local default               local default -- move tree
                local default               cloud default -- move tree
                cloud default               local default -- move tree
                cloud default               cloud default -- if change bucket, move tree
             *************************************************************/
            if (isUseDefaultRoot(c))
            {
                java.nio.file.Path srcParent = getFileRootPath(oldParent);
                java.nio.file.Path dest = getFileRootPath(c);
                if (null != srcParent && null != dest)
                {
                    if (!FileUtil.hasCloudScheme(srcParent))
                    {
                        File src = new File(srcParent.toFile(), c.getName());
                        if (src.exists())
                        {
                            if (!FileUtil.hasCloudScheme(dest))
                            {
                                // local -> local
                                moveFileRoot(src, dest.toFile(), user, c);
                            }
                            else
                            {
                                // local -> cloud; source starts under @files
                                File filesSrc = new File(src, FILES_LINK);
                                if (filesSrc.exists())
                                    moveFileRoot(filesSrc.toPath(), dest, user, c);
                                FileUtil.deleteDir(src);        // moveFileRoot will delete @files, but we need to delete its parent
                            }
                        }
                    }
                    else
                    {
                        // Get source path using moving container and parent's config (cloudRoot), because that config must be the source config
                        java.nio.file.Path src = CloudStoreService.get().getPath(c, getCloudRootName(oldParent), new Path(""));
                        if (!FileUtil.hasCloudScheme(dest))
                        {
                            // cloud -> local; destination is under @files
                            dest = dest.resolve(FILES_LINK);
                            moveFileRoot(src, dest, user, c);
                        }
                        else
                        {
                            // cloud -> cloud
                            if (!getCloudRootName(oldParent).equals(getCloudRootName(c)))
                            {
                                // Different configs
                                moveFileRoot(src, dest, user, c);
                            }
                        }
                    }
                }
            }
        }

        @NotNull
        @Override
        public Collection<String> canMove(Container c, Container newParent, User user)
        {
            return Collections.emptyList();
        }

        @Override
        public void propertyChange(PropertyChangeEvent propertyChangeEvent)
        {
            ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)propertyChangeEvent;
            Container c = evt.container;

            switch (evt.property)
            {
                case Name:          // container rename event
                {
                    String oldValue = (String) propertyChangeEvent.getOldValue();
                    String newValue = (String) propertyChangeEvent.getNewValue();

                    java.nio.file.Path location = null;
                    try
                    {
                        location = getMappedDirectory(c, false);
                    }
                    catch (IOException ex)
                    {
                        _log.error(ex);
                    }
                    if (location != null && !FileUtil.hasCloudScheme(location))    // If cloud, folder name for container not dependent on Name
                    {
                        //Don't rely on container object. Seems not to point to the
                        //new location even AFTER rename. Just construct new file paths
                        File locationFile = location.toFile();
                        File parentDir = locationFile.getParentFile();
                        File oldLocation = new File(parentDir, oldValue);
                        File newLocation = new File(parentDir, newValue);
                        if (newLocation.exists())
                            moveToDeleted(newLocation);

                        if (oldLocation.exists())
                        {
                            oldLocation.renameTo(newLocation);
                            fireFileMoveEvent(oldLocation, newLocation, evt.user, evt.container);
                        }
                    }
                    break;
                }
            }
        }
    }


    @Override
    public @Nullable String getFolderName(FileContentService.ContentType type)
    {
        if (type != null)
            return "@" + type.name();
        return null;
    }


    /**
     * Move the file or directory into a ".deleted" directory under the parent directory.
     * @return True if successfully moved.
     */
    private static boolean moveToDeleted(File fileToMove)
    {
        if (!fileToMove.exists())
            return false;

        File parent = fileToMove.getParentFile();

        File deletedDir = new File(parent, ".deleted");
        if (!deletedDir.exists())
            if (!deletedDir.mkdir())
                return false;

        File newLocation = new File(deletedDir, fileToMove.getName());
        if (newLocation.exists())
            FileUtil.deleteDir(newLocation);

        return fileToMove.renameTo(newLocation);
    }

    static void logFileAction(java.nio.file.Path directory, String fileName, FileAction action, User user)
    {
        try (BufferedWriter fw = Files.newBufferedWriter(directory.resolve(UPLOAD_LOG), StandardOpenOption.APPEND, StandardOpenOption.CREATE))
        {
            fw.write(action.toString() + "\t" + fileName + "\t" + new Date() + "\t" + (user == null ? "(unknown)" : user.getEmail()) + "\n");
        }
        catch (Exception x)
        {
            //Just log it.
            _log.error(x);
        }
    }

    @Override
    public FilesAdminOptions getAdminOptions(Container c)
    {
        FileRoot root = FileRootManager.get().getFileRoot(c);
        String xml = null;

        if (!StringUtils.isBlank(root.getProperties()))
        {
            xml = root.getProperties();
        }
        return new FilesAdminOptions(c, xml);
    }

    @Override
    public void setAdminOptions(Container c, FilesAdminOptions options)
    {
        if (options != null)
        {
            setAdminOptions(c, options.serialize());
        }
    }

    @Override
    public void setAdminOptions(Container c, String properties)
    {
        FileRoot root = FileRootManager.get().getFileRoot(c);

        root.setProperties(properties);
        FileRootManager.get().saveFileRoot(null, root);
    }

    public static final String NAMESPACE_PREFIX = "FileProperties";
    public static final String PROPERTIES_DOMAIN = "File Properties";
    public static final String TYPE_PROPERTIES = "FileProperties";

    @Override
    public String getDomainURI(Container container)
    {
        return getDomainURI(container, getAdminOptions(container).getFileConfig());
    }

    @Override
    public String getDomainURI(Container container, FilesAdminOptions.fileConfig config)
    {
        while (config == FilesAdminOptions.fileConfig.useParent && container != container.getParent())
        {
            container = container.getParent();
            config = getAdminOptions(container).getFileConfig();
        }

        //String typeURI = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":List" + ".Folder-" + container.getRowId() + ":" + name;

        return new Lsid("urn:lsid:labkey.com:" + NAMESPACE_PREFIX + ".Folder-" + container.getRowId() + ':' + TYPE_PROPERTIES).toString();
    }

    @Override
    public ExpData getDataObject(WebdavResource resource, Container c)
    {
        return getDataObject(resource, c, null, false);
    }

    private static ExpData getDataObject(WebdavResource resource, Container c, User user, boolean create)
    {
        // TODO: S3: seems to only be called from Search and currently we're not searching in cloud. SaveCustomPropsAction seems unused
        if (resource != null)
        {
            File file = resource.getFile();
            ExpData data = ExperimentService.get().getExpDataByURL(file, c);

            if (data == null && create)
            {
                data = ExperimentService.get().createData(c, FileContentService.UPLOADED_FILE);
                data.setName(file.getName());
                data.setDataFileURI(file.toURI());
                data.save(user);
            }
            return data;
        }
        return null;
    }

    @Override
    public QueryUpdateService getFilePropsUpdateService(TableInfo tinfo, Container container)
    {
        return new FileQueryUpdateService(tinfo, container);
    }

    @Override
    public boolean isValidProjectRoot(String root)
    {
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
        {
            return false;
        }
        return true;
    }

    @Override
    public void moveFileRoot(java.nio.file.Path prev, java.nio.file.Path dest, @Nullable User user, @Nullable Container container)
    {
        if (!FileUtil.hasCloudScheme(prev) && !FileUtil.hasCloudScheme(dest))
        {
            moveFileRoot(prev.toFile(), dest.toFile(), user, container);    // Both files; try rename
        }
        else
        {
            try
            {
                // At least one is in the cloud
                FileUtil.copyDirectory(prev, dest);
                FileUtil.deleteDir(prev);                          // TODO use more efficient delete
                fireFileMoveEvent(prev, dest, user, container);
            }
            catch (IOException e)
            {
                _log.error("error occurred moving the file root", e);
            }
        }
    }

    @Override
    public void moveFileRoot(File prev, File dest, @Nullable User user, @Nullable Container container)
    {
        try
        {
            _log.info("moving " + prev.getPath() + " to " + dest.getPath());
            boolean doRename = true;

            // Our best bet for perf is to to a rename, which doesn't require creating an actual copy.
            // If it exists, try deleting the target directory, which will only succeed if it's empty, but would
            // enable using renameTo() method. Don't delete if it's a symbolic link, since it wouldn't be recreated
            // in the same way.
            if (dest.exists() && !Files.isSymbolicLink(dest.toPath()))
                doRename = dest.delete();

            if (doRename && !prev.renameTo(dest))
            {
                _log.info("rename failed, attempting to copy");

                //listFiles can return null, which could cause a NPE
                File[] children = prev.listFiles();
                if (children != null)
                {
                    for (File file : children)
                        FileUtil.copyBranch(file, dest);
                }
                FileUtil.deleteDir(prev);
            }
            fireFileMoveEvent(prev, dest, user, container);
        }
        catch (IOException e)
        {
            _log.error("error occurred moving the file root", e);
        }
    }

    @Override
    public void fireFileCreateEvent(@NotNull File created, @Nullable User user, @Nullable Container container)
    {
        fireFileCreateEvent(created.toPath(), user, container);
    }

    @Override
    public void fireFileCreateEvent(@NotNull java.nio.file.Path created, @Nullable User user, @Nullable Container container)
    {
        java.nio.file.Path absPath = FileUtil.getAbsoluteCaseSensitivePath(container, created);
        for (FileListener fileListener : _fileListeners)
        {
            fileListener.fileCreated(absPath, user, container);
        }
    }

    @Override
    public int fireFileMoveEvent(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
    {
        return fireFileMoveEvent(src.toPath(), dest.toPath(), user, container);
    }

    @Override
    public int fireFileMoveEvent(@NotNull java.nio.file.Path src, @NotNull java.nio.file.Path dest, @Nullable User user, @Nullable Container container)
    {
        // Make sure that we've got the best representation of the file that we can
        java.nio.file.Path absSrc = FileUtil.getAbsoluteCaseSensitivePath(container, src);
        java.nio.file.Path absDest = FileUtil.getAbsoluteCaseSensitivePath(container, dest);
        int result = 0;
        for (FileListener fileListener : _fileListeners)
        {
            result += fileListener.fileMoved(absSrc, absDest, user, container);
        }
        return result;
    }

    @Override
    public void addFileListener(FileListener listener)
    {
        _fileListeners.add(listener);
    }

    @Override
    public Map<String, Collection<File>> listFiles(@NotNull Container container)
    {
        Map<String, Collection<File>> files = new LinkedHashMap<>();
        for (FileListener fileListener : _fileListeners)
        {
            files.put(fileListener.getSourceName(), new HashSet<>(fileListener.listFiles(container)));
        }
        return files;
    }

    @Override
    public SQLFragment listFilesQuery(@NotNull User currentUser)
    {
        SQLFragment frag = new SQLFragment();
        if (currentUser == null || !currentUser.hasSiteAdminPermission())
        {
            frag.append("SELECT\n");
            frag.append("  CAST(NULL AS VARCHAR) AS Container,\n");
            frag.append("  NULL AS Created,\n");
            frag.append("  NULL AS CreatedBy,\n");
            frag.append("  NULL AS Modified,\n");
            frag.append("  NULL AS ModifiedBy,\n");
            frag.append("  NULL AS FilePath,\n");
            frag.append("  NULL AS SourceKey,\n");
            frag.append("  NULL AS SourceName\n");
            frag.append("WHERE 1 = 0");
        }
        else
        {
            String union = "";
            frag.append("(");
            for (FileListener fileListener : _fileListeners)
            {
                SQLFragment subselect = fileListener.listFilesQuery();
                if (subselect != null)
                {
                    frag.append(union);
                    frag.append(subselect);
                    union = "UNION\n";
                }
            }
            frag.append(")");
        }
        return frag;
    }

    // TODO: Delete this in favor of SiteSettings.siteFileRoot
    @Deprecated
    public enum SiteRootStartupProperties implements StartupProperty
    {
        siteRootFile
        {
            @Override
            public String getDescription()
            {
                return "Site-level file root. DO NOT USE... use SiteSettings.siteFileRoot instead.";
            }
        }
    }

    // TODO: Delete this in favor of SiteSettingsProperties.webRoot
    public static class SiteRootStartupPropertyHandler extends StandardStartupPropertyHandler<SiteRootStartupProperties>
    {
        public SiteRootStartupPropertyHandler()
        {
            super(SCOPE_SITE_ROOT_SETTINGS, SiteRootStartupProperties.class);
        }

        @Override
        public void handle(Map<SiteRootStartupProperties, StartupPropertyEntry> map)
        {
            StartupPropertyEntry entry = map.get(SiteRootStartupProperties.siteRootFile);
            if (null != entry)
            {
                _log.warn("Support for SiteRootSettings.siteRootFile will be removed soon; use SiteSettings.siteFileRoot instead.");
                File fileRoot = new File(entry.getValue());
                FileContentService.get().setSiteDefaultRoot(fileRoot, null);
                FileContentService.get().setFileRootSetViaStartupProperty(true);
            }
        }
    }

    public void setFileRootSetViaStartupProperty(boolean fileRootSetViaStartupProperty)
    {
        _fileRootSetViaStartupProperty = fileRootSetViaStartupProperty;
    }

    @Override
    public boolean isFileRootSetViaStartupProperty()
    {
        return _fileRootSetViaStartupProperty;
    }

    public static void populateSiteRootFileWithStartupProps()
    {
        // populate the site root file settings with values read from startup properties
        // expects startup properties formatted like: FileSiteRootSettings.fileRoot;bootstrap=/labkey/labkey/files
        // if more than one FileSiteRootSettings.siteRootFile specified in the startup properties file then the last one overrides the previous ones
        ModuleLoader.getInstance().handleStartupProperties(new SiteRootStartupPropertyHandler());
    }

    public ContainerListener getContainerListener()
    {
        return _containerListener;
    }

    public Set<Map<String, Object>> getNodes(boolean isShowOverridesOnly, @Nullable String browseUrl, @Nullable String showAdminUrl, Container c)
    {
        Set<Map<String, Object>> children = new LinkedHashSet<>();

        try {
            AttachmentDirectory root = getMappedAttachmentDirectory(c, false);

            if (root != null)
            {
                boolean isDefault = isUseDefaultRoot(c);
                if (!isDefault || !isShowOverridesOnly)
                {
                    ActionURL config = PageFlowUtil.urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
                    Map<String, Object> node = createFileSetNode(c, FILES_LINK, root.getFileSystemDirectoryPath());
                    node.put("default", isUseDefaultRoot(c));
                    node.put("configureURL", config.getEncodedLocalURIString());
                    node.put("browseURL", browseUrl);
                    node.put("webdavURL", FilesWebPart.getRootPath(c, FILES_LINK));

                    children.add(node);
                }
            }

            for (AttachmentDirectory fileSet : getRegisteredDirectories(c))
            {
                ActionURL config = new ActionURL(FileContentController.ShowAdminAction.class, c);
                Map<String, Object> node =  createFileSetNode(c, fileSet.getName(), fileSet.getFileSystemDirectoryPath());
                node.put("configureURL", config.getEncodedLocalURIString());
                node.put("browseURL", browseUrl);
                node.put("webdavURL", FilesWebPart.getRootPath(c, FILE_SETS_LINK, fileSet.getName()));
                node.put("rootType", "fileset");

                children.add(node);
            }

            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);
            if (pipeRoot != null)
            {
                boolean isDefault = PipelineService.get().hasSiteDefaultRoot(c);
                if (!isDefault || !isShowOverridesOnly)
                {
                    ActionURL config = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
                    ActionURL pipelineBrowse = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c, null);
                    Map<String, Object> node = createFileSetNode(c, PIPELINE_LINK, pipeRoot.getRootNioPath());
                    node.put("default", isDefault );
                    node.put("configureURL", config.getEncodedLocalURIString());
                    node.put("browseURL", pipelineBrowse.getEncodedLocalURIString());
                    node.put("webdavURL", FilesWebPart.getRootPath(c, PIPELINE_LINK));

                    children.add(node);
                }
            }
        }
        catch (IOException | UnsetRootDirectoryException e)
        {

        }
        return children;
    }

    protected Map<String, Object> createFileSetNode(Container container, String name, java.nio.file.Path dir)
    {
        Map<String, Object> node = new HashMap<>();
        if (dir != null)
        {
            node.put("name", name);
            node.put("path", FileUtil.getAbsolutePath(container, dir));
            node.put("leaf", true);
        }
        return node;
    }

    public String getAbsolutePathFromDataFileUrl(String dataFileUrl, Container container)
    {
        return FileUtil.getAbsolutePath(container, FileUtil.createUri(dataFileUrl));
    }

    @Nullable
    @Override
    public String getWebDavUrl(java.nio.file.@NotNull Path path, @NotNull Container container, @NotNull PathType type)
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(container);

        if (root == null)
            return null;

        try
        {
            path = path.toAbsolutePath();

            // currently, only report if the file is under the parent container
            if (root.isUnderRoot(path))
            {
                String relPath = root.relativePath(path);
                if (relPath == null)
                    return null;

                if(!isCloudRoot(container))
                {
                    relPath = Path.parse(FilenameUtils.separatorsToUnix(relPath)).encode();
                }
                else
                {
                    // Do not encode path from S3 folder.  It is already encoded.
                    relPath = Path.parse(FilenameUtils.separatorsToUnix(relPath)).toString();
                }

                return switch (type)
                        {
                            case folderRelative -> relPath;
                            case serverRelative -> Path.parse(root.getWebdavURL()).encode() + relPath;
                            case full -> AppProps.getInstance().getBaseServerUrl() + Path.parse(root.getWebdavURL()).encode() + relPath;
                            default -> throw new IllegalArgumentException("Unexpected path type: " + type);
                        };
            }
        }
        catch (InvalidPathException e)
        {
            _log.error("Invalid WebDav URL from: " + path, e);
        }

        return null;
    }

    @Override
    public String getDataFileRelativeFileRootPath(@NotNull String dataFileUrl, Container container)
    {
        Set<Map<String, Object>> children = getNodes(false, null, null, container);
        String filesRoot = null; // the path for @files
        for (Map<String, Object> child : children)
        {
            String rootName = (String) child.get("name");
            String rootPath = (String) child.get("path");

            // skip default @pipeline, which is the same as @files
            if (PIPELINE_LINK.equals(rootName))
            {
                 if((boolean) child.get("default") || rootPath.equals(filesRoot))
                     continue;
            }

            if (FILES_LINK.equals(rootName))
                filesRoot = rootPath;

            String absoluteFilePath = getAbsolutePathFromDataFileUrl(dataFileUrl, container);
            if (StringUtils.startsWith(absoluteFilePath, rootPath))
            {
                String offset = absoluteFilePath.replace(rootPath, "").replace("\\", "/");
                int lastSlash = offset.lastIndexOf("/");
                if (lastSlash <= 0)
                    return "/";
                else
                    return offset.substring(0, lastSlash);
            }
        }
        return null;
    }

    @Override
    public void ensureFileData(@NotNull ExpDataTable table)
    {
        Container container = table.getUserSchema().getContainer();
        User user = table.getUserSchema().getUser();
        QueryUpdateService qus = table.getUpdateService();
        if (qus == null)
        {
            throw new IllegalArgumentException("getUpdateServer() returned null from " + table);
        }

        synchronized (_fileDataUpToDateCache)
        {
            if (_fileDataUpToDateCache.get(container) != null) // already synced in the past 5 minutes, skip
                return;

            _fileDataUpToDateCache.put(container, true);
        }

        List<String> existingDataFileUrls = getDataFileUrls(container);
        Collection<AttachmentDirectory> filesets = getRegisteredDirectories(container);
        Set<Map<String, Object>> children = getNodes(false, null, null, container);
        String filesRoot = null; // the path for @files
        for (Map<String, Object> child : children)
        {
            String rootName = (String) child.get("name");
            String rootPathVal = (String) child.get("path");

            // skip default @pipeline, which is the same as @files
            if (PIPELINE_LINK.equals(rootName))
            {
                if((boolean) child.get("default") || rootPathVal.equals(filesRoot))
                    continue;
            }

            if (FILES_LINK.equals(rootName))
                filesRoot = rootPathVal;

            String rootDavUrl = (String) child.get("webdavURL");

            WebdavResource resource = getResource(rootDavUrl);
            if (resource == null)
                continue;

            List<Map<String, Object>> rows = new ArrayList<>();
            BatchValidationException errors = new BatchValidationException();
            File file = resource.getFile();

            if (file == null)
            {
                String rootType = (String) child.get("rootType");
                if ("fileset".equals(rootType))
                {
                    for (AttachmentDirectory fileset : filesets)
                    {
                        if (fileset.getName().equals(rootName))
                        {
                            try
                            {
                                file = fileset.getFileSystemDirectory();
                            }
                            catch (MissingRootDirectoryException e)
                            {
                                _log.error("Unable to list files for fileset: " + rootName, e);
                            }
                            break;
                        }
                    }
                }
            }

            if (file == null)
                return;

            try (var ignore = SpringActionController.ignoreSqlUpdates())
            {
                java.nio.file.Path rootPath = file.toPath();
                Files.walk(rootPath, 100) // prevent symlink loop
                        .filter(path -> !Files.isSymbolicLink(path) && path.compareTo(rootPath) != 0) // exclude symlink & root
                        .forEach(path -> {
                            if (!containsUrlOrVariation(existingDataFileUrls, path))
                                rows.add(new CaseInsensitiveHashMap<>(Collections.singletonMap("DataFileUrl", path.toUri().toString())));

                        });

                qus.insertRows(user, container, rows, errors, null, null);
            }
            catch (Exception e)
            {
                _log.error("Error listing content of directory: " + file.getAbsolutePath(), e);
            }
        }
    }


    @Override
    public void addZiploaderPattern(DirectoryPattern directoryPattern)
    {
       _ziploaderPattern.add(directoryPattern);
    }

    @Override
    public List<DirectoryPattern> getZiploaderPatterns(Container container)
    {
        List<DirectoryPattern> registeredPatterns = new ArrayList<>();
        for(Module module : container.getActiveModules())
        {
            _ziploaderPattern.forEach(p -> {
                if(p.getModule().getName().equalsIgnoreCase(module.getName()))
                    registeredPatterns.add(p);
            });
        }
        return registeredPatterns;
    }

    public List<String> getDataFileUrls(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("DataFileUrl"), null, CompareType.NONBLANK);
        TableSelector selector = new TableSelector(ExperimentService.get().getTinfoData(), Collections.singleton("DataFileUrl"), filter, null);
        return selector.getArrayList(String.class);
    }

    public Path getPath(String uri)
    {
        Path path = Path.decode(uri);

        if (!path.startsWith(WebdavService.getPath()) && path.contains(WebdavService.getPath().getName()))
        {
            String newPath = path.toString();
            int idx = newPath.indexOf(WebdavService.getPath().toString());

            if (idx != -1)
            {
                newPath = newPath.substring(idx);
                path = Path.parse(newPath);
            }
        }
        return path;
    }

    @Nullable
    public WebdavResource getResource(String uri)
    {
        Path path = getPath(uri);
        return WebdavService.get().getResolver().lookup(path);
    }

    public static void throwIfPathNotFile(java.nio.file.Path path)
    {
        if (null == path || FileUtil.hasCloudScheme(path))
            throw new RuntimeException("Cannot get File object from Cloud File Root.");    // TODO: new exception?
    }

    private boolean containsUrlOrVariation(List<String> existingUrls, java.nio.file.Path path)
    {
        String url = path.toUri().toString();
        if (existingUrls.contains(url))
            return true;

        boolean urlHasTrailingSlash = (Files.isDirectory(path) && (url.endsWith("/") || url.endsWith(File.pathSeparator)));
        if (urlHasTrailingSlash && existingUrls.contains(url.substring(0, url.length() - 1)))
            return true;

        if (!FileUtil.hasCloudScheme(path))
        {
            File file = path.toFile();
            String legacyUrl = file.toURI().toString();
            if (existingUrls.contains(legacyUrl))      // Legacy URI format (file:/users/...)
                return true;

            if (existingUrls.contains(file.getPath()))
                return true;
        }
        return false;
    }

    // Cache with short-lived entries so that exp.files can perform reasonably
    private static final Cache<Container, Boolean> _fileDataUpToDateCache = CacheManager.getCache(CacheManager.UNLIMITED, 5 * CacheManager.MINUTE, "Files");

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends AssertionError
    {
        private static final String TRICKY_CHARACTERS_FOR_PROJECT_NAMES = "\u2603~!@$&()_+{}-=[],.#\u00E4\u00F6\u00FC";

        private static final String PROJECT1 = "FileRootTestProject1" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
        private static final String PROJECT1_SUBFOLDER1 = "Subfolder1";
        private static final String PROJECT1_SUBFOLDER2 = "Subfolder2" + TRICKY_CHARACTERS_FOR_PROJECT_NAMES;
        private static final String PROJECT1_SUBSUBFOLDER = "SubSubfolder";
        private static final String PROJECT1_SUBSUBFOLDER_SIBLING = "SubSubfolderSibling";
        private static final String PROJECT2 = "FileRootTestProject2";

        private static final String FILE_ROOT_SUFFIX = "_FileRootTest";
        private static final String TXT_FILE = "FileContentTestFile.txt";

        private Map<Container, File> _expectedPaths;

        @Test
        public void fileRootsTest()
        {
            //pre-clean
            cleanup();

            _expectedPaths = new HashMap<>();

            FileContentService svc = FileContentService.get();
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT1);
            _expectedPaths.put(project1, null);

            Container project2 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT2);
            _expectedPaths.put(project2, null);

            Container subfolder1 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER1);
            _expectedPaths.put(subfolder1, null);

            Container subfolder2 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER2);
            _expectedPaths.put(subfolder2, null);

            Container subsubfolder = ContainerManager.createContainer(subfolder1, PROJECT1_SUBSUBFOLDER);
            _expectedPaths.put(subsubfolder, null);

            //set custom root on project, then expect children to inherit
            File testRoot = getTestRoot();

            svc.setFileRoot(project1, testRoot);
            _expectedPaths.put(project1, testRoot);

            //the subfolder should inherit from the parent
            _expectedPaths.put(subfolder1, new File(testRoot, subfolder1.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(subfolder1), svc.getDefaultRoot(subfolder1, false));
            assertPathsEqual("Subfolder1 has incorrect root", _expectedPaths.get(subfolder1), svc.getFileRoot(subfolder1));

            _expectedPaths.put(subfolder2, new File(testRoot, subfolder2.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(subfolder2), svc.getDefaultRoot(subfolder2, false));
            assertPathsEqual("Subfolder2 has incorrect root", _expectedPaths.get(subfolder2), svc.getFileRoot(subfolder2));

            _expectedPaths.put(subsubfolder, new File(_expectedPaths.get(subfolder1), subsubfolder.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(subsubfolder), svc.getDefaultRoot(subsubfolder, false));
            assertPathsEqual("SubSubfolder has incorrect root", _expectedPaths.get(subsubfolder), svc.getFileRoot(subsubfolder));

            //override root on 1st child, expect children of that folder to inherit
            _expectedPaths.put(subfolder1, new File(testRoot, "CustomSubfolder"));
            _expectedPaths.get(subfolder1).mkdirs();
            svc.setFileRoot(subfolder1, _expectedPaths.get(subfolder1));
            assertPathsEqual("SubSubfolder has incorrect root", new File(_expectedPaths.get(subfolder1), subsubfolder.getName()), svc.getFileRoot(subsubfolder));

            //reset project, we assume overridden child roots to remain the same
            svc.setFileRoot(project1, null);
            assertPathsEqual("Subfolder1 has incorrect root", _expectedPaths.get(subfolder1), svc.getFileRoot(subfolder1));
            assertPathsEqual("SubSubfolder has incorrect root", new File(_expectedPaths.get(subfolder1), subsubfolder.getName()), svc.getFileRoot(subsubfolder));

        }

        private void assertPathsEqual(String msg, File expected, File actual)
        {
            String expectedPath = FileUtil.getAbsoluteCaseSensitiveFile(expected).getPath();
            String actualPath = FileUtil.getAbsoluteCaseSensitiveFile(actual).getPath();
            Assert.assertEquals(msg, expectedPath, actualPath);
        }

        private File getTestRoot()
        {
            FileContentService svc = FileContentService.get();
            File siteRoot = svc.getSiteDefaultRoot();
            File testRoot = new File(siteRoot, FILE_ROOT_SUFFIX);
            testRoot.mkdirs();
            Assert.assertTrue("Unable to create test file root", testRoot.exists());

            return testRoot;
        }

        @Test
        //when we move a folder, we expect child files to follow, and expect
        // any file paths stored in the DB to also get updated
        public void testFolderMove() throws Exception
        {
            //pre-clean
            cleanup();

            _expectedPaths = new HashMap<>();

            FileContentService svc = FileContentService.get();
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT1);
            _expectedPaths.put(project1, null);

            Container project2 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT2);
            _expectedPaths.put(project2, null);

            Container subfolder1 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER1);
            _expectedPaths.put(subfolder1, null);

            Container subfolder2 = ContainerManager.createContainer(project1, PROJECT1_SUBFOLDER2);
            _expectedPaths.put(subfolder2, null);

            Container subsubfolder = ContainerManager.createContainer(subfolder1, PROJECT1_SUBSUBFOLDER);
            Container subsubfolderSibling = ContainerManager.createContainer(subfolder1, PROJECT1_SUBSUBFOLDER_SIBLING);
            _expectedPaths.put(subsubfolder, null);

            //create a test file that we will follow
            File fileRoot = svc.getFileRoot(subsubfolder, ContentType.files);
            fileRoot.mkdirs();

            File childFile = new File(fileRoot, TXT_FILE);
            childFile.createNewFile();

            ExpData data = ExperimentService.get().createData(subsubfolder, UPLOADED_FILE);
            data.setDataFileURI(childFile.toPath().toUri());
            data.save(TestContext.get().getUser());

            ExpProtocol protocol = ExperimentService.get().createExpProtocol(subsubfolder, ExpProtocol.ApplicationType.ProtocolApplication, "DummyProtocol");
            protocol = ExperimentService.get().insertSimpleProtocol(protocol, TestContext.get().getUser());

            ExpRun expRun = ExperimentService.get().createExperimentRun(subsubfolder, "DummyRun");
            expRun.setProtocol(protocol);
            expRun.setFilePathRootPath(childFile.getParentFile().toPath());

            ViewBackgroundInfo info = new ViewBackgroundInfo(subsubfolder, TestContext.get().getUser(), null);
            ExpRun run = ExperimentService.get().saveSimpleExperimentRun(
                    expRun,
                    Collections.emptyMap(),
                    Collections.singletonMap(data, "Data"),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    info,
                    _log,
                    false);

            Assert.assertTrue("File not found: " + childFile.getPath(), childFile.exists());
            ContainerManager.move(subsubfolder, subfolder2, TestContext.get().getUser());
            Container movedSubfolder = ContainerManager.getChild(subfolder2, subsubfolder.getName());

            _expectedPaths.put(movedSubfolder, new File(svc.getFileRoot(subfolder2), movedSubfolder.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(movedSubfolder), svc.getDefaultRoot(movedSubfolder, false));
            assertPathsEqual("SubSubfolder has incorrect root", _expectedPaths.get(movedSubfolder), svc.getFileRoot(movedSubfolder));

            File expectedFile = new File(svc.getFileRoot(movedSubfolder, ContentType.files), TXT_FILE);
            Assert.assertTrue("File was not moved, expected: " + expectedFile.getPath(), expectedFile.exists());

            ExpData movedData = ExperimentService.get().getExpData(data.getRowId());
            Assert.assertNotNull(movedData);

            // Reload the run after it's path has hopefully been updated
            expRun = ExperimentService.get().getExpRun(expRun.getRowId());

            assertPathsEqual("Incorrect data file path", expectedFile, FileUtil.stringToPath(movedSubfolder, movedData.getDataFileUrl()).toFile());
            assertPathsEqual("Incorrect run root path", expectedFile.getParentFile(), expRun.getFilePathRoot());

            // Issue 38206 - file paths get mangled with multiple folder moves
            ContainerManager.move(subsubfolderSibling, subfolder2, TestContext.get().getUser());

            // Reload the run after it's path has hopefully NOT been updated
            expRun = ExperimentService.get().getExpRun(expRun.getRowId());
            assertPathsEqual("Incorrect run root path", expectedFile.getParentFile(), expRun.getFilePathRoot());
        }

        @Test
        public void testWorkbooksAndTabs()
        {
            //pre-clean
            cleanup();

            FileContentService svc = FileContentService.get();
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT1);

            Container workbook = ContainerManager.createContainer(project1, null, null, null, WorkbookContainerType.NAME, TestContext.get().getUser());
            File expectedWorkbookRoot = new File(svc.getFileRoot(project1), workbook.getName());
            assertPathsEqual("Workbook has incorrect file root", expectedWorkbookRoot, svc.getFileRoot(workbook));

            Container tab = ContainerManager.createContainer(project1, "tab", null, null, TabContainerType.NAME, TestContext.get().getUser());
            File expectedTabRoot = new File(svc.getFileRoot(project1), tab.getName());
            assertPathsEqual("Folder tab has incorrect file root", expectedTabRoot, svc.getFileRoot(tab));
        }

        /**
         * Test that the Site Settings can be configured from startup properties
         */
        @Test
        public void testStartupPropertiesForSiteRootSettings() throws Exception
        {
            // save the original Site Root File settings so that we can restore them when this test is done
            File originalSiteRootFile = FileContentService.get().getSiteDefaultRoot();

            // create the new site root file to test with as a child of the current site root file so that we know it is in a dir that exist
            String originalSiteRootFilePath = originalSiteRootFile.getAbsolutePath();
            File testSiteRootFile = new File(originalSiteRootFilePath, "testSiteRootFile");
            testSiteRootFile.createNewFile();

            ModuleLoader.getInstance().handleStartupProperties(new SiteRootStartupPropertyHandler(){
                @Override
                public @NotNull Collection<StartupPropertyEntry> getStartupPropertyEntries()
                {
                    return List.of(new StartupPropertyEntry("siteRootFile", testSiteRootFile.getAbsolutePath(), "startup", SCOPE_SITE_ROOT_SETTINGS));
                }

                @Override
                public boolean performChecks()
                {
                    return false;
                }
            });

            // now check that the expected changes occurred to the Site Root File settings on the server
            File newSiteRootFile = FileContentService.get().getSiteDefaultRoot();
            Assert.assertEquals("The expected change in Site Root File was not found", testSiteRootFile.getAbsolutePath(), newSiteRootFile.getAbsolutePath());

            // restore the Site Root File server settings to how they were originally
            FileContentService.get().setSiteDefaultRoot(originalSiteRootFile, null);
            testSiteRootFile.delete();
        }

        @After
        public void cleanup()
        {
            FileContentService svc = FileContentService.get();
            Assert.assertNotNull(svc);

            deleteContainerAndFiles(svc, ContainerManager.getForPath(PROJECT1));
            deleteContainerAndFiles(svc, ContainerManager.getForPath(PROJECT2));

            File testRoot = getTestRoot();
            if (testRoot.exists())
            {
                FileUtil.deleteDir(testRoot);
            }
        }

        private void deleteContainerAndFiles(FileContentService svc, @Nullable Container c)
        {
            if (c != null)
            {
                ContainerManager.deleteAll(c, TestContext.get().getUser());

                File file1 = svc.getFileRoot(c);
                if (file1 != null && file1.exists())
                {
                    FileUtil.deleteDir(file1);
                }
            }
        }
    }
}
