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

package org.labkey.filecontent;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
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
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileListener;
import org.labkey.api.files.FileRoot;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.files.UnsetRootDirectoryException;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
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

import static org.labkey.api.settings.ConfigProperty.modifier.bootstrap;

/**
 * User: klum
 * Date: Dec 9, 2009
 */
public class FileContentServiceImpl implements FileContentService
{
    private static final Logger _log = Logger.getLogger(FileContentServiceImpl.class);
    private static final String UPLOAD_LOG = ".upload.log";
    private static final FileContentServiceImpl INSTANCE = new FileContentServiceImpl();

    private final ContainerListener _containerListener = new FileContentServiceContainerListener();
    private final List<FileListener> _fileListeners = new CopyOnWriteArrayList<>();

    enum Props {
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
    public @Nullable File getFileRoot(@NotNull Container c, @NotNull ContentType type)
    {
        switch (type)
        {
            case files:
                String folderName = getFolderName(type);
                java.nio.file.Path dir = _getFileRoot(c);
                return dir != null ? new File(dir.toFile(), folderName) : null;

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
                java.nio.file.Path fileRootPath = _getFileRoot(c);
                if (null != fileRootPath && !FileUtil.hasCloudScheme(fileRootPath))  // Don't add @files when we're in the cloud
                    fileRootPath = fileRootPath.resolve(getFolderName(type));
                return fileRootPath;

            case pipeline:
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                return root != null ? root.getRootNioPath() : null;
        }
        return null;
    }

    private @Nullable java.nio.file.Path _getFileRoot(Container c)
    {
        java.nio.file.Path root = getFileRootPath(c);
        if (root != null)
        {
            java.nio.file.Path dir;

            //Don't want the Project part of the path.
            if (c.isRoot())
                dir = getSiteDefaultRootPath();
            else
                dir = root;

            return dir;
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
                fileRootPath = CloudStoreService.get().getPathForOtherContainer(firstOverride, c, FileUtil.pathToString(parentRoot), new Path(""));
            }
            else
            {
                fileRootPath = new File(parentRoot.toFile(), getRelativePath(c, firstOverride)).toPath();
            }

            try
            {
                if (null != fileRootPath && !Files.exists(fileRootPath) && createDir)
                    Files.createDirectories(fileRootPath);
            }
            catch (IOException e)
            {
                return null; //  throw new RuntimeException(e);    TODO: does returning null make certain tests, like TargetedMSQCGuideSetTest pass on Windows?
            }
            catch (Exception e)
            {
                // Amazon can throw // TODO probably need to include S3 jar in this module to be more specific
                return null;
            }

            return fileRootPath;
        }
        return null;
    }

    // Return pretty path string for defaultFileRoot and boolean true if defaultFileRoot is cloud
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
    private FileRoot getDefaultFileRoot(Container c)
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

