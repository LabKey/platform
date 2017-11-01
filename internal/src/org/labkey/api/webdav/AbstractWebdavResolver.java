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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public abstract class AbstractWebdavResolver implements WebdavResolver
{
    @Nullable
    @Override
    public LookupResult lookupEx(Path fullPath)
    {
        if (fullPath == null || !fullPath.startsWith(getRootPath()))
            return null;
        Path path = getRootPath().relativize(fullPath).normalize();

        WebdavResource root = getRoot();
        if (path.size() == 0)
            return new LookupResult(this,root);

        // start at the root and work down, to avoid lots of cache misses
        WebdavResource resource = root;
        for (String name : path)
        {
            WebdavResource r = resource.find(name);
            // short circuit the descent at last web folder
            if (null == r  || r instanceof UnboundResource)
                return new LookupResult(this,new UnboundResource(fullPath));
            resource = r;
        }
        if (null == resource)
            resource = new UnboundResource(fullPath);
        return new LookupResult(this,resource);
    }

    protected abstract WebdavResource getRoot();

    @Nullable
    @Override
    public WebdavResource welcome()
    {
        return lookup(Path.rootPath);
    }

    @Override
    public boolean isStaticContent()
    {
        return false;
    }

    public static class UnboundResource extends AbstractWebdavResource
    {
        public UnboundResource(String path)
        {
            super(Path.parse(path));
        }

        public UnboundResource(Path path)
        {
            super(path);
        }

        public boolean exists()
        {
            return false;
        }

        public boolean isCollection()
        {
            return false;
        }

        public boolean isFile()
        {
            return false;
        }

        @Override
        public Set<Class<? extends Permission>> getPermissions(User user)
        {
            return Collections.emptySet();
        }

        public WebdavResource find(String name)
        {
            return new UnboundResource(this.getPath().append(name));
        }

        public Collection<String> listNames()
        {
            return Collections.emptyList();
        }

        public Collection<WebdavResource> list()
        {
            return Collections.emptyList();
        }

        public long getCreated()
        {
            return Long.MIN_VALUE;
        }

        public long getLastModified()
        {
            return Long.MIN_VALUE;
        }

        public InputStream getInputStream(User user) throws IOException
        {
            return null;
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public long getContentLength()
        {
            return 0;
        }

        @NotNull
        public Collection<History> getHistory()
        {
            return Collections.emptyList();
        }
    }


    public abstract class AbstractWebFolderResource extends AbstractWebdavResourceCollection implements WebdavResolver.WebFolder
    {
        protected WebdavResolver _resolver;
        final Container _c;
        protected ArrayList<String> _children = null;

        protected AbstractWebFolderResource(WebdavResolver resolver, Container c)
        {
            super(resolver.getRootPath().append(c.getParsedPath()), resolver);
            _resolver = resolver;
            _c = c;
            _containerId = c.getId();
            setPolicy(c.getPolicy());
        }

        @Override
        public long getCreated()
        {
            return null != _c && null != _c.getCreated() ? _c.getCreated().getTime() : Long.MIN_VALUE;
        }

        @Override
        public User getCreatedBy()
        {
            return UserManager.getUser(_c.getCreatedBy());
        }

        @Override
        public long getLastModified()
        {
            return getCreated();
        }

        @Override
        public User getModifiedBy()
        {
            return getCreatedBy();
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            // context
            Path contextPath = null==context ? AppProps.getInstance().getParsedContextPath() : Path.parse(context.getContextPath());
            // _webdav
            Path path = contextPath.append(getPath().get(0)).append(getContainerId());
            return path.encode("/", "/");
        }

        public Container getContainer()
        {
            return _c;
        }

        public boolean exists()
        {
            return true;
        }

        public boolean isCollection()
        {
            return exists();
        }

        public synchronized List<String> getWebFoldersNames()
        {
            if (null == _children)
            {
                List<Container> list = ContainerManager.getChildren(_c);
                ArrayList<String> children = new ArrayList<>(list.size() + 2);
                for (Container aList : list)
                    children.add(aList.getName());

                for (WebdavService.Provider p : WebdavService.get().getProviders())
                {
                    Set<String> s = p.addChildren(this);
                    if (s != null)
                        children.addAll(s);
                }
                // providers might not be registred if !isStartupComplete();
                if (!ModuleLoader.getInstance().isStartupComplete())
                    return children;
                _children = children;
            }
            return _children;
        }


        @Override
        public boolean canCreateCollection(User user, boolean forCreate)
        {
            return false;
        }

        @Override
        public boolean canCreate(User user, boolean forCreate)
        {
            return false;
//            return null != _attachmentResource && _attachmentResource.canCreate(user);
        }

        @Override
        public boolean canRename(User user, boolean forRename)
        {
            return false;
        }

        @Override
        public boolean canDelete(User user, boolean forDelete, List<String> message)
        {
            return false;
        }

        @Override
        public boolean canWrite(User user, boolean forWrite)
        {
            return false;
        }


        @NotNull
        public Collection<String> listNames()
        {
            Set<String> set = new TreeSet<>();
//            if (null != _attachmentResource)
//                set.addAll(_attachmentResource.listNames());
            set.addAll(getWebFoldersNames());
            ArrayList<String> list = new ArrayList<>(set);
            Collections.sort(list);
            return list;
        }

    }

    public abstract class AbstractWebdavListener extends ContainerManager.AbstractContainerListener
    {
        public void containerCreated(Container c, User user)
        {
            invalidate(c.getParsedPath().getParent(), false);
        }

        public void containerDeleted(Container c, User user)
        {
            invalidate(c.getParsedPath(), true);
            invalidate(c.getParsedPath().getParent(), false);
        }

        public void propertyChange(PropertyChangeEvent pce)
        {
            ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)pce;
            Container c = evt.container;
            try
            {
                switch (evt.property)
                {
                    case PipelineRoot:
                    case Policy:
                    case AttachmentDirectory:
                    case WebRoot:
                    case EndpointDirectory:
                    default:
                    {
                        invalidate(c.getParsedPath(), true);
                        break;
                    }
                    case Name:
                    {
                        String oldName = (String)evt.getOldValue();
                        invalidate(c.getParsedPath(), true);
                        invalidate(resolveSibling(c, oldName), true);
                        invalidate(c.getParsedPath().getParent(), false);
                        break;
                    }
                    case Parent:
                    {
                        Container oldParent = (Container)pce.getOldValue();
                        invalidate(c.getParsedPath(), true);
                        invalidate(getParentPath(c), false);
                        invalidate(resolveSibling(c,c.getName()), true);
                        invalidate(oldParent.getParsedPath(), false);
                        break;
                    }
                    case SiteRoot:
                        clearFolderCache();
                        break;
                }
            }
            catch (Exception x)
            {
                clearFolderCache();
            }
        }


        Path getParentPath(Container c)
        {
            Path p = c.getParsedPath();
            if (p.size() == 0)
                throw new IllegalArgumentException();
            return p.getParent();
        }


        Path resolveSibling(Container c, String name)
        {
            Path p = c.getParsedPath();
            if (p.size() == 0)
                throw new IllegalArgumentException();
            return p.getParent().append(name);
        }

        abstract protected void clearFolderCache();

        abstract protected void invalidate(Path containerPath, boolean recursive);
    }


}
