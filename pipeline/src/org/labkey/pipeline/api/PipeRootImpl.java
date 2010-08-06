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

    public static File ensureRoot(URI uriRoot)
    {
        File file = new File(uriRoot);
        NetworkDrive.ensureDrive(file.getPath());
        return file;
    }

    private Container _container;
    @NotNull private final URI _uri;
    private File _rootPath;
    private final String _entityId;
    private final GlobusKeyPairImpl _keyPair;
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
        _container = ContainerManager.getForId(root.getContainerId());
        _uri = new URI(root.getPath());
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
        return _container;
    }

    @NotNull
    public URI getUri()
    {
        return _uri;
    }

    @NotNull
    synchronized public File getRootPath()
    {
        if (_rootPath == null)
            _rootPath = ensureRoot(_uri);
        return _rootPath;
    }

    public File resolvePath(String path)
    {
        File root = getRootPath();
        File file = new File(root, path);
        if (!isUnderRoot(file))
        {
            return null;
        }
        return file;
    }

    public String relativePath(File file)
    {
        String strRoot = getRootPath().toString();
        if (!isUnderRoot(file))
            return null;

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
        // First, see if the file is under the root as it is:
        if (isUnderRoot(file.toURI()))
        {
            return true;
        }
        // Next, see if canonicalizing both the root and the file causes them to be under each other.
        // Canonicalizing the path both standardizes the filename case, but also resolves symbolic links.

        try
        {
            File canFile = file.getCanonicalFile();
            File canRoot = new File(_uri).getCanonicalFile();
            return URIUtil.isDescendant(canRoot.toURI(), canFile.toURI());
        }
        catch (IOException e) {
            return false;
        }
    }

    public boolean isUnderRoot(URI uri)
    {
        return URIUtil.isDescendant(_uri, uri);
    }

    // UNDONE: need wrappers for file download/upload permissions
    public boolean hasPermission(Container container, User user, Class<? extends Permission> perm)
    {
        return _container.hasPermission(user, perm) && container.hasPermission(user, perm);
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
            return _container != null ? _container.getResourceId() : null;

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
}
