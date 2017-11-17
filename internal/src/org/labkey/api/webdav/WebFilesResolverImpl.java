/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WebFilesResolverImpl extends AbstractWebdavResolver
{
    final private static Path PATH = Path.parse("/_webfiles/");
    static WebFilesResolverImpl _instance = new WebFilesResolverImpl(PATH);

    final Path _rootPath;

    private WebFilesResolverImpl(Path path)
    {
        _rootPath = path;
        // skip folder caching since changes under @files aren't attached to container listener
    }

    public static WebdavResolver get()
    {
        return _instance;
    }

    public boolean requiresLogin()
    {
        return false;
    }

    public Path getRootPath()
    {
        return _rootPath;
    }

    WebFilesFolderResource _root = null;

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

        public boolean canRename(User user, boolean forRename)
        {
            return hasAccess(user) && !user.isGuest() && canCreate(user, forRename) && canDelete(user, forRename, null);
        }

        @Override
        public synchronized List<String> getWebFoldersNames()
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
                File fileRootFolder = getFileRootFile(container);
                if (fileRootFolder != null)
                {
                    File[] fileRootContent = fileRootFolder.listFiles();
                    if (fileRootContent != null)
                    {
                        for (File file : fileRootContent)
                        {
                            if (file.isDirectory())
                            {
                                String rawFileName = file.getName();
                                String noConflictName = getFolderNameNoConflict(children, rawFileName);
                                children.add(noConflictName);
                                folderNamesMap.put(noConflictName, rawFileName);
                            }
                            else
                                children.add(file.getName());
                        }
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

        public WebdavResource find(String child)
        {
            String name = null;
            for (String folder : getWebFoldersNames())
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

            if (name != null)
            {
                Path path = getPath().append(name);
                WebdavResource resource = null;

                // if child container
                if (c != null)
                {
                    resource = new WebFilesFolderResource(_resolver, c);
                }
                else // if directory under @files
                {
                    File fileRootFile = getFileRootFile(parentContainer);
                    if (fileRootFile != null)
                    {
                        String rawFolderName = folderNamesMap.get(name);
                        File targetFile = null;
                        File[] fileRootContent = fileRootFile.listFiles();
                        if (fileRootContent != null)
                        {
                            for (File file : fileRootContent)
                            {
                                if (file.isDirectory() && file.getName().equals(rawFolderName))
                                    targetFile = file;
                                else if (!file.isDirectory() && file.getName().equals(name))
                                    targetFile = file;
                            }
                        }
                        if (targetFile != null)
                            resource = new FileSystemResource(this, name, targetFile, parentContainer.getPolicy());

                    }

                }

                if (resource != null)
                    return resource;
            }
            else // uploading, creating subfolder directly under child (but maps to @files) is supported
            {
                File fileRootFile = getFileRootFile(parentContainer);
                if (fileRootFile != null)
                {
                    FileSystemResource fileRootResource = new FileSystemResource(this, "@files", fileRootFile, parentContainer.getPolicy());
                    if (child.equals("@files"))
                        return fileRootResource;
                    return new FileSystemResource(fileRootResource, child);
                }
            }

            return new UnboundResource(this.getPath().append(child));
        }

        private File getFileRootFile(Container container)
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            if (svc != null)
            {
                try
                {
                    AttachmentDirectory dir = svc.getMappedAttachmentDirectory(container, false);
                    if (dir != null)
                        return dir.getFileSystemDirectory();
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
