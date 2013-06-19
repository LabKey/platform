/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 10:00:49 AM
 */
public abstract class AbstractWebdavResource extends AbstractResource implements WebdavResource
{
    private SecurityPolicy _policy;
    protected String _containerId;

    protected String _etag = null;
    protected Map<String, Object> _properties = null;

    protected AbstractWebdavResource(Path path)
    {
        this(path, WebdavService.get().getResolver());
    }

    protected AbstractWebdavResource(Path path, WebdavResolver resolver)
    {
        super(path, resolver);
    }

    protected AbstractWebdavResource(Path folder, String name)
    {
        this(folder, name, WebdavService.get().getResolver());
    }

    protected AbstractWebdavResource(Path folder, String name, WebdavResolver resolver)
    {
        super(folder, name, resolver);
    }

    protected AbstractWebdavResource(Resource folder, String name)
    {
        super(folder, name, WebdavService.get().getResolver());
    }

    @Override
    public WebdavResolver getResolver()
    {
        return (WebdavResolver)super.getResolver();
    }

    public WebdavResource parent()
    {
        Path p = getPath();
        if (p.getNameCount()==0)
            return null;
        Path parent = p.getParent();
        return WebdavService.get().lookup(parent);
    }

    public WebdavResource find(String name)
    {
        return null;
    }

    public Collection<? extends WebdavResource> list()
    {
        return Collections.emptyList();
    }

    public long getCreated()
    {
        return getLastModified();
    }

    public User getCreatedBy()
    {
        return null;
    }

    public void setLastIndexed(long indexed, long modified)
    {
        if (isFile())
            ServiceRegistry.get().getService(SearchService.class).setLastIndexedForPath(getPath(), indexed, modified);
    }

    public User getModifiedBy()
    {
        return null;
    }

    public String getDescription()
    {
        return null;
    }

    public String getContentType()
    {
        if (isCollection())
            return "text/html";
        return PageFlowUtil.getContentTypeFor(getName());
    }


    @NotNull
    public String getHref(ViewContext context)
    {
        ActionURL url = null==context ? null : context.getActionURL();
        int port = null==url ? AppProps.getInstance().getServerPort() : url.getPort();
        String host = null==url ? AppProps.getInstance().getServerName() : url.getHost();
        String scheme = null==context ? AppProps.getInstance().getScheme() : url.getScheme();
        boolean defaultPort = "http".equals(scheme) && 80 == port || "https".equals(scheme) && 443 == port;
        String portStr = defaultPort ? "" : ":" + port;
        return c(scheme + "://" + host + portStr, getLocalHref(context));
    }


    @NotNull
    public String getLocalHref(ViewContext context)
    {
        String contextPath = null==context ? AppProps.getInstance().getContextPath() : context.getContextPath();
        String href = c(contextPath, getPath().encode());
        if (isCollection() && !href.endsWith("/"))
            href += "/";
        return href;
    }


    public String getExecuteHref(ViewContext context)
    {
        String path = parent().getExecuteHref(context);
        path += (path.endsWith("/")?"":"/") + PageFlowUtil.encode(getPath().getName());
        return path;
    }


    public String getIconHref()
    {
        if (isCollection())
            return AppProps.getInstance().getContextPath() + "/_icons/folder.gif";
        return AppProps.getInstance().getContextPath() + Attachment.getFileIcon(getName());
    }


    public String getETag(boolean force)
    {
        long len = 0;
        if (null == _etag)
        {
            try
            {
                len = getContentLength();
            }
            catch (IOException x)
            {
                /* */
            }
            _etag = "W/\"" + len + "-" + getLastModified() + "\"";
        }
        return _etag;
    }


    public String getETag()
    {
        return getETag(false);
    }


    public Map<String, ?> getProperties()
    {
        if (null == _properties)
            return Collections.emptyMap();
        Map<String, ?> ret = _properties;
        assert null != (ret = Collections.unmodifiableMap(ret));
        return ret;
    }

    public Map<String, Object> getMutableProperties()
    {
        if (null == _properties)
            _properties = new HashMap<String,Object>();
        return _properties;
    }
    
    public InputStream getInputStream() throws IOException
    {
        return getInputStream(null);
    }

    /** provides one place to completely block access to the resource */
    protected boolean hasAccess(User user)
    {
        return true;
    }


    protected SecurityPolicy getPolicy()
    {
        return _policy;
    }


    protected void setPolicy(SecurityPolicy policy)
    {
        _policy = policy;
    }

