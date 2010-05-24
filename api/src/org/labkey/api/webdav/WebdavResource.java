/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileStream;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 11:42:10 AM
 */
public interface WebdavResource extends Resource
{

    WebdavResource find(String name);

    // TODO move more functionality into interface and remove this method
    File getFile();

    Collection<? extends WebdavResource> list();

    long getCreated();

    User getCreatedBy();

    String getDescription();

    User getModifiedBy();

    // may return Long.MIN_VALUE
    long getLastIndexed();

    void setLastIndexed(long msLastIndexed, long msModified);

    String getContentType();

    Map<String,?> getProperties();

    /** should only be called by creator of Resource (may not be thread-safe) */
    Map<String,Object> getMutableProperties();

    /** Caller needs to check permissions */
    FileStream getFileStream(User user) throws IOException;

    /** Caller needs to check permissions */
    @Nullable
    InputStream getInputStream(User user) throws IOException;

    /** Caller needs to check permissions */
    long copyFrom(User user, FileStream in) throws IOException;

    /** Caller needs to check permissions */
    long copyFrom(User user, WebdavResource r) throws IOException;

    /** Caller needs to check permissions */
    void moveFrom(User user, WebdavResource r) throws IOException;

    long getContentLength() throws IOException;

    @NotNull
    String getHref(ViewContext context);

    @NotNull
    String getLocalHref(ViewContext context);

    /**
     * for files should return the location of the rendered version of this file
     * may be same as getLocalHref().
     *
     * For collections, it may be a prefix for child nodes, to use.  May or may
     * not be a valid href.
     */
    @Nullable
    String getExecuteHref(ViewContext context);

    @Nullable
    String getIconHref();

    String getETag();

    @NotNull
    Collection<WebdavResolver.History> getHistory();

    @NotNull
    Collection<NavTree> getActions(User user);

    /** user may read properties of this resource */
    boolean canList(User user);

    /** user may read file stream of this resource */
    boolean canRead(User user);

    boolean canWrite(User user);
    boolean canCreate(User user);
    boolean canCreateCollection(User user); // only on collection can create sub collection
    boolean canDelete(User user);
    boolean canRename(User user);

    // dav methods
    boolean delete(User user) throws IOException;

    //
    // for SearchService
    //

    /**
     * unique name for full text index purposes there should be SearchService.ResourceResolver that can resolve this documentid
     *
     * This name should be server unique, and as far as possible not reusable (e.g. non-reused rowid and entityids are
     * better than names that may be reused.
     */
    String getDocumentId();

    /**
     * required for fast permission filtering by SearchService, only non-indexable resources may return null
     */
    String getContainerId();

    /**
     * return false to skip this collection/resource and children
     * TODO: is this always recursive????
     * @return
     */
    boolean shouldIndex();

    /**
     * Returns custom (user configured) properties for this resource
     * @return
     */
    Map<String, String> getCustomProperties(User user);

    void notify(ContainerUser context, String message);
}
