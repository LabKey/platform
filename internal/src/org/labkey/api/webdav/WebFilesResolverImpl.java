package org.labkey.api.webdav;

import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Filter;
import org.labkey.api.util.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebFilesResolverImpl extends AbstractWebdavResolver
{
    final private static Path PATH = Path.parse("/_webfiles/");
    static WebFilesResolverImpl _instance = new WebFilesResolverImpl(PATH);

    final Path _rootPath;

    private WebFilesResolverImpl(Path path)
    {
        _rootPath = path;
        ContainerManager.addContainerListener(new WebfilesListener());
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

    private class WebfilesListener extends AbstractWebdavListener
    {
        @Override
        protected void clearFolderCache()
        {
            _folderCache.clear();
        }

        @Override
        protected void invalidate(Path containerPath, boolean recursive)
        {
            final Path path = getRootPath().append(containerPath);
            _folderCache.remove(path);
            if (recursive)
                _folderCache.removeUsingFilter(new Filter<Path>() {
                    @Override
                    public boolean accept(Path test)
                    {
                        return test.startsWith(path);
                    }
                });
            if (containerPath.size() == 0)
            {
                synchronized (WebFilesResolverImpl.this)
                {
                    _root = null;
                }
            }
        }
    }

    // Cache with short-lived entries to make webfiles perform reasonably.  WebFilesResolverImpl is a singleton, so we
    // end up with just one of these.
    private Cache<Path, WebdavResource> _folderCache = CacheManager.getCache(CacheManager.UNLIMITED, 5 * CacheManager.MINUTE, "WebFiles folders");

    public class WebFilesFolderResource extends AbstractWebFolderResource
    {
        private Map<String, String> folderNamesMap = new HashMap<>();

        WebFilesFolderResource(WebdavResolver resolver, Container c)
        {
            super(resolver, c);
        }

        @Override
        public synchronized List<String> getWebFoldersNames()
        {
            Container container = getContainer();
            if (null == _children)
            {
                List<Container> list = ContainerManager.getChildren(container);
                ArrayList<String> children = new ArrayList<>(list.size() + 2);
                // get child containers
                for (Container aList : list)
                {
                    String containerName = aList.getName();
                    children.add(containerName);
                    folderNamesMap.put(containerName, containerName);
                }

                // get directories under @files
                FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                if (svc != null)
                {
                    AttachmentDirectory dir = null;
                    try
                    {
                        dir = svc.getMappedAttachmentDirectory(container, false);
                        if (dir != null)
                        {
                            File fileRootFolder = dir.getFileSystemDirectory();
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
                                }
                            }
                        }
                    }
                    catch (MissingRootDirectoryException e)
                    {
                        // Don't complain here, just hide the @files subfolders
                    }
                }

                // providers might not be registred if !isStartupComplete();
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
                // check in webfolder cache
                WebdavResource resource = _folderCache.get(path);
                if (null != resource)
                    return resource;

                // if child container
                if (c != null)
                {
                    resource = new WebFilesFolderResource(_resolver, c);
                }
                else // if directory under @files
                {
                    String rawFolderName = folderNamesMap.get(name);
                    File targetFile = null;
                    FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
                    if (svc != null)
                    {
                        AttachmentDirectory dir = null;
                        try
                        {
                            dir = svc.getMappedAttachmentDirectory(parentContainer, false);
                            if (dir != null)
                            {
                                File fileRootFolder = dir.getFileSystemDirectory();
                                File[] fileRootContent = fileRootFolder.listFiles();
                                if (fileRootContent != null)
                                {
                                    for (File file : fileRootContent)
                                    {
                                        if (file.getName().equals(rawFolderName))
                                            targetFile = file;
                                    }
                                }
                            }
                            if (targetFile != null)
                                resource = new FileSystemResource(this, name, targetFile, parentContainer.getPolicy(), true);

                        }
                        catch (MissingRootDirectoryException e)
                        {
                            // Don't complain here, just hide the @files subfolders
                        }
                    }

                }

                if (resource != null)
                {
                    _folderCache.put(path, resource);
                    return resource;
                }
            }

            return new UnboundResource(this.getPath().append(child));
        }

        @Override
        public boolean shouldIndex()
        {
            return false; // resources in _webdav are already indexed, prevent _webfiles from double indexing
        }
    }

}
