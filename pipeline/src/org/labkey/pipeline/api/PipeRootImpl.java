/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.DirectoryNotDeletedException;
import org.labkey.api.pipeline.GlobusKeyPair;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PipeRootImpl implements PipeRoot
{
    private static final String SYSTEM_DIRECTORY_NAME = ".labkey";
    private static final String SYSTEM_DIRECTORY_LEGACY = "system";

    private String _containerId;
    private final URI[] _uris;
    private transient File[] _rootPaths;
    private final String _entityId;
    private transient final GlobusKeyPairImpl _keyPair;
    private final boolean _searchable;
    /** true if this root is based on the site or project default file root */
    private boolean _isDefaultRoot;

    // No-args constructor to support de-serialization in Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    public PipeRootImpl() throws URISyntaxException
    {
        _uris = null;
        _entityId = null;
        _keyPair = null;
        _searchable = false;
    }

    public PipeRootImpl(PipelineRoot root, boolean isDefaultRoot)
    {
        this(root);
        _isDefaultRoot = isDefaultRoot;
    }

    public PipeRootImpl(PipelineRoot root)
    {
        _containerId = root.getContainerId();
        _uris = new URI[root.getSupplementalPath() == null ? 1 : 2];
        try
        {
            _uris[0] = new URI(root.getPath());
            if (root.getSupplementalPath() != null)
            {
                _uris[1] = new URI(root.getSupplementalPath());
            }
        }
        catch (URISyntaxException e)
        {
            throw new UnexpectedException(e);
        }
        _entityId = root.getEntityId();
        _searchable = root.isSearchable();
        if (root.getKeyBytes() != null && root.getCertBytes() != null)
        {
            _keyPair = new GlobusKeyPairImpl(root.getKeyBytes(), root.getKeyPassword(), root.getCertBytes());
        }
        else
        {
            _keyPair = null;
        }
    }

    @NotNull
    public File ensureSystemDirectory()
    {
        File root = getRootPath();
        File systemDir = new File(root, SYSTEM_DIRECTORY_NAME);
        if (!NetworkDrive.exists(systemDir))
        {
            systemDir.mkdirs();

            File systemDirLegacy = new File(root, SYSTEM_DIRECTORY_LEGACY);
            if (systemDirLegacy.exists())
            {
                for (File f : systemDirLegacy.listFiles())
                    f.renameTo(systemDir);
            }

            for (PipelineProvider provider : PipelineService.get().getPipelineProviders())
                provider.initSystemDirectory(root, systemDir);
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
        return _uris[0];
    }

    @NotNull
    public File getRootPath()
    {
        return getRootPaths()[0];
    }

    public synchronized File[] getRootPaths()
    {
        if (_rootPaths == null)
        {
            _rootPaths = new File[_uris.length];
            for (int i = 0; i < _uris.length; i++)
            {
                _rootPaths[i] = new File(_uris[i]);
                NetworkDrive.ensureDrive(_rootPaths[i].getPath());
            }
        }
        return _rootPaths;
    }

    private File findRootPath(File file)
    {
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

    private final String IMPORT_DIRECTORY_NAME = "unzip";
    public File getImportDirectoryPathAndEnsureDeleted() throws DirectoryNotDeletedException, FileNotFoundException
    {
        File importDir = resolvePath(IMPORT_DIRECTORY_NAME);
        if (null == importDir)
            throw new FileNotFoundException();

        if (importDir.exists() && !FileUtil.deleteDir(importDir))
        {
            throw new DirectoryNotDeletedException("Import failed: Could not delete the directory \"" + IMPORT_DIRECTORY_NAME + "\"");
        }
        return importDir;
    }

    public void deleteImportDirectory() throws DirectoryNotDeletedException
    {
        File importDir = resolvePath(IMPORT_DIRECTORY_NAME);
        if (null != importDir && importDir.exists() && !FileUtil.deleteDir(importDir))
        {
            throw new DirectoryNotDeletedException("Could not delete the directory \"" + IMPORT_DIRECTORY_NAME + "\"");
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

    public boolean isUnderRoot(File file)
    {
        return findRootPath(file) != null;
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

    public GlobusKeyPair getGlobusKeyPair()
    {
        return _keyPair;
    }

    @NotNull
    public String getResourceId()
    {
        // if the root is a file-based default, it won't have an entityId, so default to containerId
        if (_entityId == null)
            return _containerId;

        return _entityId;
    }

    @NotNull
    public String getResourceName()
    {
        return getRootPath().getName();
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
        //pipeline roots should not inherit policies from the container!
        return false;
    }

    public boolean isSearchable()
    {
        return _searchable;
    }

    public String getWebdavURL()
    {
        String davName = _isDefaultRoot ? FileContentService.FILES_LINK : FileContentService.PIPELINE_LINK;
        Container c = getContainer();
        assert null != c;
        if (null == c)
            return null;
        return FilesWebPart.getRootPath(getContainer(), davName);
    }

    @Override
    public List<String> validate()
    {
        List<String> result = new ArrayList<>();
        int i = 0;
        for (File rootPath : getRootPaths())
        {
            if (!NetworkDrive.exists(rootPath))
            {
                result.add("Pipeline root does not exist.");
            }
            else if (!rootPath.isDirectory())
            {
                result.add("Pipeline root is not a directory.");
            }
            else if (URIUtil.resolve(_uris[i], _uris[i], "test") == null)
            {
                result.add("Pipeline root is invalid.");
            }
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
        File[] roots = getRootPaths();
        form.setPath(roots[0].getPath());
        if (roots.length > 1)
        {
            form.setSupplementalPath(roots[1].getPath());
        }

        form.setGlobusKeyPair(getGlobusKeyPair());
        form.setSearchable(isSearchable());

    }

    @Override
    public boolean isDefault()
    {
        return _isDefaultRoot;
    }

    @Override
    public String toString()
    {
        StringBuilder result = new StringBuilder();
        String separator = "";
        for (File rootPath : getRootPaths())
        {
            result.append(separator);
            separator = " and supplemental location ";
            result.append("'");
            result.append(rootPath);
            result.append("'");
        }
        return result.toString();
    }
}