    /** permissions */



    public boolean canList(User user, boolean forRead)
    {
        return canRead(user, forRead);
    }


    public boolean canRead(User user, boolean forRead)
    {
        if ("/".equals(getPath()))
            return true;
        try
        {
            SecurityLogger.indent(getPath() + " AbstractWebdavResource.canRead()");
            if (!hasAccess(user))
            {
                SecurityLogger.log("hasAccess()==false",user,null,false);
                return false;
            }
            return getPermissions(user).contains(ReadPermission.class);
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    public boolean canWrite(User user, boolean forWrite)
    {
        return hasAccess(user) && !user.isGuest() && getPermissions(user).contains(UpdatePermission.class);
    }


    public boolean canCreate(User user, boolean forCreate)
    {
        return hasAccess(user) && !user.isGuest() && getPermissions(user).contains(InsertPermission.class);
    }

    public boolean canCreateCollection(User user, boolean forCreate)
    {
        return canCreate(user, forCreate);
    }

    public boolean canDelete(User user, boolean forDelete)
    {
        if (user.isGuest() || !hasAccess(user))
            return false;
        Set<Class<? extends Permission>> perms = getPermissions(user);
        return perms.contains(UpdatePermission.class) || perms.contains(DeletePermission.class);
    }


    public boolean canRename(User user, boolean forRename)
    {
        return hasAccess(user) && !user.isGuest() && canCreate(user, forRename) && canDelete(user, forRename);
    }


    public Set<Class<? extends Permission>> getPermissions(User user)
    {
        return getPolicy().getPermissions(user);
    }


    public boolean delete(User user) throws IOException
    {
        assert null == user || canDelete(user, true);
        return false;
    }
    
    public File getFile()
    {
        return null;
    }

    public long copyFrom(User user, WebdavResource r) throws IOException
    {
        return copyFrom(user, r.getFileStream(user));
    }


    public void moveFrom(User user, WebdavResource src) throws IOException
    {
        copyFrom(user, src);
        src.delete(user);
    }


    @NotNull
    public Collection<WebdavResolver.History> getHistory()
    {
        return Collections.emptyList();
    }
    

    @NotNull
    public Collection<NavTree> getActions(User user)
    {
        return Collections.emptyList();
    }

    public static String c(String path, String... names)
    {
        StringBuilder s = new StringBuilder();
        s.append(StringUtils.stripEnd(path,"/"));
        for (String name : names)
        {
            String bare = StringUtils.strip(name, "/");
            if (bare.length() > 0)
                s.append("/").append(bare);
        }
        return s.toString();
    }

    public FileStream getFileStream(User user) throws IOException
    {
        return new _FileStream(user);
    }

    private class _FileStream implements FileStream
    {
        User _user;
        InputStream _is = null;

        _FileStream(User user)
        {
            _user = user;
        }

        public long getSize()
        {
            try
            {
                return getContentLength();
            }
            catch (IOException x)
            {
                throw new RuntimeException(x);
            }
        }

        public InputStream openInputStream() throws IOException
        {
            if (null == _is)
                _is = getInputStream(_user);
            return _is;
        }

        public void closeInputStream() throws IOException
        {
            IOUtils.closeQuietly(_is);
        }
    }

    public void createLink(String name, Path target)
    {
        throw new UnsupportedOperationException();
    }

    public void removeLink(String name)
    {
        throw new UnsupportedOperationException();
    }


    //
    // SearchService
    //
    public String getDocumentId()
    {
        return "dav:" + getPath().toString();
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public boolean shouldIndex()
    {
        // TODO would be nice to call DavController.isTempFile()
        String name = getName();
        if (name.startsWith(".part"))
            return false;
        return true;
    }

    public Map<String, String> getCustomProperties(User user)
    {
        return Collections.emptyMap();
    }

    public void notify(ContainerUser context, String message)
    {
    }

    protected void setProperty(String key, String value)
    {
        if (_properties == null)
            _properties = new HashMap<String, Object>();
        _properties.put(key,value);
    }

    protected void setSearchProperty(SearchService.PROPERTY searchProperty, String value)
    {
        setProperty(searchProperty.toString(), value);
    }

    protected void setSearchCategory(SearchService.SearchCategory category)
    {
        setSearchProperty(SearchService.PROPERTY.categories,category.toString());
    }

    @Override
    public StackTraceElement[] getCreationStackTrace()
    {
        return _creationStackTrace;
    }
}
