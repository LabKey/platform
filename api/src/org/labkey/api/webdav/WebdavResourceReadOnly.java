/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by matthew on 10/23/15.
 */
public class WebdavResourceReadOnly implements WebdavResource
{
    final private WebdavResource _delegate;
    private WebdavResource getDelegate()
    {
        return _delegate;
    }

    WebdavResourceReadOnly(WebdavResource d)
    {
        _delegate = d;
    }

    @Override
    public WebdavResource find(String name)
    {
        WebdavResource wr = getDelegate().find(name);
        if (null == wr)
            return null;
        return new WebdavResourceReadOnly(wr);
    }

    @Override
    public boolean isCollectionType()
    {
        return getDelegate().isCollectionType();
    }

    @Override
    @Nullable
    public File getFile()
    {
        return getDelegate().getFile();
    }

    @Override
    public Collection<? extends WebdavResource> list()
    {
        return getDelegate().list();
    }

    @Override
    public long getCreated()
    {
        return getDelegate().getCreated();
    }

    @Override
    public User getCreatedBy()
    {
        return getDelegate().getCreatedBy();
    }

    @Override
    public String getDescription()
    {
        return getDelegate().getDescription();
    }

    @Override
    public User getModifiedBy()
    {
        return getDelegate().getModifiedBy();
    }

    @Override
    public void setLastIndexed(long msLastIndexed, long msModified)
    {
        getDelegate().setLastIndexed(msLastIndexed, msModified);
    }

    @Override
    public String getContentType()
    {
        return getDelegate().getContentType();
    }

    @Override
    public Map<String, ?> getProperties()
    {
        return getDelegate().getProperties();
    }

    @Override
    public Map<String, Object> getMutableProperties()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    public FileStream getFileStream(User user) throws IOException
    {
        return getDelegate().getFileStream(user);
    }

    @Override
    @Nullable
    public InputStream getInputStream(User user) throws IOException
    {
        return getDelegate().getInputStream(user);
    }

    @Override
    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long copyFrom(User user, WebdavResource r) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveFrom(User user, WebdavResource r) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getContentLength() throws IOException
    {
        return getDelegate().getContentLength();
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
        return getDelegate().getHref(context);
    }

    @Override
    @NotNull
    public String getLocalHref(ViewContext context)
    {
        return getDelegate().getLocalHref(context);
    }

    @Override
    @Nullable
    public String getExecuteHref(ViewContext context)
    {
        return getDelegate().getExecuteHref(context);
    }

    @Override
    @Nullable
    public String getIconHref()
    {
        return getDelegate().getIconHref();
    }

    @Override
    @Nullable
    public String getIconFontCls()
    {
        return getDelegate().getIconFontCls();
    }

    @Override
    @Nullable
    public DirectRequest getDirectGetRequest(ViewContext context, String contentDisposition)
    {
        return getDelegate().getDirectGetRequest(context, contentDisposition);
    }

    @Override
    @Nullable
    public DirectRequest getDirectPutRequest(ViewContext context)
    {
        return getDelegate().getDirectPutRequest(context);
    }

    @Override
    public String getETag(boolean force)
    {
        return getDelegate().getETag(force);
    }

    @Override
    public String getETag()
    {
        return getDelegate().getETag();
    }

    @Override
    public String getMD5(User user) throws IOException
    {
        return getDelegate().getMD5(user);
    }

    @Override
    @NotNull
    public Collection<WebdavResolver.History> getHistory()
    {
        return getDelegate().getHistory();
    }

    @Override
    @NotNull
    public Collection<NavTree> getActions(User user)
    {
        return Collections.emptyList();
    }

    @Override
    public boolean canList(User user, boolean forRead)
    {
        return false;
    }

    @Override
    public boolean canRead(User user, boolean forRead)
    {
        return getDelegate().canRead(user, forRead);
    }

    @Override
    public boolean canWrite(User user, boolean forWrite)
    {
        return false;
    }

    @Override
    public boolean canCreate(User user, boolean forCreate)
    {
        return false;
    }

    @Override
    public boolean canCreateCollection(User user, boolean forCreate)
    {
        return false;
    }

    @Override
    public boolean canDelete(User user, boolean forDelete)
    {
        return false;
    }

    @Override
    public boolean canDelete(User user, boolean forDelete, List<String> message)
    {
        return false;
    }

    @Override
    public boolean canRename(User user, boolean forRename)
    {
        return false;
    }

    @Override
    public boolean delete(User user) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean createCollection(User user)
    {
        return false;
    }

    @Override
    public String getDocumentId()
    {
        return getDelegate().getDocumentId();
    }

    @Override
    public String getContainerId()
    {
        return getDelegate().getContainerId();
    }

    @Override
    public boolean shouldIndex()
    {
        return getDelegate().shouldIndex();
    }

    @Override
    public Map<String, String> getCustomProperties(User user)
    {
        return getDelegate().getCustomProperties(user);
    }

    @Override
    public void notify(ContainerUser context, String message)
    {
        getDelegate().notify(context, message);
    }

    @Override
    public Resolver getResolver()
    {
        return getDelegate().getResolver();
    }

    @Override
    public Path getPath()
    {
        return getDelegate().getPath();
    }

    @Override
    public String getName()
    {
        return getDelegate().getName();
    }

    @Override
    public boolean exists()
    {
        return getDelegate().exists();
    }

    @Override
    public boolean isCollection()
    {
        return getDelegate().isCollection();
    }

    @Override
    public boolean isFile()
    {
        return getDelegate().isFile();
    }

    @Override
    public Collection<String> listNames()
    {
        return getDelegate().listNames();
    }

    @Override
    public Resource parent()
    {
        return getDelegate().parent();
    }

    @Override
    public long getVersionStamp()
    {
        return getDelegate().getVersionStamp();
    }

    @Override
    public long getLastModified()
    {
        return getDelegate().getLastModified();
    }

    @Override
    @Nullable
    public InputStream getInputStream() throws IOException
    {
        return getDelegate().getInputStream();
    }

    @Override
    public String toString()
    {
        return getDelegate().toString() + ": Read-only";
    }
}
