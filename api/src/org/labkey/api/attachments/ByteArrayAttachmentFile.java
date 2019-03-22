/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.attachments;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * In-memory cache of an attachment.
 * User: brittp
 * Date: Apr 17, 2006
 */
public class ByteArrayAttachmentFile implements AttachmentFile
{
    private final String _contentType;
    private final byte[] _content;
    private final String _fileName;

    private InputStream _inputStream;

    public ByteArrayAttachmentFile(String fileName, byte[] content, String contentType)
    {
        _contentType = contentType;
        _content = content;
        _fileName = fileName;
    }

    public String getContentType()
    {
        return _contentType;
    }

    public String getFilename()
    {
        return _fileName;
    }

    public long getSize()
    {
        return _content.length;
    }

    public InputStream openInputStream()
    {
        if (_inputStream != null)
            throw new IllegalStateException("An unclosed input stream is already active for this ByteArrayAttachmentFile");
        _inputStream = new BufferedInputStream(new ByteArrayInputStream(_content));
        return _inputStream;
    }

    public void closeInputStream() throws IOException
    {
        if (_inputStream == null)
            throw new IllegalStateException("No input stream is active for this ByteArrayAttachmentFile");
        _inputStream.close();
    }

    public String getError()
    {
        return null;
    }
}
