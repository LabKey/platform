/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.systemsbiology.jrap.ByteAppender;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: klum
 * Date: Sep 27, 2011
 * Time: 3:14:03 PM
 */
public class InputStreamAttachmentFile implements AttachmentFile
{
    private final InputStream _is;
    private String _filename;      // TODO: Make final
    private final ByteAppender _byteBuffer = new ByteAppender();
    private final String _contentType;     // Null means infer from filename

    public InputStreamAttachmentFile(InputStream is, String filename)
    {
        this(is, filename, null);
    }

    public InputStreamAttachmentFile(InputStream is, String filename, @Nullable String contentType)
    {
        _filename = filename;
        _contentType = contentType;

        try
        {
            byte[] buffer = new byte[4096];
            int len;

            while ((len = is.read(buffer)) > 0)
                _byteBuffer.append(buffer, 0, len);

            _is = new ByteArrayInputStream(_byteBuffer.getBuffer());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public long getSize() throws IOException
    {
        return _byteBuffer.getCount();
    }

    @Override
    public String getError()
    {
        if (_is == null)
            return "Warning: " + getFilename() + " does not exist.";
        else
            return null;
    }

    @Override
    public String getFilename()
    {
        return _filename;
    }

    @Override
    public void setFilename(String filename)
    {
        _filename = filename;
    }

    @Override
    public String getContentType()
    {
        if (null == _contentType)
            return PageFlowUtil.getContentTypeFor(getFilename());
        else
            return _contentType;
    }

    @Override
    public InputStream openInputStream() throws IOException
    {
        return _is;
    }

    @Override
    public void closeInputStream() throws IOException
    {
        if (null != _is)
            _is.close();
    }
}
