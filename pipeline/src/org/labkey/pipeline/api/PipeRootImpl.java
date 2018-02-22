/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private final String _entityId;
    private final boolean _searchable;
    private String _cloudStoreName;         // Only used for cloud

    private enum ROOT_BASE
    {
        files
        {
            String getDavName()
            {
                return FileContentService.FILES_LINK;
            }
        },
        pipeline
        {
            String getDavName()
            {
                return FileContentService.PIPELINE_LINK;
            }
        },
        cloud
        {
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
    public PipeRootImpl() throws URISyntaxException
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

    @NotNull
    public File ensureSystemDirectory()
    {
        Path path = ensureSystemDirectoryPath();
        if (FileUtil.hasCloudScheme(path))
            throw new RuntimeException("System Dir is not on file system.");
        return path.toFile();
    }

    @NotNull
    public Path ensureSystemDirectoryPath()
    {
        Path root = getRootNioPath();
        Path systemDir = root.resolve(SYSTEM_DIRECTORY_NAME);
        if (!Files.exists(systemDir))
        {
            try
            {
                Files.createDirectories(systemDir);

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

    public Container getContainer()
    {
        return ContainerManager.getForId(_containerId);
    }

    @NotNull
    public URI getUri()
    {
        if (_uris.size() == 0)
            throw new IllegalStateException("No URI.");
        return _uris.get(0);
    }

    @NotNull
    public File getRootPath()
    {
        if (getRootPaths().size() == 0)
            throw new RuntimeException("No root path set.");
        return getRootPaths().get(0);
    }

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

    @NotNull
    public File getLogDirectory()
    {
        // If pipeline root is in File system, return that; otherwise return temp directory
        if (isCloudRoot())
            return FileUtil.getTempDirectory();
        else
            return getRootPath();
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

    public List<URI> getRootURIs()
    {
        return _uris;
    }

    private File findRootPath(File file)
    {
        if (isCloudRoot())
            return null;
        
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

        // First, see if the file is under the root as it is:
        if (URIUtil.isDescendant(root.toUri(), file.toUri()))
        {
            return root;
        }
        return null;
    }

    @Nullable
    public File resolvePath(String path)
    {
        if (null == path)
            throw new NotFoundException("Must specify a file path");

        // Remove leading "./" sometimes added by the client side FileBrowser
        if (path.startsWith("./"))
            path = path.substring(2);

        // Check if the file already exists on disk
        for (File root : getRootPaths())
        {
            File file = new File(root, path);
            // Check that it's under the root to protect against ../../ type paths
            if (file.exists() && isUnderRoot(file))
            {
                return file;
            }
        }

        // Return the path to the default location
        File root = getRootPath();
        File file = FileUtil.getAbsoluteCaseSensitiveFile(new File(root, path));
        // Check that it's under the root to protect against ../../ type paths
        if (!isUnderRoot(file))
        {
            return null;
        }
        return file;
    }

    @Nullable
    public Path resolveToNioPath(String path)
    {
        if (null == path)
            throw new NotFoundException("Must specify a file path");

        if (ROOT_BASE.cloud.equals(_defaultRoot))
        {
            // Remove leading "./" sometimes added by the client side FileBrowser
            if (path.startsWith("./"))
                path = path.substring(2);

            // Return the path to the default location
            org.labkey.api.util.Path combinedPath = StringUtils.isNotBlank(_uris.get(0).getPath()) ?
                    new org.labkey.api.util.Path(_uris.get(0).getPath(), path) :
                    new org.labkey.api.util.Path(path);
            return CloudStoreService.get().getPath(getContainer(), _cloudStoreName, combinedPath);
            // TODO: Do we need? Check that it's under the root to protect against ../../ type paths
        }
        else
        {
            File  file = resolvePath(path);
            return null != file ? file.toPath() : null;
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

    @NotNull
    public File getImportDirectory()
    {
        // If pipeline root is in File system, return that; otherwise return temp directory
        File root = isCloudRoot() ?
            FileUtil.getTempDirectory() :
            getRootPath();
        return new File(root, PipelineService.UNZIP_DIR);
    }

    public File getImportDirectoryPathAndEnsureDeleted() throws DirectoryNotDeletedException, FileNotFoundException
    {
        File importDir = getImportDirectory();

        if (importDir.exists() && !FileUtil.deleteDir(importDir))
            throw new DirectoryNotDeletedException("Import failed: Could not delete the directory \"" + PipelineService.UNZIP_DIR + "\"");

        return importDir;
    }

    public void deleteImportDirectory() throws DirectoryNotDeletedException
    {
        File importDir = getImportDirectory();
        if (importDir.exists() && !FileUtil.deleteDir(importDir))
        {
            throw new DirectoryNotDeletedException("Could not delete the directory \"" + PipelineService.UNZIP_DIR + "\"");
        }
    }

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

    public boolean isUnderRoot(File file)
    {
        return findRootPath(file) != null;
    }

    public boolean isUnderRoot(Path path)
    {
        return findRootPath(path) != null;
    }

    // UNDONE: need wrappers for file download/upload permissions
    public boolean hasPermission(Container container, User user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm) && container.hasPermission(user, perm);
    }

    // UNDONE: need wrappers for file download/upload permissions
    public void requiresPermission(Container container, User user, Class<? extends Permission> perm)
    {
        if (!hasPermission(container, user, perm))
        {
            throw new UnauthorizedException();
        }
    }

    public String getEntityId()
    {
        return _entityId;
    }

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

    @NotNull
    public String getResourceName()
    {
        return FileUtil.getFileName(getRootNioPath());
    }

    @NotNull
    public String getResourceDescription()
    {
        return "The pipeline root directory " + getResourceName();
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getModule(PipelineService.MODULE_NAME);
    }

    public SecurableResource getParentResource()
    {
        return getContainer();
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        return Collections.emptyList();
    }

    @NotNull
    public Container getResourceContainer()
    {
        return getContainer();
    }

    public boolean mayInheritPolicy()
    {
        // configured pipeline roots should not inherit policies from the container, but default pipeline root does
        return isDefault();
    }

    public boolean isSearchable()
    {
        return _searchable;
    }

    public String getWebdavURL()
    {
        String davName = _defaultRoot.getDavName();
        Container c = getContainer();
        assert null != c;
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
                    File rootPath = new File(uri);
                    if (!NetworkDrive.exists(rootPath))
                    {
                        result.add("Pipeline root does not exist.");
                    }
                    else if (!rootPath.isDirectory())
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
                form.setSupplementalPath(_uris.get(0).getPath());
            }

            form.setSearchable(isSearchable());
        }
        else
        {
            form.setPath("");
        }
    }

    @Override
    public boolean isDefault()
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
