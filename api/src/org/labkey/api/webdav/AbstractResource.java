/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.search.SearchService;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
* User: matthewb
* Date: Oct 21, 2008
* Time: 10:00:49 AM
*/
public abstract class AbstractResource implements Resource
{
    protected long _ts = System.currentTimeMillis();                   
    private Path _path;
    protected SecurityPolicy _policy;
    protected String _containerId;
    
    protected String _etag = null;
    protected Map<String,Object> _properties = null;


    protected AbstractResource(Path path)
    {
        this._path = path;
    }

    protected AbstractResource(Path folder, String name)
    {
        this(folder.append(name));
    }

    protected AbstractResource(Resource folder, String name)
    {
        this(folder.getPath().append(name));
    }

    public Path getPath()
    {
        return _path;
    }

    protected void setPath(Path path)
    {
        _path = path;
    }


    public String getName()
    {
        return _path.getName();
    }


    public Resource parent()
    {
        if (_path.getNameCount()==0)
            return null;
        Path parent = _path.getParent();
        return WebdavService.get().lookup(parent);
    }


    public long getCreated()
    {
        return getLastModified();
    }


    public User getCreatedBy()
    {
        return null;
    }


    public long getLastModified()
    {
        return Long.MIN_VALUE;
    }

    public long getLastIndexed()
    {
        return Long.MIN_VALUE;
    }

    public void setLastIndexed(long ms)
    {
        if (isFile())
            ServiceRegistry.get().getService(SearchService.class).setLastIndexedForPath(getPath(), ms);
    }

    public User getModifiedBy()
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
        ActionURL url = context.getActionURL();
        int port = context.getRequest().getServerPort();
        boolean defaultPort = "http".equals(url.getScheme()) && 80 == port || "https".equals(url.getScheme()) && 443 == port;
        String portStr = defaultPort ? "" : ":" + port;
        return c(url.getScheme() + "://" + url.getHost() + portStr, getLocalHref(context));
    }


    @NotNull
    public String getLocalHref(ViewContext context)
    {
        String contextPath = null==context ? AppProps.getInstance().getContextPath() : context.getContextPath();
        String href = c(contextPath, _path.encode());
        if (isCollection() && !href.endsWith("/"))
            href += "/";
        return href;
    }


    public String getExecuteHref(ViewContext context)
    {
        return c(AppProps.getInstance().getContextPath(), getPath().toString());
    }


    public String getIconHref()
    {
        if (isCollection())
            return AppProps.getInstance().getContextPath() + "/" + PageFlowUtil.extJsRoot() + "/resources/images/default/tree/folder.gif";
        return AppProps.getInstance().getContextPath() + Attachment.getFileIcon(getName());
    }


    public String getETag()
    {
        long len = 0;
        try
        {
            len = getContentLength();
        }
        catch (IOException x)
        {
            /* */
        }
        if (null == _etag)
            _etag = "W/\"" + len + "-" + getLastModified() + "\"";
        return _etag;
    }

    public Map<String, ?> getProperties()
    {
        if (null == _properties)
            return Collections.emptyMap();
        Map<String, ?> ret = _properties;
        assert null != (ret = Collections.unmodifiableMap(ret));
        return ret;
    }

    /** provides one place to completely block access to the resource */
    protected boolean hasAccess(User user)
    {
        return true;
    }


    /** permissions */
    public boolean canList(User user)
    {
        return canRead(user);
    }


    public boolean canRead(User user)
    {
        if ("/".equals(_path))
            return true;
        if (user == User.getSearchUser())
            return true;
        return hasAccess(user) && getPermissions(user).contains(ReadPermission.class);
    }


    public boolean canWrite(User user)
    {
        return hasAccess(user) && !user.isGuest() && getPermissions(user).contains(UpdatePermission.class);
    }


    public boolean canCreate(User user)
    {
        return hasAccess(user) && !user.isGuest() && getPermissions(user).contains(InsertPermission.class);
    }

    public boolean canCreateCollection(User user)
    {
        return canCreate(user);
    }

    public boolean canDelete(User user)
    {
        if (user.isGuest() || !hasAccess(user))
            return false;
        Set<Class<? extends Permission>> perms = getPermissions(user);
        return perms.contains(UpdatePermission.class) || perms.contains(DeletePermission.class);
    }


    public boolean canRename(User user)
    {
        return hasAccess(user) && !user.isGuest() && canCreate(user) && canDelete(user);
    }


    public Set<Class<? extends Permission>> getPermissions(User user)
    {
        return _policy.getPermissions(user);
    }


    public boolean delete(User user) throws IOException
    {
        assert null == user || canDelete(user);
        return false;
    }
    
    public File getFile()
    {
        return null;
    }

    public long copyFrom(User user, Resource r) throws IOException
    {
        return copyFrom(user, r.getFileStream(user));
    }


    public void moveFrom(User user, Resource src) throws IOException
    {
        copyFrom(user, src);
        src.delete(user);
    }


    @NotNull
    public List<WebdavResolver.History> getHistory()
    {
        return Collections.emptyList();
    }
    

    @NotNull
    public List<NavTree> getActions()
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
        if (!canRead(user))
            return null;
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
        return true;
    }

    protected void setProperty(String key, String value)
    {
        if (_properties == null)
            _properties = new HashMap<String, Object>();
        _properties.put(key,value);
    }

    protected void setProperty(SearchService.SearchCategory category)
    {
        if (_properties == null)
            _properties = new HashMap<String, Object>();
        _properties.put(SearchService.PROPERTY.category.toString(),category.toString());
    }
}
