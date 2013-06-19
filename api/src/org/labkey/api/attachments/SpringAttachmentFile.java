/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Sep 10, 2007
 * Time: 7:30:27 PM
 */
public class SpringAttachmentFile implements AttachmentFile
{
    private MultipartFile _file;
    private InputStream _in;
    private String _filename = null;

    public SpringAttachmentFile(MultipartFile file)
    {
        _file = file;
    }

    public String getFilename()
    {
        return null != _filename ? _filename : _file.getOriginalFilename();
    }

    public void setFilename(String filename)
    {
        _filename = filename;
    }

    public String getContentType()
    {
        return _file.getContentType();
    }

    public long getSize()
    {
        return _file.getSize();
    }

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

    public InputStream openInputStream() throws IOException
    {
        _in = _file.getInputStream();
        return _in;
    }

    public void closeInputStream() throws IOException
    {
        if (null != _in)
            _in.close();
    }

    public static List<AttachmentFile> createList(Map<String, MultipartFile> fileMap)
    {
        List<AttachmentFile> files = new ArrayList<>(fileMap.size());
        ArrayList<String> keys = new ArrayList<>(fileMap.keySet());
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);

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
}
