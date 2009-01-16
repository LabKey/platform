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

import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.labkey.api.settings.AppProps;
import org.labkey.api.attachments.Attachment;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
* User: matthewb
* Date: Oct 21, 2008
* Time: 10:00:49 AM
*/
public abstract class AbstractResource implements WebdavResolver.Resource
{
    protected long _ts = System.currentTimeMillis();
    private String _path;
    protected ACL _acl;
    protected String _etag = null;

    protected AbstractResource(String path)
    {
        this._path = path;
        assert _path.equals("/") || !_path.endsWith("/");
    }

    protected AbstractResource(String folder, String name)
    {
        this(WebdavResolverImpl.c(folder,name));
    }

    protected AbstractResource(FileSystemResource folder, String name)
    {
        this(WebdavResolverImpl.c(folder,name));
    }

    public String getPath()
    {
        return _path;
    }


    public String getName()
    {
        String p = _path;
        if (p.endsWith("/"))
            p = _path.substring(0,p.length()-1);
        int i = p.lastIndexOf("/");
        return p.substring(i+1);
    }


    public String getParentPath()
    {
        String p = _path;
        if (p.endsWith("/"))
            p = _path.substring(0,p.length()-1);
        int i = p.lastIndexOf("/");
        return i<0 ? "" : p.substring(0,i+1);
    }


    public WebdavResolver.Resource parent()
    {
        if ("/".equals(_path))
            return null;
        String parent = _path.endsWith("/") ? _path.substring(0, _path.length()-1) : _path;
        parent = parent.substring(0,parent.lastIndexOf("/")+1);
        return WebdavService.getResolver().lookup(parent);
    }


    public String getContentType()
    {
        if (isCollection())
            return "text/html";
        return PageFlowUtil.getContentTypeFor(_path);
    }


    @NotNull
    public String getHref(ViewContext context)
    {
        ActionURL url = context.getActionURL();
        int port = context.getRequest().getServerPort();
        boolean defaultPort = "http".equals(url.getScheme()) && 80 == port || "https".equals(url.getScheme()) && 443 == port;
        String portStr = defaultPort ? "" : ":" + port;
        String href = url.getScheme() + "://" + url.getHost() + portStr + context.getContextPath() + context.getRequest().getServletPath() + PageFlowUtil.encodePath(_path);
        if (isCollection() && !href.endsWith("/"))
            href += "/";
        return href;
    }


    @NotNull
    public String getLocalHref(ViewContext context)
    {
        String href = context.getContextPath() + context.getRequest().getServletPath() + PageFlowUtil.encodePath(_path);
        if (isCollection() && !href.endsWith("/"))
            href += "/";
        return href;
    }


    public String getExecuteHref(ViewContext context)
    {
        return null;
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


    protected boolean hasAccess(User user)
    {
        return true;
    }


    public boolean canList(User user)
    {
        return canRead(user);
    }


    public boolean canRead(User user)
    {
        if ("/".equals(_path))
            return true;
        return hasAccess(user) && (getPermissions(user) & ACL.PERM_READ) != 0;
    }


    public boolean canWrite(User user)
    {
        return hasAccess(user) && (getPermissions(user) & ACL.PERM_UPDATE) != 0;
    }


    public boolean canCreate(User user)
    {
        return hasAccess(user) && (getPermissions(user) & ACL.PERM_INSERT) != 0;
    }


    public boolean canDelete(User user)
    {
        return hasAccess(user) && (getPermissions(user) & ACL.PERM_UPDATE) != 0;
    }


    public boolean canRename(User user)
    {
        return hasAccess(user) && canCreate(user) && canDelete(user);
    }


    public int getPermissions(User user)
    {
        return _acl.getPermissions(user);
    }


    public boolean delete(User user) throws IOException
    {
        assert !canDelete(user);
        return false;
    }
    
    public File getFile()
    {
        return null;
    }

    public long copyFrom(User user, WebdavResolver.Resource r) throws IOException
    {
        return copyFrom(user, r.getInputStream(user));
    }


    @NotNull
    public List<WebdavResolver.History> getHistory()
    {
        return Collections.EMPTY_LIST;
    }

    @NotNull
    public List<NavTree> getActions()
    {
        return Collections.EMPTY_LIST;
    }
}
