/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;

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
    private static final Logger LOG = LogHelper.getLogger(FileAttachmentFile.class, "Attachment file for file system files");
    private final File _file;
    private final String _filename;

    private InputStream _in;

    public FileAttachmentFile(File file)
    {
        this(file, null);
    }

    public FileAttachmentFile(File file, @Nullable String originalName)
    {
        _file = file;
        _filename = null != originalName ? originalName : file.getName();
    }

    @Override
    public String getContentType()
    {
        return PageFlowUtil.getContentTypeFor(getFilename());
    }

    @Override
    public String getError()
    {
        if (!_file.exists())
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
    public InputStream openInputStream() throws IOException
    {
        if (_in != null)
            throw new IllegalStateException("An unclosed input stream is already active for this FileAttachmentFile");

        _in = new FileInputStream(_file);
        return _in;
    }

    @Override
    public void closeInputStream() throws IOException
    {
        if (_in != null)
        {
            _in.close();
            _in = null;
        }
        else
            LOG.debug("No input stream is active for this FileAttachmentFile");
    }

    @Override
    public long getSize()
    {
        return _file.length();
    }
}
