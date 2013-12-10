/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;

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
    private final String _filename;
    private final byte[] _bytes;
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
            // TODO: Check for ByteArrayInputStream and short-circuit
            _bytes = IOUtils.toByteArray(is);
            _is = new ByteArrayInputStream(_bytes);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            try { is.close(); } catch (IOException ignored) {}
        }
    }
    
    @Override
    public long getSize() throws IOException
    {
        return _bytes.length;
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
