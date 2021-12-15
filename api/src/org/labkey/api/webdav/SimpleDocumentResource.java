/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: matthewb
 * Date: Nov 25, 2009
 * Time: 5:31:23 PM
 */
public class SimpleDocumentResource extends AbstractDocumentResource
{
    private final String _documentId;
    private final String _contentType;
    private final byte[] _body;
    private final URLHelper _executeUrl;
    private final User _createdBy;
    private final User _modifiedBy;
    private final long _created;
    private final long _modified;

    public SimpleDocumentResource(Path path, String documentId, String containerId, String contentType, @Nullable String body, URLHelper executeUrl, @Nullable Map<String, Object> properties)
    {
        this(path, documentId, containerId, contentType, body, executeUrl, null, null, null, null, properties);
    }

    public SimpleDocumentResource(Path path, String documentId, String containerId, String contentType, @Nullable String body, URLHelper executeUrl,
                                  @Nullable User createdBy, @Nullable Date created,
                                  @Nullable User modifiedBy, @Nullable Date modified,
                                  @Nullable Map<String, Object> properties)
    {
        super(path);
        _containerId = containerId;
        _documentId = documentId;
        _contentType = contentType;
        _body = null == body ? new byte[0] : body.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
        _executeUrl = executeUrl;
        _createdBy = createdBy;
        _created = toTime("created", created, properties);
        _modifiedBy = modifiedBy;
        _modified = toTime("modified", modified, properties);
        // Make sure the "execute URL" container is supplied as a GUID, not a path, since paths are not stable. Also,
        // the GUID should match either the container ID or the resource ID (if present).
        assert !(_executeUrl instanceof ActionURL) || ((ActionURL)_executeUrl).getExtraPath().equals(_containerId) || (null != properties && ((ActionURL)_executeUrl).getExtraPath().equals(properties.get(SearchService.PROPERTY.securableResourceId.toString())));
        if (null != properties)
            _properties = new HashMap<>(properties);
    }

    public SimpleDocumentResource(Path path, String documentId, String contentType, @Nullable String body, ActionURL executeUrl, @Nullable Map<String, Object> properties)
    {
        this(path, documentId, executeUrl.getExtraPath(), contentType, body, executeUrl, properties);
    }

    private static long toTime(String fieldName, @Nullable Date d, @Nullable Map<String, Object> properties)
    {
        if (d != null)
            return d.getTime();

        if (properties != null)
        {
            Object o = properties.get(fieldName);
            if (o instanceof Date)
                return ((Date)o).getTime();

            if (o instanceof Number)
                return ((Number)o).longValue();
        }

        return Long.MIN_VALUE;
    }

    @Override
    public void setLastIndexed(long ms, long modified)
    {
        //UNDONE
    }

    @Override
    public String getContentType()
    {
        return _contentType == null ? super.getContentType() : _contentType;
    }

    @Override
    public boolean exists()
    {
        return true;
    }

    @Override
    public InputStream getInputStream(User user)
    {
        return new ByteArrayInputStream(_body);
    }

    @Override
    public long copyFrom(User user, FileStream in)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getContentLength()
    {
        return _body.length;
    }

    @Override
    public String getDocumentId()
    {
        return _documentId;
    }

    @Override
    public String getExecuteHref(ViewContext context)
    {
        return null == _executeUrl ? null : _executeUrl.getLocalURIString();
    }

    @Override
    public long getCreated()
    {
        return _created;
    }

    @Override
    public User getCreatedBy()
    {
        return _createdBy;
    }

    @Override
    public long getLastModified()
    {
        return _modified;
    }

    @Override
    public User getModifiedBy()
    {
        return _modifiedBy;
    }
}
