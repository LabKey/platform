/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.resource.AbstractResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractWebdavResource extends AbstractResource implements WebdavResource
{
    private static final String FOLDER_FONT_CLS = "fa fa-folder-o";

    private SecurableResource _resource;
    private List<ExpData> _data = null;

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

    @Override
    public boolean createCollection(User user) throws DavException
    {
        try
        {
            return this.getFile() != null && FileUtil.mkdirs(this.getFile(), AppProps.getInstance().isInvalidFilenameUploadBlocked());
        }
        catch (IOException e)
        {
            throw new DavException(e.getCause());
        }
    }

    @Override
    public WebdavResource parent()
    {
        Path p = getPath();
        if (p.getNameCount()==0)
            return null;
        Path parent = p.getParent();
        return WebdavService.get().lookup(parent);
    }

    @Override
    public WebdavResource find(Path.Part name)
    {
        return null;
    }

    @Override
    public Collection<? extends WebdavResource> list()
    {
        return Collections.emptyList();
    }

    @Override
    public long getCreated()
    {
        return getLastModified();
    }

    @Override
    public User getCreatedBy()
    {
        List<ExpData> data = getExpData();
        if (data == null || data.isEmpty())
            return null;

        return data.get(0).getCreatedBy();
    }

    @Override
    public String getDescription()
    {
        List<ExpData> data = getExpData();
        if (data == null || data.isEmpty())
            return null;

        return data.get(0).getComment();
    }

    @Override
    public User getModifiedBy()
    {
        List<ExpData> data = getExpData();
        if (data == null || data.isEmpty())
            return null;

        return data.get(0).getModifiedBy();
    }

    @Override
    public void setLastIndexed(long indexed, long modified)
    {
        SearchService ss = SearchService.get();
        if (isFile() && ss != null)
            ss.setLastIndexedForPath(getPath(), indexed, modified);
    }

    @Override
    public String getContentType()
    {
        if (isCollection())
            return "text/html";
        return PageFlowUtil.getContentTypeFor(getName());
    }

    @Override
    public String getAbsolutePath(User user)
    {
        return null;
    }

    @Override
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

    @Override
    @NotNull
    public String getLocalHref(ViewContext context)
    {
        String contextPath = null==context ? AppProps.getInstance().getContextPath() : context.getContextPath();
        String href = c(contextPath, getPath().encode());
        if (isCollection() && !href.endsWith("/"))
            href += "/";
        return href;
    }

    @Override
    public String getExecuteHref(ViewContext context)
    {
        String path = parent().getExecuteHref(context);
        path += (path.endsWith("/")?"":"/") + PageFlowUtil.encode(getPath().getName());
        return path;
    }

    @Override
    public String getIconHref()
    {
        if (isCollection())
            return AppProps.getInstance().getContextPath() + "/_icons/folder.gif";
        return AppProps.getInstance().getContextPath() + Attachment.getFileIcon(getName());
    }

    @Nullable
    @Override
    public String getIconFontCls()
    {
        if (isCollection())
            return FOLDER_FONT_CLS;
        return Attachment.getFileIconFontCls(getName());
    }

    @Nullable
    @Override
    public DirectRequest getDirectGetRequest(ViewContext context, String contentDisposition)
    {
        return null;
    }

    @Nullable
    @Override
    public DirectRequest getDirectPutRequest(ViewContext context)
    {
        return null;
    }

    @Override
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

    @Override
    public String getETag()
    {
        return getETag(false);
    }

    @Override
    public String getMD5(User user) throws IOException
    {
        return FileUtil.md5sum(getInputStream(user));
    }

    @Override
    public Map<String, ?> getProperties()
    {
        if (null == _properties)
            return Collections.emptyMap();
        Map<String, ?> ret = _properties;
        assert null != (ret = Collections.unmodifiableMap(ret));
        return ret;
    }

    @Override
    public Map<String, Object> getMutableProperties()
    {
        if (null == _properties)
            _properties = new HashMap<>();
        return _properties;
    }
    
    @Override
    public InputStream getInputStream() throws IOException
    {
        return getInputStream(null);
    }

    /** provides one place to completely block access to the resource */
    protected boolean hasAccess(User user)
    {
        return true;
    }

    protected SecurableResource getSecurableResource()
    {
        return _resource;
    }

    protected void setSecurableResource(SecurableResource resource)
    {
        _resource = resource;
    }

    /** permissions */

    @Override
    public boolean canList(User user, boolean forRead)
    {
        return canRead(user, forRead);
    }

    @Override
    public boolean canRead(User user, boolean forRead)
    {
        // TODO: This looks wrong
        if ("/".equals(getPath()))
            return true;
        try
        {
            SecurityLogger.indent(getPath() + " AbstractWebdavResource.canRead()");
            if (!hasAccess(user))
            {
                SecurityLogger.log("hasAccess()==false", user, null, false);
                return false;
            }
            return getPermissions(user).contains(ReadPermission.class);
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }

    @Override
    public boolean canWrite(User user, boolean forWrite)
    {
        Set<Role> roles = user.equals(getCreatedBy()) ? RoleManager.roleSet(OwnerRole.class) : Set.of();
        return hasAccess(user) && !user.isGuest() &&
                SecurityManager.hasAllPermissions(null, getSecurableResource(), user, Set.of(UpdatePermission.class), roles);
    }

    @Override
    public boolean canCreate(User user, boolean forCreate)
    {
        return hasAccess(user) && !user.isGuest() &&
                SecurityManager.hasAllPermissions(null, getSecurableResource(), user, Set.of(InsertPermission.class), Set.of());
    }

    @Override
    public boolean canCreateCollection(User user, boolean forCreate)
    {
        return canCreate(user, forCreate);
    }

    @Override
    public boolean canDelete(User user, boolean forDelete)
    {
        return canDelete(user, forDelete, null);
    }

    @Override
    public boolean canDelete(User user, boolean forDelete, /* OUT */ @Nullable List<String> message)
    {
        if (user.isGuest() || !hasAccess(user))
            return false;
        Set<Class<? extends Permission>> perms = getPermissions(user);
        return perms.contains(UpdatePermission.class) || perms.contains(DeletePermission.class);
    }

    @Override
    public boolean canRename(User user, boolean forRename)
    {
        return hasAccess(user) && !user.isGuest() && canCreate(user, forRename) && canDelete(user, forRename, null);
    }

    public Set<Class<? extends Permission>> getPermissions(User user)
    {
        return SecurityManager.getPermissions(getSecurableResource(), user, Set.of());
    }

    @Override
    public boolean delete(User user) throws IOException
    {
        assert null == user || canDelete(user, true, null);
        return false;
    }
    
    @Override
    public File getFile()
    {
        return null;
    }

    @Override
    public long copyFrom(User user, WebdavResource r) throws IOException, DavException
    {
        return copyFrom(user, r.getFileStream(user));
    }

    @Override
    public void moveFrom(User user, WebdavResource src) throws IOException, DavException
    {
        copyFrom(user, src);
        src.delete(user);
    }

    @Override
    @NotNull
    public Collection<WebdavResolver.History> getHistory()
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Collection<NavTree> getActions(User user)
    {
        return Collections.emptyList();
    }

    protected Collection<NavTree> getActionsHelper(User user, List<ExpData> expDatas)
    {
        List<NavTree> result = new ArrayList<>();
        Set<Integer> runIDs = new HashSet<>();

        for (ExpData data : expDatas)
        {
            if (data == null || !data.getContainer().hasPermission(user, ReadPermission.class))
                continue;

            ActionURL dataURL = data.findDataHandler().getContentURL(data);
            List<? extends ExpRun> runs = ExperimentService.get().getRunsUsingDatas(Collections.singletonList(data));

            for (ExpRun run : runs)
            {
                if (!run.getContainer().hasPermission(user, ReadPermission.class))
                    continue;
                if (!runIDs.add(run.getRowId()))
                    continue;

                ActionURL runURL = dataURL == null ? LsidManager.get().getDisplayURL(run.getLSID()) : dataURL;
                String actionName;

                if (!run.getName().equals(data.getName()))
                {
                    actionName = run.getName() + " (" + run.getProtocol().getName() + ")";
                }
                else
                {
                    actionName = run.getProtocol().getName();
                }

                result.add(new NavTree(actionName, runURL));
            }
        }
        return result;
    }

    public static String c(String path, String... names)
    {
        StringBuilder s = new StringBuilder();
        s.append(StringUtils.stripEnd(path,"/"));
        for (String name : names)
        {
            String bare = StringUtils.strip(name, "/");
            if (!bare.isEmpty())
                s.append("/").append(bare);
        }
        return s.toString();
    }

    @Override
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

        @Override
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

        @Override
        public InputStream openInputStream() throws IOException
        {
            if (null == _is)
                _is = getInputStream(_user);
            return _is;
        }

        @Override
        public void closeInputStream()
        {
            IOUtils.closeQuietly(_is);
        }
    }

    public void createLink(String name, Path target, @Nullable String indexPage)
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
    @Override
    public String getDocumentId()
    {
        if (null == parent())
            return "dav:" + getPath();
        StringBuilder docid = new StringBuilder(parent().getDocumentId());
        if (docid.charAt(docid.length()-1)!='/')
            docid.append("/");
        docid.append(getName());
        if (isCollection())
            docid.append('/');
        return docid.toString();
    }

    @Override
    public String getContainerId()
    {
        return _containerId;
    }

    @Override
    public boolean shouldIndex()
    {
        // TODO would be nice to call DavController.isTempFile()
        String name = getName();
        if (name.startsWith(".part")) // applet uploader temp files
            return false;
        if (name.startsWith("._")) // mac finder temporary files
            return false;
        if (name.equals(".DS_Store")) // mac
            return false;
        if (name.startsWith("~") || name.startsWith(".~"))  // Office working files
            return false;
        return true;
    }

    @Override
    public Map<String, String> getCustomProperties(User user)
    {
        return Collections.emptyMap();
    }

    @Override
    public void notify(ContainerUser context, String message)
    {
    }

    protected void addAuditEvent(ContainerUser context, String message)
    {
        String dir;
        String name;
        File f = getFile();
        if (f != null)
        {
            dir = f.getParent();
            name = f.getName();
        }
        else
        {
            Resource parent = parent();
            dir = parent == null ? "" : parent.getPath().toString();
            name = getName();
        }

        Container c = context.getContainer();

        // translate the actions into a more meaningful message
        if ("created".equalsIgnoreCase(message))
        {
            message = "File uploaded to " + c.getContainerNoun() + ": " + c.getPath();
        }
        else if ("deleted".equalsIgnoreCase(message))
        {
            message = "File deleted from " + c.getContainerNoun() + ": " + c.getPath();
        }
        else if ("replaced".equalsIgnoreCase(message))
        {
            String path = ("/".equals(c.getPath())) ? c.getPath() : this.getPath().toString();
            message = "File replaced in " + c.getContainerNoun() + ": " + path;
        }
        else if ("fileDeleteFailed".equalsIgnoreCase(message))
        {
            message = "File delete failed from " + c.getContainerNoun() + ": " + c.getPath();
        }
        else if ("dirDeleteFailed".equalsIgnoreCase(message))
        {
            message = "Directory delete failed from " + c.getContainerNoun() + ": " + c.getPath();
        }

//        String subject = "File Management Tool notification: " + message;

        FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(c.getId(), message);

        event.setDirectory(dir);
        event.setFile(name);
        event.setResourcePath(getPath().toString());

        AuditLogService.get().addEvent(context.getUser(), event);
    }

    protected void setProperty(String key, String value)
    {
        if (_properties == null)
            _properties = new HashMap<>();
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

    protected List<ExpData> getExpData()
    {
        if (null == _data)
        {
            java.nio.file.Path file = getNioPath();
            return getExpDatasHelper(file, getContainer());
        }
        return _data;
    }

    @NotNull
    protected List<ExpData> getExpDatasHelper(@Nullable java.nio.file.Path path, Container container)
    {
        if (null == _data)
        {
            List<ExpData> list = new LinkedList<>();

            if (null != path)
            {
                for (WebdavResourceExpDataProvider provider : WebdavService.get().getExpDataProviders())
                {
                    list.addAll(provider.getExpDataByPath(path, container));
                }
            }

            //Sort the results by creation date so the original is used for metadata display
            _data = list.stream().sorted(Comparator.comparing(ExpObject::getCreated)).toList();
        }
        return _data;
    }

    Container getContainer()
    {
        String id = getContainerId();
        if (null == id)
            return null;
        return ContainerManager.getForId(id);
    }

    @Override
    public void setLastModified(long time) throws IOException
    {
        // No-op
    }
}
