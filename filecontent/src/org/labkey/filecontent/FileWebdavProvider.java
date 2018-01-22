/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.AbstractWebdavResourceCollection;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * User: matthewb
 * Date: Oct 22, 2008
 * Time: 8:20:29 AM
 */
public class FileWebdavProvider implements WebdavService.Provider
{
    @Nullable
    public Set<String> addChildren(@NotNull WebdavResource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();

        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        if (svc == null)
        {
            return null;
        }
        // Check for the default file location
        Set<String> result = new HashSet<>();
        java.nio.file.Path root = svc.getFileRootPath(c);
        if (root != null)
        {
            if (!FileUtil.hasCloudScheme(root) && NetworkDrive.exists(root.toFile()))
            {
                result.add(FileContentService.FILES_LINK);
            }
        }

        // Check if there are any named file sets
        for (AttachmentDirectory dir : svc.getRegisteredDirectories(c))
        {
            if (!StringUtils.isEmpty(dir.getLabel()))
            {
                result.add(FileContentService.FILE_SETS_LINK);
                break;
            }
        }
        return result;
    }


    public WebdavResource resolve(@NotNull WebdavResource parent, @NotNull String name)
    {
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
        Container c = folder.getContainer();

        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
        if (null == service)
            return null;
        if (service.isCloudRoot(c))
            return null;

        if (FileContentService.FILE_SETS_LINK.equalsIgnoreCase(name))
            return new _FilesetsFolder(c, parent.getPath());

        if (FileContentService.FILES_LINK.equalsIgnoreCase(name))
        {
            try
            {
                AttachmentDirectory dir = service.getMappedAttachmentDirectory(c, false);
                if (dir != null)
                {
                    return new _FilesResource(parent, name, dir.getFileSystemDirectory(), c.getPolicy());
                }
            }
            catch (MissingRootDirectoryException e)
            {
                // Don't complain here, just hide the @files subfolder
            }
        }

        return null;
    }

    class _FilesResource extends FileSystemResource
    {
        public _FilesResource(WebdavResource folder, String name, File file, SecurityPolicy policy)
        {
            super(folder, name, file, policy);
        }

        public _FilesResource(FileSystemResource folder, String relativePath)
        {
            super(folder,relativePath);
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            Container c = ContainerManager.getForId(getContainerId());
            Path containerPath = null==c ? null : c.getParsedPath();

            assert getPath().startsWith(WebdavService.getPath());
            Path rel = WebdavService.getPath().relativize(getPath());
            if (null != containerPath && rel.startsWith(containerPath))
            {
                rel = containerPath.relativize(rel);
                rel = new Path(c.getId()).append(rel);
            }
            Path fileServlet = new Path("files");
            Path contextPath = Path.parse(AppProps.getInstance().getContextPath());
            Path full = contextPath.append(fileServlet).append(rel);
            return full.encode("/", null) + "?renderAs=DEFAULT";
        }

        @Override
        public WebdavResource find(String name)
        {
            return new _FilesResource(this, name);
        }
    }


    class _FilesetsFolder extends AbstractWebdavResourceCollection
    {
        Container _c;
        HashMap<String,AttachmentDirectory> _map = new HashMap<>();
        ArrayList<String> _names = new ArrayList<>();
        
        _FilesetsFolder(Container c, Path folder)
        {
            super(folder, FileContentService.FILE_SETS_LINK);
            _c = c;
            setPolicy(_c.getPolicy());
            
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            for (AttachmentDirectory dir : svc.getRegisteredDirectories(_c))
            {
                if (StringUtils.isEmpty(dir.getLabel()))
                    continue;
                _map.put(dir.getLabel(), dir);
                _names.add(dir.getLabel());
            }
            _names.sort(String.CASE_INSENSITIVE_ORDER);
        }

        @Override
        public boolean canCreateCollection(User user, boolean canCreate)
        {
            return false;
        }

        public boolean exists()
        {
            return true;
        }

        public long getCreated()
        {
            return Long.MIN_VALUE;
        }

        public long getLastModified()
        {
            return Long.MIN_VALUE;
        }

        @NotNull
        public Collection<String> listNames()
        {
            return Collections.unmodifiableList(_names);
        }

        public WebdavResource find(String name)
        {
            AttachmentDirectory dir = _map.get(name);
            if (dir != null)
            {
                Path path = getPath().append(name);
                return AttachmentService.get().getAttachmentResource(path, dir);
            }
            return null;
        }
    }
}
