/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.webdav;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileListener;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WebFilesResolverImpl extends AbstractWebdavResolver implements FileListener
{
    final private static Path PATH = Path.parse("/_webfiles/");
    static WebFilesResolverImpl _instance = new WebFilesResolverImpl(PATH);

    final Path _rootPath;

    private WebFilesResolverImpl(Path path)
    {
        _rootPath = path;
        ContainerManager.addContainerListener(new WebfilesContainerListener());
    }

    public static WebFilesResolverImpl get()
    {
        return _instance;
    }

    @Override
    public boolean requiresLogin()
    {
        return false;
    }

    @Override
    public Path getRootPath()
    {
        return _rootPath;
    }

    WebFilesFolderResource _root = null;

    @Override
    protected synchronized WebFilesFolderResource getRoot()
    {
        if (null == _root)
        {
            _root = new WebFilesFolderResource(this, ContainerManager.getRoot())
            {
                @Override
                public boolean canList(User user, boolean forRead)
                {
                    return true;
                }
            };
        }
        return _root;
    }

    @Override
    public String toString()
    {
        return "webfiles";
    }

    private class WebfilesContainerListener extends AbstractWebdavListener
    {
        @Override
        protected void clearFolderCache()
        {
            _webfilesCache.clear();
        }

        @Override
        protected void invalidate(Path containerPath, boolean recursive)
        {
            invalidateCache(containerPath, recursive);
        }
    }

    public void invalidateCache(Path containerPath, boolean recursive)
    {
        if (!AppProps.getInstance().isWebfilesRootEnabled())
            return;

        final Path path = getRootPath().append(containerPath);
        _webfilesCache.remove(path);
        if (recursive)
            _webfilesCache.removeUsingFilter(test -> test.startsWith(path));
        if (containerPath.size() == 0)
        {
            synchronized (WebFilesResolverImpl.this)
            {
                _root = null;
            }
        }
    }

    @Override
    public String getSourceName()
    {
        return "WebfilesFileListener";
    }

    @Override
    public void fileCreated(@NotNull File created, @Nullable User user, @Nullable Container container)
    {
        fileCreated(created.toPath(), user, container);
    }

    @Override
    public void fileCreated(@NotNull java.nio.file.Path created, @Nullable User user, @Nullable Container container)
    {
        if (container != null)
        {
            invalidateCache(container.getParsedPath(), true);
        }
    }

    @Override
    public int fileMoved(@NotNull File src, @NotNull File dest, @Nullable User user, @Nullable Container container)
    {
        return fileMoved(src.toPath(), dest.toPath(), user, container);
    }

    @Override
    public int fileMoved(@NotNull java.nio.file.Path src, @NotNull java.nio.file.Path dest, @Nullable User user, @Nullable Container container)
    {
        if (AppProps.getInstance().isWebfilesRootEnabled() && container != null)
        {
            invalidateCache(container.getParsedPath(), true);
        }
        return 0;
    }

    @Override
    public Collection<File> listFiles(@Nullable Container container)
    {
        return null;
    }

    @Override
    public SQLFragment listFilesQuery()
    {
        return null;
    }

    // Cache with short-lived entries to make webfiles perform reasonably.  WebFilesResolverImpl is a singleton, so we
    // end up with just one of these.
    private Cache<Path, WebdavResource> _webfilesCache = CacheManager.getCache(CacheManager.UNLIMITED, 5 * CacheManager.MINUTE, "WebFiles");

    @Override
    public boolean isEnabled()
    {
        return AppProps.getInstance().isWebfilesRootEnabled();
    }

    public class WebFilesFolderResource extends AbstractWebFolderResource
    {
        private Map<String, String> folderNamesMap = new HashMap<>();

        WebFilesFolderResource(WebdavResolver resolver, Container c)
        {
            super(resolver, c);
        }

        @Override
        public boolean canList(User user, boolean forRead)
        {
            return canRead(user, forRead);
        }


        @Override
        public boolean canRead(User user, boolean forRead)
        {
            if ("/".equals(getPath()))
                return true;
            return getPermissions(user).contains(ReadPermission.class);
        }

        @Override
        public boolean canWrite(User user, boolean forWrite)
        {
            return !user.isGuest() && getPermissions(user).contains(UpdatePermission.class);
        }

        @Override
        public boolean canCreate(User user, boolean forCreate)
        {
            return hasAccess(user) && !user.isGuest() && getPermissions(user).contains(InsertPermission.class);
        }

        @Override
        public boolean canCreateCollection(User user, boolean forCreate)
        {
            return canCreate(user, forCreate);
        }

        @Override
        public boolean canDelete(User user, boolean forDelete)
        {
            return canDelete(user,forDelete,null);
        }

        @Override
        public boolean canDelete(User user, boolean forDelete, /* OUT */ @Nullable List<String> message)
        {
            if (user.isGuest() || !hasAccess(user))
                return false;
            Set<Class<? extends Permission>> perms = getPermissions(user);
            return perms.contains(UpdatePermission.class) || perms.contains(DeletePermission.class);
        }

        @Override
        public boolean canRename(User user, boolean forRename)
        {
            return hasAccess(user) && !user.isGuest() && canCreate(user, forRename) && canDelete(user, forRename, null);
        }

        @Override
        public synchronized List<String> getWebFoldersNames(boolean isListing)
        {
            Container container = getContainer();
            if (null == _children)
            {
                List<Container> childContainers = ContainerManager.getChildren(container);
                ArrayList<String> children = new ArrayList<>(childContainers.size() + 2);
                // get child containers
                for (Container childContainer : childContainers)
                {
                    String containerName = childContainer.getName();
                    children.add(containerName);
                    folderNamesMap.put(containerName, containerName);
                }

                // get directories under @files
                java.nio.file.Path fileRootFolder = getFileRootFile(container);
                if (fileRootFolder != null)
                {
                    try (Stream<java.nio.file.Path> list = Files.list(fileRootFolder))
                    {
                        list.forEach(path ->
                        {
                            String rawFileName = FileUtil.getFileName(path);
                            if (Files.isDirectory(path))
                            {
                                String noConflictName = getFolderNameNoConflict(children, rawFileName);
                                children.add(noConflictName);
                                folderNamesMap.put(noConflictName, rawFileName);
                            }
                            else
                                children.add(rawFileName);
                        });
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

                // providers might not be registered if !isStartupComplete();
                if (!ModuleLoader.getInstance().isStartupComplete())
                    return children;
                _children = children;
            }
            return _children;
        }

        private String getFolderNameNoConflict(List<String> existingNames, String fileFolderName)
        {
            // if no conflict, use original folder name,
            // otherwise, child container wins to keep their name; folders under @files will use " (files)" as suffix, then "2", "3" until an unused name is found
            if (!containsIgnoreCase(existingNames, fileFolderName))
                return fileFolderName;
            String noConflictName = fileFolderName + " (files)";
            int i = 1;
            while (containsIgnoreCase(existingNames, noConflictName))
            {
                noConflictName = fileFolderName + " (files " + ++i + ")";
            }
            return noConflictName;
        }

        private boolean containsIgnoreCase(List<String> collection, String target)
        {
            return collection.stream().anyMatch(s -> s.equalsIgnoreCase(target));
        }

        @Override
        public WebdavResource find(String child)
        {
            String name = null;
            for (String folder : getWebFoldersNames(false))
            {
                if (folder.equalsIgnoreCase(child))
                {
                    name = folder;
                    break;
                }
            }

            Container parentContainer = getContainer();
            Container c = parentContainer.getChild(child);
            if (name == null && c != null)
                name = c.getName();

            WebdavResource resource = null;
            Path path = null;
            if (name != null)
            {
                path = getPath().append(name);
                // check in webfolder cache
                resource = _webfilesCache.get(getPath().append(name));
                if (null != resource)
                    return resource;

                // if child container
                if (c != null)
                {
                    resource = new WebFilesFolderResource(_resolver, c);
                }
                else // if directory under @files
                {
                    java.nio.file.Path fileRootFile = getFileRootFile(parentContainer);
                    if (fileRootFile != null)
                    {
                        String rawFolderName = folderNamesMap.get(name);
                        try
                        {
                            FileContentService fileContentService = FileContentService.get();
                            boolean isCloudRoot = null != fileContentService && fileContentService.isCloudRoot(parentContainer);
                            try (Stream<java.nio.file.Path> list = Files.list(fileRootFile))
                            {
                                for (java.nio.file.Path p : list.collect(Collectors.toList()))
                                {
                                    String pathFileName = FileUtil.getFileName(p);
                                    if ((Files.isDirectory(p) && StringUtils.equals(rawFolderName, pathFileName)) ||
                                        (!Files.isDirectory(p) && StringUtils.equals(name, pathFileName)))
                                    {
                                        if (!FileUtil.hasCloudScheme(p))
                                        {
                                            return new FileSystemResource(this, name, p.toFile(), parentContainer.getPolicy());
                                        }
                                        else if (isCloudRoot)
                                        {
                                            CloudStoreService cloudStoreService = CloudStoreService.get();
                                            if (null != cloudStoreService)
                                            {
                                                resource = cloudStoreService.getWebFilesResource(this, parentContainer, (null != rawFolderName ? rawFolderName : name), name);
                                            }
                                            break;
                                        }
                                        // TODO: handle hasCloudScheme(path) && !isCloudRoot
                                    }
                                }
                            }
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
            else // uploading, creating subfolder directly under child (but maps to @files) is supported
            {
                FileContentService fileContentService = FileContentService.get();
                boolean isCloudRoot = null != fileContentService && fileContentService.isCloudRoot(parentContainer);
                java.nio.file.Path fileRootFile = getFileRootFile(parentContainer);
                if (fileRootFile != null)
                {
                    if (!FileUtil.hasCloudScheme(fileRootFile))
                    {
                        FileSystemResource fileRootResource = new FileSystemResource(this, "@files", fileRootFile.toFile(), parentContainer.getPolicy());
                        if (child.equals("@files"))
                            return fileRootResource;
                        return new FileSystemResource(fileRootResource, child);
                    }
                    else if (isCloudRoot)
                    {
                        CloudStoreService cloudStoreService = CloudStoreService.get();
                        if (null != cloudStoreService)
                        {
                            resource = cloudStoreService.getWebFilesResource(this, parentContainer, child, child);
                        }
                    }
                    // TODO: handle hasCloudScheme(path) && !isCloudRoot
                }
            }

            if (resource != null)
            {
                _webfilesCache.put(path, resource);
                return resource;
            }

            return new UnboundResource(this.getPath().append(child));
        }

        private java.nio.file.Path getFileRootFile(Container container)
        {
            FileContentService svc = FileContentService.get();
            if (svc != null)
            {
                try
                {
                    AttachmentDirectory dir = svc.getMappedAttachmentDirectory(container, false);
                    if (dir != null)
                        return dir.getFileSystemDirectoryPath();
                }
                catch (MissingRootDirectoryException e)
                {
                    // Don't complain here, just hide the @files subfolders
                }
            }
            return null;
        }

        @Override
        public boolean shouldIndex()
        {
            return false; // resources in _webdav are already indexed, prevent _webfiles from double indexing
        }
    }

}
