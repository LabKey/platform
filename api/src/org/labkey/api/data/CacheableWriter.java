/*
 * Copyright (c) 2005-2011 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.labkey.api.attachments.DocumentWriter;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * User: jeckels
 * Date: Nov 18, 2005
 */
public class CacheableWriter implements DocumentWriter
{
    public static final CacheableWriter noDocument = new CacheableWriter() {
        @Override
        public String toString()
        {
            return "No Document";
        }
    };

    private String _contentType;
    private String _disposition;
    private int _size;
    private ByteArrayOutputStream _bOut = new ByteArrayOutputStream();
    private byte[] _bytes;

    public CacheableWriter()
    {
    }

    public CacheableWriter(String contentType, InputStream is) throws IOException
    {
        this(contentType, IOUtils.toByteArray(is));
    }

    public CacheableWriter(String contentType, byte[] bytes)
    {
        _bytes = bytes;
        _size = _bytes.length;
        _contentType = contentType;
    }

    public void setContentType(String contentType)
    {
        _contentType = contentType;
    }

    public void setContentDisposition(String value)
    {
        _disposition = value;
    }

    public void setContentLength(int size)
    {
        _size = size;
    }

    public OutputStream getOutputStream() throws IOException
    {
        return _bOut;
    }

    public byte[] getBytes()
    {
        if (_bytes == null)
        {
            _bytes = _bOut.toByteArray();
        }

        return _bytes;
    }

    public void writeToResponse(HttpServletResponse response, Calendar expires) throws IOException
    {
        response.reset();
        response.setContentType(_contentType);
        if (expires != null)
        {
            String expiration = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT")).format(expires);
            response.setHeader("Expires", expiration);
        }
        if (_disposition != null)
        {
            response.setHeader("Content-Disposition", _disposition);
        }
        response.setContentLength(_size);
        response.getOutputStream().write(getBytes());
    }
}
