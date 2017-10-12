/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
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

    // isCollection() only returns true when resource exists
    // return true to indicate that this resource may _only_ be a collection (whether it exists or not)
    boolean isCollectionType();

    // TODO move more functionality into interface and remove this method
    @Nullable File getFile();

    Collection<? extends WebdavResource> list();

    long getCreated();

    User getCreatedBy();

    String getDescription();

    User getModifiedBy();

    void setLastIndexed(long msLastIndexed, long msModified);

    String getContentType();

    // search service properties
    Map<String,?> getProperties();

    /** should only be called by creator of Resource (may not be thread-safe) */
    Map<String,Object> getMutableProperties();

    /** Caller needs to check permissions */
    @Nullable
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

    String getAbsolutePath(User user);

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

    @Nullable
    String getIconFontCls();

    /**
     * Get the request used for sending a direct GET request for this resource which
     * may include additional http headers.
     *
     * If null, the {@link #getLocalHref(ViewContext)} is sufficient for getting this resource.
     */
    @Nullable
    DirectRequest getDirectGetRequest(ViewContext context, String contentDisposition);

    /**
     * Get the request used for sending a direct PUT request for this resource which
     * may include additional http headers.
     *
     * If null, the {@link #getLocalHref(ViewContext)} is sufficient for putting this resource.
     */
    @Nullable
    DirectRequest getDirectPutRequest(ViewContext context);

    // static resources may cache the etag, force to check file system
    String getETag(boolean force);

    String getETag();

    /**
     * Calculate MD5sum of this resource.  The default implementation will calculate
     * the MD5sum by reading the reasource InputStream.
     */
    String getMD5(User user) throws IOException;

    @NotNull
    Collection<WebdavResolver.History> getHistory();

    @NotNull
    Collection<NavTree> getActions(User user);

    /**
     * @param user authenticated user
     * @param forRead  true if user wants to read, false if checking capabilities
     * @return true if the user has permission and server has capability
     */
    boolean canList(User user, boolean forRead);

    /**
     * @param user authenticated user
     * @param forRead  true if user wants to read, false if checking capabilities
     * @return true if the user has permission and server has capability
     */
    boolean canRead(User user, boolean forRead);

    /**
     * @param user authenticated user
     * @param forWrite  true if user wants to delete, false if checking capabilities
     * @return true if the user has permission and server has capability
     */
    boolean canWrite(User user, boolean forWrite);
    /**
     * @param user authenticated user
     * @param forCreate  true if user wants to create, false if checking capabilities (affects logging)
     * @return true if the user has permission and server has capability
     */
    boolean canCreate(User user, boolean forCreate);
    /**
     * @param user authenticated user
     * @param forCreate  true if user wants to create, false if checking capabilities (affects logging)
     * @return true if the user has permission and server has capability
     */
    boolean canCreateCollection(User user, boolean forCreate); // only on collection can create sub collection
    /**
     * @param user authenticated user
     * @param forDelete  true if user wants to delete, false if checking capabilities (affects logging)
     * @return true if the user has permission and server has capability
     */
    boolean canDelete(User user, boolean forDelete);
    boolean canDelete(User user, boolean forDelete, /* OUT */ List<String> message);
    /**
     * @param user authenticated user
     * @param forRename  true if user wants to rename, false if checking capabilities (affects logging)
     * @return true if the user has permission and server has capability
     */
    boolean canRename(User user, boolean forRename);

    // dav methods
    boolean delete(User user) throws IOException;

    boolean createCollection(User user);

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
     * For a collection, this indicates whether this resource should be scanned for children to index
     * For a file, this indicates whether this individual object should be skipped
     *
     * Note there are other checks that can cause an object to be skipped.
     * see LuceneSearchService.accept()
     *
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