        return FileUtil.stringToPath(c, fileRootPath);
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
            URI uri = FileUtil.createUri(strPath);
            if (FileUtil.hasCloudScheme(uri))
                absolutePath = FileUtil.getAbsolutePath(c, uri);
            else
                absolutePath = FileUtil.getAbsoluteCaseSensitiveFile(new File(uri)).getAbsolutePath();
        }
        _setFileRoot(c, absolutePath);
    }

    private void _setFileRoot(@NotNull Container c, @Nullable String absolutePath)
    {
        if (c.isWorkbookOrTab())
            throw new IllegalArgumentException("File roots cannot be set of workbooks or tabs");
        
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

        Container effective = getEffectiveContainer(container);
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
        Container effective = getEffectiveContainer(c);
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
        
        Container effective = getEffectiveContainer(c);
        if (null == effective)
            return true;

        FileRoot root = FileRootManager.get().getFileRoot(effective);
        return root.isUseDefault() || StringUtils.isEmpty(root.getPath());
    }

    private Container getEffectiveContainer(Container c)
    {
        return c.isWorkbookOrTab() ? c.getParent() : c;
    }

    @Override
    public void setIsUseDefaultRoot(Container c, boolean useDefaultRoot)
    {
        Container effective = getEffectiveContainer(c);
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
        if (root == null || !root.exists())
            throw new IllegalArgumentException("Invalid site root: does not exist");
        
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
    public AttachmentDirectory getMappedAttachmentDirectory(Container c, boolean createDir) throws UnsetRootDirectoryException, MissingRootDirectoryException
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
            throw new IllegalStateException(e.getMessage());
        }
    }

    public java.nio.file.Path getMappedDirectory(Container c, boolean create) throws UnsetRootDirectoryException, IOException
    {
        java.nio.file.Path root = getFileRootPath(c);
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

        java.nio.file.Path dirPath = _getFileRoot(c);
        if (create && null != dirPath && !Files.exists(dirPath))
            Files.createDirectories(dirPath);
        return dirPath;
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
                java.nio.file.Path dir = getMappedDirectory(c, false);
                //Don't try to create dir if root not configured.
                //But if we should have a directory, create it
                if (null != dir && !Files.exists(dir))
                    getMappedDirectory(c, true);
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
                if (isUseDefaultRoot(c))
                    dir = getMappedDirectory(c, false);

                if (null != dir && Files.exists(dir))
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
            // only attempt to move the root if this is a managed file system
            if (isUseDefaultRoot(c))
            {
                java.nio.file.Path path = _getFileRoot(oldParent);
                if (null != path && !FileUtil.hasCloudScheme(path))
                {
                    File src = new File(path.toFile(), c.getName());
                    if (src.exists())
                    {
                        java.nio.file.Path dstPath = _getFileRoot(c);
                        if (null != dstPath && !FileUtil.hasCloudScheme(dstPath))
                            moveFileRoot(src, dstPath.toFile(), user, c);
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
     * @return True if succesfully moved.
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

    static void logFileAction(java.nio.file.Path directory, String fileName, FileAction action, User user) throws IOException
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
            FileRoot root = FileRootManager.get().getFileRoot(c);

            root.setProperties(options.serialize());
            FileRootManager.get().saveFileRoot(null, root);
        }
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
                data = ExperimentService.get().createData(c, new DataType("UploadedFile"));
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
                if(prev.listFiles() != null)
                {
                    for (File file : prev.listFiles())
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
        created = FileUtil.getAbsoluteCaseSensitiveFile(created);
        for (FileListener fileListener : _fileListeners)
        {
            fileListener.fileCreated(created, user, container);
        }
    }

    @Override
    public void fireFileMoveEvent(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
    {
        // Make sure that we've got the best representation of the file that we can
        src = FileUtil.getAbsoluteCaseSensitiveFile(src);
        dest = FileUtil.getAbsoluteCaseSensitiveFile(dest);
        for (FileListener fileListener : _fileListeners)
        {
            fileListener.fileMoved(src, dest, user, container);
        }
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
        if (currentUser == null || !currentUser.isSiteAdmin())
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

    public static void populateSiteRootFileWithStartupProps()
    {
        final boolean isBootstrap = ModuleLoader.getInstance().isNewInstall();

        // populate the site root file settings with values read from startup properties as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like: FileSiteRootSettings.fileRoot;bootstrap=/labkey/labkey/files
        // if more than one FileSiteRootSettings.siteRootFile specified in the startup properties file then the last one overrides the previous ones
        Collection<ConfigProperty> startupProps = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_SITE_ROOT_SETTINGS);
        startupProps.stream()
                .filter( prop -> prop.getName().equals("siteRootFile"))
                .filter( prop -> prop.getModifier() != bootstrap || isBootstrap )
                .forEach(prop -> {
                    File fileRoot = new File(prop.getValue());
                    FileContentService.get().setSiteDefaultRoot(fileRoot, null);
                });
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
            if (absoluteFilePath.startsWith(rootPath))
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
    public void ensureFileData(QueryUpdateService qus, @NotNull User user, @NotNull Container container)
    {
        if (qus == null)
            return;

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
                                _log.error("Unable to list files for fileset: " + rootName);
                            }
                            break;
                        }
                    }
                }
            }

            if (file == null)
                return;

            try
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
                _log.error("Error listing content of directory: " + file.getAbsolutePath());
            }
        }
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
            if (null != file)
            {
                String legacyUrl = file.toURI().toString();
                if (existingUrls.contains(legacyUrl))      // Legacy URI format (file:/users/...)
                    return true;

                if (existingUrls.contains(file.getPath()))
                    return true;
            }
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
        private static final String PROJECT2 = "FileRootTestProject2";

        private static final String FILE_ROOT_SUFFIX = "_FileRootTest";
        private static final String TXT_FILE = "FileContentTestFile.txt";

        private Map<Container, File> _expectedPaths;

        @Test
        public void fileRootsTest() throws Exception
        {
            //pre-clean
            cleanup();

            _expectedPaths = new HashMap<>();

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
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
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
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

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
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

            //create a test file that we will follow
            File fileRoot = svc.getFileRoot(subsubfolder, ContentType.files);
            fileRoot.mkdirs();

            File childFile = new File(fileRoot, TXT_FILE);
            childFile.createNewFile();

            ExpData data = ExperimentService.get().createData(subsubfolder, new DataType("FileContentTest"));
            data.setDataFileURI(childFile.toPath().toUri());
            data.save(TestContext.get().getUser());
            int rowId = data.getRowId();

            Assert.assertTrue("File not found: " + childFile.getPath(), childFile.exists());
            ContainerManager.move(subsubfolder, subfolder2, TestContext.get().getUser());
            Container movedSubfolder = ContainerManager.getChild(subfolder2, subsubfolder.getName());

            _expectedPaths.put(movedSubfolder, new File(svc.getFileRoot(subfolder2), movedSubfolder.getName()));
            assertPathsEqual("Incorrect values returned by getDefaultRoot", _expectedPaths.get(movedSubfolder), svc.getDefaultRoot(movedSubfolder, false));
            assertPathsEqual("SubSubfolder has incorrect root", _expectedPaths.get(movedSubfolder), svc.getFileRoot(movedSubfolder));

            File expectedFile = new File(svc.getFileRoot(movedSubfolder, ContentType.files), TXT_FILE);
            Assert.assertTrue("File was not moved, expected: " + expectedFile.getPath(), expectedFile.exists());

            ExpData movedData = ExperimentService.get().getExpData(rowId);
            Assert.assertNotNull(movedData);

            assertPathsEqual("Incorrect file path", expectedFile, FileUtil.stringToPath(movedSubfolder, movedData.getDataFileUrl()).toFile());
        }

        @Test
        public void testWorkbooksAndTabs() throws Exception
        {
            //pre-clean
            cleanup();

            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT1);

            Container workbook = ContainerManager.createContainer(project1, null, null, null, Container.TYPE.workbook, TestContext.get().getUser());
            File expectedWorkbookRoot = new File(svc.getFileRoot(project1), workbook.getName());
            assertPathsEqual("Workbook has incorrect file root", expectedWorkbookRoot, svc.getFileRoot(workbook));

            Container tab = ContainerManager.createContainer(project1, "tab", null, null, Container.TYPE.tab, TestContext.get().getUser());
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

            // ensure that the site wide ModuleLoader has test startup property values in the _configPropertyMap
            prepareTestStartupProperties(testSiteRootFile);

            // call the method that makes use of the test startup properties to change the Site Root File settings on the server
            populateSiteRootFileWithStartupProps();

            // now check that the expected changes occured to the Site Root File settings on the server
            File newSiteRootFile = FileContentService.get().getSiteDefaultRoot();
            Assert.assertEquals("The expected change in Site Root File was not found", testSiteRootFile.getAbsolutePath(), newSiteRootFile.getAbsolutePath());

            // restore the Site Root File server settings to how they were originally
            FileContentService.get().setSiteDefaultRoot(originalSiteRootFile, null);
            testSiteRootFile.delete();
        }

        private void prepareTestStartupProperties(File testSiteRootFile)
        {
            // prepare a multimap of config properties to test with that has properties assigned for several scopes and populate with sample properties from several scopes
            MultiValuedMap<String, ConfigProperty> testConfigPropertyMap = new HashSetValuedHashMap<>();

            // prepare test Site Root Settings properties
            ConfigProperty testSiteRootSettingsProp1 =  new ConfigProperty("siteRootFile", testSiteRootFile.getAbsolutePath(), "startup", ConfigProperty.SCOPE_SITE_ROOT_SETTINGS);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_SITE_ROOT_SETTINGS, testSiteRootSettingsProp1);

            // set these test startup test properties to be used by the entire server
            ModuleLoader.getInstance().setConfigProperties(testConfigPropertyMap);
        }

        @After
        public void cleanup()
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            Assert.assertNotNull(svc);

            Container project1 = ContainerManager.getForPath(PROJECT1);
            if (project1 != null)
            {
                ContainerManager.deleteAll(project1, TestContext.get().getUser());

                File file1 = svc.getFileRoot(project1);
                if (file1 != null && file1.exists())
                {
                    FileUtil.deleteDir(file1);
                }
            }

            Container project2 = ContainerManager.getForPath(PROJECT2);
            if (project2 != null)
            {
                ContainerManager.deleteAll(project2, TestContext.get().getUser());

                File file2 = svc.getFileRoot(project2);
                if (file2 != null && file2.exists())
                {
                    FileUtil.deleteDir(file2);
                }
            }

            File testRoot = getTestRoot();
            if (testRoot.exists())
            {
                FileUtil.deleteDir(testRoot);
            }
        }
    }
}
