/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.view.FilesWebPart;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.GlobusKeyPair;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.*;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class PipeRootImpl implements PipeRoot
{
    private static final String SYSTEM_DIRECTORY_NAME = ".labkey";
    private static final String SYSTEM_DIRECTORY_LEGACY = "system";

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

    private String _containerId;
    private final URI[] _uris;
    private transient File[] _rootPaths;
    private final String _entityId;
    private transient final GlobusKeyPairImpl _keyPair;
    private final boolean _searchable;
    /** true if this root is based on the site or project default file root */
    private boolean _isDefaultRoot;

    public PipeRootImpl(PipelineRoot root, boolean isDefaultRoot) throws URISyntaxException
    {
        this(root);
        _isDefaultRoot = isDefaultRoot;
    }

    public PipeRootImpl(PipelineRoot root) throws URISyntaxException
    {
        _containerId = root.getContainerId();
        _uris = new URI[root.getSupplementalPath() == null ? 1 : 2];
        _uris[0] = new URI(root.getPath());
        if (root.getSupplementalPath() != null)
        {
            _uris[1] = new URI(root.getSupplementalPath());
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

    public File resolvePath(String path)
    {
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
        File file = new File(root, path);
        // Check that it's under the root to protect against ../../ type paths
        if (!isUnderRoot(file))
        {
            return null;
        }
        return file;
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
            HttpView.throwUnauthorized();
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
    public Set<Class<? extends Permission>> getRelevantPermissions()
    {
        //TODO: review this--what are the relevant permissions for a pipeline root?
        Set<Class<? extends Permission>> perms = new HashSet<Class<? extends Permission>>();
        perms.add(ReadPermission.class);
        perms.add(InsertPermission.class);
        perms.add(UpdatePermission.class);
        perms.add(DeletePermission.class);
        return perms;
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
        return FilesWebPart.getRootPath(getContainer(), davName);
    }

    @Override
    public boolean isValid()
    {
        return NetworkDrive.exists(getRootPath()) && getRootPath().isDirectory();
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
    public String toString()
    {
        return "Pipeline root pointed at " + getRootPath();
    }
}
