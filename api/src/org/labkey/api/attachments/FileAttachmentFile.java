/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: adam
 * Date: Sep 11, 2007
 * Time: 9:23:50 AM
 */
public class FileAttachmentFile implements AttachmentFile
{
    private final File _file;
    private InputStream _in;
    private String _filename;

    public FileAttachmentFile(File file)
    {
        _file = file;
        _filename = file.getName();
    }

    public byte[] getBytes() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    public String getContentType()
    {
        return PageFlowUtil.getContentTypeFor(getFilename());
    }

    public String getError()
    {
        if (!_file.exists())
            return "Warning: " + getFilename() + " does not exist.";
        else
            return null;
    }

    public String getFilename()
    {
        return _filename;
    }

    public void setFilename(String filename)
    {
        _filename = filename;
    }

    public InputStream openInputStream() throws IOException
    {
        _in = new FileInputStream(_file);
        return _in;
    }

    public void closeInputStream() throws IOException
    {
        if (null != _in)
            _in.close();
    }

    public long getSize()
    {
        return _file.length();
    }
}
