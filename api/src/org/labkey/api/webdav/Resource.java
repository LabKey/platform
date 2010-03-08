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

import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 11:42:10 AM
 */
public interface Resource
{
    Path getPath();

    String getName();

    boolean exists();

    boolean isCollection();

    Resource find(String name);

    // should really be 'isResource()'
    boolean isFile();

    // TODO move more functionality into interface and remove this method
    File getFile();

    List<String> listNames();

    List<Resource> list();

    Resource parent();

    long getCreated();

    User getCreatedBy();

    String getDescription();

    long getLastModified();

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
    InputStream getInputStream(User user) throws IOException;

    /** Caller needs to check permissions */
    long copyFrom(User user, FileStream in) throws IOException;

    /** Caller needs to check permissions */
    long copyFrom(User user, Resource r) throws IOException;

    /** Caller needs to check permissions */
    void moveFrom(User user, Resource r) throws IOException;

    long getContentLength() throws IOException;

    @NotNull
    String getHref(ViewContext context);

    @NotNull
    String getLocalHref(ViewContext context);

    @Nullable
    String getExecuteHref(ViewContext context);

    @Nullable
    String getIconHref();

    String getETag();

    @NotNull
    List<WebdavResolver.History> getHistory();

    @NotNull
    List<NavTree> getActions(User user);

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
}
