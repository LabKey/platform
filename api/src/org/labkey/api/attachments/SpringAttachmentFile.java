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
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.vfs.FileLike;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Sep 10, 2007
 * Time: 7:30:27 PM
 */
public class SpringAttachmentFile implements AttachmentFile
{
    private static final Logger LOG = LogHelper.getLogger(SpringAttachmentFile.class, "Attachment file for uploaded files");
    private final MultipartFile _file;
    private final String _filename;

    private InputStream _in;

    public SpringAttachmentFile(MultipartFile file)
    {
        this(file, null);
    }

    public SpringAttachmentFile(MultipartFile file, @Nullable String newName)
    {
        _file = file;
        _filename = (null == newName ? _file.getOriginalFilename() : newName);

    }

    @Override
    public String getFilename()
    {
        return _filename;
    }

    @Override
    public String getContentType()
    {
        return _file.getContentType();
    }

    @Override
    public long getSize()
    {
        return _file.getSize();
    }

    @Override
    public String getError()
    {
        if (!_file.isEmpty() && 0 == getSize())
            return "Warning: " + getFilename() + " was not uploaded.  Attachments must not exceed the maximum file size of " + getMaxFileUploadSize() + ".";
        else
            return null;
    }

    private String getMaxFileUploadSize()
    {
        // For now, assume we're configured for the default maximum struts file size
        // TODO: Lame -- should query Spring to determine the configured max file size
        return "250MB";
    }

    @Override
    public InputStream openInputStream() throws IOException
    {
        if (_in != null)
            throw new IllegalStateException("An unclosed input stream is already active for this SpringAttachmentFile");

        _in = _file.getInputStream();
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
            LOG.debug("No input stream is active for this SpringAttachmentFile");
    }

    public static List<AttachmentFile> createList(Map<String, MultipartFile> fileMap)
    {
        List<AttachmentFile> files = new ArrayList<>(fileMap.size());
        ArrayList<String> keys = new ArrayList<>(fileMap.keySet());
        keys.sort(String.CASE_INSENSITIVE_ORDER);

        for (String fileKey : keys)
        {
            MultipartFile file = fileMap.get(fileKey);
            if (!file.isEmpty())
            {
                files.add(new SpringAttachmentFile(file));
            }
        }

        return files;
    }

    public String toString()
    {
        return getFilename();
    }

    public boolean isEmpty()
    {
        return _file.isEmpty();
    }

    public void saveTo(FileLike targetFile) throws IOException
    {
        InputStream is = openInputStream();
        try (OutputStream out = targetFile.openOutputStream())
        {
            FileUtil.copyData(is, out);
        }
        finally
        {
            closeInputStream();
        }
    }
}
