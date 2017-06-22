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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public SimpleDocumentResource(Path path, String documentId, String containerId, String contentType, @Nullable String body, URLHelper executeUrl, @Nullable Map<String, Object> properties)
    {
        super(path);
        _containerId = containerId;
        _documentId = documentId;
        _contentType = contentType;
        _body = null == body ? new byte[0] : body.getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
        _executeUrl = executeUrl;
        assert !(_executeUrl instanceof ActionURL) || ((ActionURL)executeUrl).getExtraPath().equals(_containerId);
        if (null != properties)
            _properties = new HashMap<>(properties);
    }

    public SimpleDocumentResource(Path path, String documentId, String contentType, @Nullable String body, ActionURL executeUrl, @Nullable Map<String, Object> properties)
    {
        this(path, documentId, executeUrl.getExtraPath(), contentType, body, executeUrl, properties);
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

    public boolean exists()
    {
        return true;
    }

    public InputStream getInputStream(User user) throws IOException
    {
        return new ByteArrayInputStream(_body);
    }

    public long copyFrom(User user, FileStream in) throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public long getContentLength() throws IOException
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
        return _executeUrl.getLocalURIString();
    }
}
