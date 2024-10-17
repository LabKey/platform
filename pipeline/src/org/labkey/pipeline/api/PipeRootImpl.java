/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.pipeline.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.DirectoryNotDeletedException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PipeRootImpl implements PipeRoot
{
    private static final String SYSTEM_DIRECTORY_NAME = ".labkey";
    private static final String SYSTEM_DIRECTORY_LEGACY = "system";


    private String _containerId;
    private final List<URI> _uris = new ArrayList<>();
    private List<File> _rootPaths = new ArrayList<>();
    private List<Path> _rootNioPaths = new ArrayList<>();
    private final String _entityId;
    private final boolean _searchable;
    private String _cloudStoreName;         // Only used for cloud

    private enum ROOT_BASE
    {
        files
        {
            @Override
            String getDavName()
            {
                return FileContentService.FILES_LINK;
            }
        },
        pipeline
        {
            @Override
            String getDavName()
            {
                return FileContentService.PIPELINE_LINK;
            }
        },
        cloud
        {
            @Override
            String getDavName()
            {
                return FileContentService.CLOUD_LINK;
            }
        };

        abstract String getDavName();
    }

    /** true if this root is based on the site or project default file root */
    private ROOT_BASE _defaultRoot;

    // No-args constructor to support de-serialization in Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    public PipeRootImpl()
    {
        _entityId = null;
        _searchable = false;
        _defaultRoot = ROOT_BASE.pipeline;
    }

    public PipeRootImpl(PipelineRoot root, boolean isDefaultRoot)
    {
        this(root);
        _defaultRoot = isDefaultRoot ? ROOT_BASE.files : ROOT_BASE.pipeline;
    }

    public PipeRootImpl(PipelineRoot root)
    {
        String rootPath = root.getPath().startsWith(FileContentService.CLOUD_ROOT_PREFIX) ? root.getPath().replace(FileContentService.CLOUD_ROOT_PREFIX, "") : root.getPath();
        _defaultRoot = root.getPath().startsWith(FileContentService.CLOUD_ROOT_PREFIX) ? ROOT_BASE.cloud : ROOT_BASE.pipeline;

        _containerId = root.getContainerId();

        if (ROOT_BASE.cloud.equals(_defaultRoot))
        {
            Container container = getContainer();
            _cloudStoreName = rootPath.substring(1, rootPath.indexOf("/", 1));
            rootPath = rootPath.substring(rootPath.indexOf("/", 1) + 1);
            if (null == container)
                throw new IllegalStateException("Container missing");
        }

        try
        {
            _uris.add(new URI(rootPath));
            if (root.getSupplementalPath() != null)
            {
                _uris.add(new URI(root.getSupplementalPath()));
            }
        }
        catch (URISyntaxException e)
        {
            throw new UnexpectedException(e);
        }
        _entityId = root.getEntityId();
        _searchable = root.isSearchable();
    }

    @Override
    @NotNull
    public File ensureSystemDirectory()
    {
        Path path = ensureSystemDirectoryPath();
        if (FileUtil.hasCloudScheme(path))
            throw new RuntimeException("System Dir is not on file system.");
        return path.toFile();
    }

    @Override
    @NotNull
    public Path ensureSystemDirectoryPath()
    {
        Path root = getRootNioPath();
        Path systemDir = root.resolve(SYSTEM_DIRECTORY_NAME);
        if (!Files.exists(systemDir))
        {
            try
            {
                FileUtil.createDirectories(systemDir);

                Path systemDirLegacy = root.resolve(SYSTEM_DIRECTORY_LEGACY);
                if (Files.exists(systemDirLegacy))
                {
                    // Legacy means it must be on file system
                    File legacyDir = systemDirLegacy.toFile();
                    for (File f : legacyDir.listFiles())
                        f.renameTo(systemDir.toFile());
                }

                for (PipelineProvider provider : PipelineService.get().getPipelineProviders())
                    provider.initSystemDirectory(root, systemDir);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        return systemDir;
    }

    @Override
    public Container getContainer()
    {
        return ContainerManager.getForId(_containerId);
    }

    @Override
    @NotNull
    public URI getUri()
    {
        if (_uris.size() == 0)
            throw new IllegalStateException("No URI.");
        return _uris.get(0);
    }

    @Override
    @NotNull
    public File getRootPath()
    {
        if (getRootPaths().size() == 0)
            throw new RuntimeException("No root path set.");
        return getRootPaths().get(0);
    }

    @Override
    @NotNull
    public Path getRootNioPath()
    {
        assert _uris.size() > 0;
        if (ROOT_BASE.cloud.equals(_defaultRoot))
        {
            return CloudStoreService.get().getPath(getContainer(), _cloudStoreName, new org.labkey.api.util.Path(_uris.get(0).getPath()));
        }
        else
            return getRootPath().toPath();
    }

    @Override
    public @NotNull FileLike getRootFileLike()
    {
        return new FileSystemLike.Builder(getRootPath()).readwrite().root();
    }

    @Override
    @NotNull
    public File getLogDirectory()
    {
        // If pipeline root is in File system, return that; otherwise return temp directory
        if (isCloudRoot())
            return FileUtil.getTempDirectory();
        else
            return getRootPath();
    }

    @Override
    @NotNull
    public FileLike getLogDirectoryFileLike(boolean forWrite)
    {
        var b = new FileSystemLike.Builder(getLogDirectory()).readonly();
        if (forWrite)
            b.readwrite();
        return b.root();
    }

    public synchronized List<File> getRootPaths()
    {
        if (_rootPaths.size() == 0 && !isCloudRoot())
        {
            for (URI uri : _uris)
            {
                File file = new File(uri);
                _rootPaths.add(file);
                NetworkDrive.ensureDrive(file.getPath());
            }
        }
        return _rootPaths;
    }

    // This list will return local path for cloud as well, caller can exclude
    public synchronized List<FileLike> getRootFileLikePaths(boolean forWrite)
    {
        if (forWrite)
            return getRootPaths().stream().map(f -> new FileSystemLike.Builder(f).readwrite().root()).toList();
        else
            return getRootPaths().stream().map(f -> new FileSystemLike.Builder(f).readonly().root()).toList();
    }


    public synchronized List<Path> getRootNioPaths()
    {
        if (_rootNioPaths.isEmpty() && !isCloudRoot())
        {
            for (URI uri : _uris)
            {
                Path file = FileUtil.getPath(getContainer(), uri);
                _rootNioPaths.add(file);
                assert file != null;
                NetworkDrive.ensureDrive(file.toString());
            }
        }
        return _rootNioPaths;
    }

    public List<URI> getRootURIs()
    {
        return _uris;
    }

    private File findRootPath(File file)
    {
        if (isCloudRoot())
            return null;

        // remove "." and ".."
        file = FileUtil.resolveFile(file);
        
        for (File root : getRootPaths())
        {
            // First, see if the file is under the root as it is:
            if (URIUtil.isDescendant(root.toURI(), file.toURI()))
            {
                return root;
            }
        }

        for (File root : getRootPaths())
        {
            // Next, see if canonicalizing both the root and the file causes them to be under each other.
            // Canonicalizing the path both standardizes the filename case, but also resolves symbolic links.

            try
            {
                File canFile = file.getCanonicalFile();
                File canRoot = root.getCanonicalFile();
                if (URIUtil.isDescendant(canRoot.toURI(), canFile.toURI()))
                {
                    return root;
                }
            }
            catch (IOException e) {}
        }
        return null;
    }

    private Path findRootPath(Path file)
    {
        Path root = getRootNioPath();

        // remove "." and ".."
        file = file.normalize();

        // First, see if the file is under the root as it is:
        if (URIUtil.isDescendant(root.toUri(), file.toUri()))
        {
            return root;
        }

        for (int i = 1; i < getRootURIs().size(); i++)
        {
            URI rootURI = getRootURIs().get(i);
            // Try the supplemental roots as well
            if (URIUtil.isDescendant(rootURI, file.toUri()))
            {
                return Paths.get(rootURI);
            }
        }

        return null;
    }


    @Override
    @Nullable
    public File resolvePath(String pathStr)
    {
        if (null == pathStr)
            throw new NotFoundException("Must specify a file path");

        return resolvePath(org.labkey.api.util.Path.parse(pathStr));
    }


    @Nullable
    public File resolvePath(org.labkey.api.util.Path path)
    {
        var pair = _resolveRoot(path);
        if (null == pair)
            return null;
        return pair.second;
    }


    @Override
    public @Nullable FileLike resolvePathToFileLike(String relativePath)
    {
        var parsedPath = org.labkey.api.util.Path.parse(relativePath);

        var pair = _resolveRoot(parsedPath);
        if (null == pair)
            return null;
        var root = new FileSystemLike.Builder(pair.first).readwrite().root();
        return root.resolveFile(parsedPath);
    }


    /* return file root and relative path */
    @Nullable
    public Pair<File,File> _resolveRoot(org.labkey.api.util.Path path)
    {
        // Check if the file already exists on disk
        for (File root : getRootPaths())
        {
            File file = FileUtil.appendPath(root, path);
            // Check that it's under the root to protect against ../../ type paths
            if (file.exists() && isUnderRoot(file))
            {
                return new Pair<>(root,file);
            }
        }

        // Return the path to the default location
        File root = getRootPath();
        File file = FileUtil.getAbsoluteCaseSensitiveFile(FileUtil.appendPath(root, path));
        // Check that it's under the root to protect against ../../ type paths
        if (!isUnderRoot(file))
        {
            return null;
        }
        return new Pair<>(root,file);
    }

//    /** resolve a path treating it as an absolute file system path */
//    @Nullable
//    private Pair<FileLike,FileLike> _resolveRootRelative(org.labkey.api.util.Path path)
//    {
//        FileLike defaultRoot = getRootFileLike();
//        path = path.absolute().normalize();
//        if (path.isEmpty() || "..".equals(path.get(0)))
//            return null;
//
//        // Check if the file already exists on disk
//        for (FileLike root : getRootFileLikePaths(false))
//        {
//            if (root.is)
//            FileLike file = root.resolveFile(path);
//            if (file.exists())
//                return new Pair<>(root,file);
//        }
//
//        FileLike root = getRootFileLike();
//        FileLike file = root.resolveFile(path);
//        return new Pair<>(root,file);
//    }


    @Override
    @Nullable
    public Path resolveToNioPath(String pathStr)
    {
        if (pathStr == null)
            throw new NotFoundException("Must specify a file path");

        // Remove leading "./" sometimes added by the client side FileBrowser
        if (pathStr.startsWith("./"))
            pathStr = pathStr.substring(2);

        var path = org.labkey.api.util.Path.parse(pathStr);

        try
        {
            if (ROOT_BASE.cloud.equals(_defaultRoot))
            {
                // Return the path to the default location
                var combinedPath = StringUtils.isNotBlank(_uris.get(0).getPath()) ?
                        org.labkey.api.util.Path.parse(_uris.get(0).getPath()).append(path) :
                        path;
                return CloudStoreService.get().getPath(getContainer(), _cloudStoreName, combinedPath);
                // TODO: Do we need? Check that it's under the root to protect against ../../ type paths
            }
            else
            {
                File file = resolvePath(path);
                return null != file ? file.toPath() : null;
            }
        }
        catch (InvalidPathException e)
        {
            throw new NotFoundException("Must specify a valid file path", e);
        }
    }

    @Override
    @Nullable
    public Path resolveToNioPathFromUrl(String url)
    {
        if (ROOT_BASE.cloud.equals(_defaultRoot))
        {
            return resolveToNioPath(FileUtil.decodeSpaces(CloudStoreService.get().getRelativePath(getContainer(), _cloudStoreName, url)));
        }
        else
        {
            try
            {
                URI uri = new URI(url);
                if (!FileUtil.hasCloudScheme(uri))
                    return new File(uri).toPath();
            }
            catch (URISyntaxException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
        return null;
    }

    /**
     * Get a local directory that can be used for importing (Read/Write)
     *
     * Cloud: Uses temp directory
     * Default: Uses file root
     * @return
     */
    @Override
    @NotNull
    public File getImportDirectory()
    {
        // If pipeline root is in File system, return that; otherwise return temp directory
        File root = isCloudRoot() ?
            FileUtil.getTempDirectory() :
            getRootPath();
        return FileUtil.appendName(root, PipelineService.UNZIP_DIR);
    }

    @Override
    public Path deleteImportDirectory(@Nullable Logger logger) throws DirectoryNotDeletedException
    {
        Path importDir = getImportDirectory().toPath();
        if (Files.exists(importDir) && !FileUtil.deleteDir(importDir, logger))
        {
            throw new DirectoryNotDeletedException("Could not delete the directory \"" + PipelineService.UNZIP_DIR + "\"");
        }

        return importDir;
    }

    @Override
    public String relativePath(File file)
    {
        File root = findRootPath(file);
        if (root == null)
        {
            return null;
        }
        String strRoot = root.toString();

        String strFile = file.toString();
        if (!strFile.toLowerCase().startsWith(strRoot.toLowerCase()))
            return null;
        String ret = strFile.substring(strRoot.length());
        if (ret.startsWith(File.separator))
        {
            return ret.substring(1);
        }
        return ret;
    }

    @Override
    public String relativePath(Path path)
    {
        Path root = findRootPath(path);
        if (root == null)
        {
            return null;
        }
        String strRoot = root.toString();

        String strPath = path.toString();
        if (!strPath.toLowerCase().startsWith(strRoot.toLowerCase()))
            return null;
        String ret = strPath.substring(strRoot.length());
        if (ret.startsWith(File.separator) || ret.startsWith("/"))
        {
            return ret.substring(1);
        }
        return ret;
    }

    @Override
    public boolean isUnderRoot(File file)
    {
        return findRootPath(file) != null;
    }

    @Override
    public boolean isUnderRoot(Path path)
    {
        return findRootPath(path) != null;
    }

    // UNDONE: need wrappers for file download/upload permissions
    @Override
    public boolean hasPermission(Container container, User user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm) && container.hasPermission(user, perm);
    }

    // UNDONE: need wrappers for file download/upload permissions
    @Override
    public void requiresPermission(Container container, User user, Class<? extends Permission> perm)
    {
        if (!hasPermission(container, user, perm))
        {
            throw new UnauthorizedException();
        }
    }

    @Override
    public String getEntityId()
    {
        return _entityId;
    }

    @Override
    @NotNull
    public String getResourceId()
    {
        // if the root is a file-based default, it won't have an entityId, so default to containerId
        if (_entityId == null)
            return _containerId;

        return _entityId;
    }

    @Nullable
    public String getCloudStoreName()
    {
        return _cloudStoreName;
    }

    @Override
    public boolean isCloudRoot()
    {
        return ROOT_BASE.cloud.equals(_defaultRoot);
    }

    @Override
    @NotNull
    public String getResourceName()
    {
        String fileName = FileUtil.getFileName(getRootNioPath());
        return fileName.isEmpty() ? "Root for " + getContainer().getName() : fileName;
    }

    @Override
    @NotNull
    public String getResourceDescription()
    {
        return "The pipeline root directory " + getResourceName();
    }

    @Override
    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getModule(PipelineService.MODULE_NAME);
    }

    @Override
    public SecurableResource getParentResource()
    {
        return getContainer();
    }

    @Override
    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Container getResourceContainer()
    {
        return getContainer();
    }

    @Override
    public boolean mayInheritPolicy()
    {
        // configured pipeline roots should not inherit policies from the container, but default pipeline root does
        return isFileRoot();
    }

    @Override
    public boolean isSearchable()
    {
        return _searchable;
    }

    @Override
    public String getWebdavURL()
    {
        String davName = _defaultRoot.getDavName();
        Container c = getContainer();
        if (null == c)
            return null;
        return FilesWebPart.getRootPath(getContainer(), davName, _cloudStoreName);
    }

    @Override
    public List<String> validate()
    {
        List<String> result = new ArrayList<>();
        if (null != _uris)
        {
            for (URI uri : _uris)
            {
                if (null != uri && StringUtils.isNotBlank(uri.toString()) && !FileUtil.hasCloudScheme(uri))
                {
                    Path rootPath = Path.of(uri);
                    if (!NetworkDrive.exists(rootPath))
                    {
                        result.add("Pipeline root does not exist.");
                    }
                    else if (!Files.isDirectory(rootPath))
                    {
                        result.add("Pipeline root is not a directory.");
                    }
                    else if (URIUtil.resolve(uri, uri, "test") == null)
                    {
                        result.add("Pipeline root is invalid.");
                    }
                }
                else if (isCloudRoot() && !isCloudStoreEnabled())
                {
                    result.add("Cloud store not enabled.");
                }
            }
        }
        else
        {
            result.add("Pipeline root is invalid.");
        }
        return result;
    }

    @Override
    public boolean isValid()
    {
        return validate().isEmpty();
    }

    @Override
    public void configureForm(SetupForm form)
    {
        if (_uris.size() > 0)
        {
            String uriPath = _uris.get(0).getPath();
            if (ROOT_BASE.cloud.equals(_defaultRoot))
            {
                form.setPath(getCloudDirName() + (StringUtils.isNotBlank(uriPath) ? "/" + uriPath : ""));
            }
            else
            {
                form.setPath(uriPath);
            }
            if (_uris.size() > 1)
            {
                form.setSupplementalPath(_uris.get(1).getPath());
            }

            form.setSearchable(isSearchable());
        }
        else
        {
            form.setPath("");
        }
    }

    @Override
    public boolean isFileRoot()
    {
        return ROOT_BASE.files == _defaultRoot;
    }

    @Override
    public String toString()
    {
        if (isCloudRoot())
            return getCloudDirName();

        StringBuilder result = new StringBuilder();
        String separator = "";
        for (URI rootUri : getRootURIs())
        {
            result.append(separator);
            separator = " and supplemental location ";
            result.append("'");
            result.append(FileUtil.getAbsolutePath(getContainer(), rootUri));
            result.append("'");
        }
        return result.toString();
    }

    private String getCloudDirName()
    {
        return FileContentService.CLOUD_ROOT_PREFIX + "/" + _cloudStoreName;
    }

    private boolean isCloudStoreEnabled()
    {
        return CloudStoreService.get().isEnabled(_cloudStoreName, getContainer());
    }
}
