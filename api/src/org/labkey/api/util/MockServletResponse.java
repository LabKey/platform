/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
package org.labkey.api.util;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: matthewb
 * Date: 2011-09-01
 * Time: 11:25 AM
 */
class MockServletResponse implements HttpServletResponse
{
    Map<String,String> headers = new TreeMap<>();
    int status = 0;
    String message = null;
    String redirect = null;
    String contentType = null;
    String characterEncoding = null;
    int contentLength = 0;
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    Locale locale = null;

    PrintWriter printWriter = new PrintWriter(os);
    ServletOutputStream servletOutputStream = new ServletOutputStream()
    {
        @Override
        public void write(int i) throws IOException
        {
            os.write(i);
        }
    };


    @Override
    public void addCookie(Cookie cookie)
    {
    }

    @Override
    public boolean containsHeader(String s)
    {
        return headers.containsKey(s);
    }

    @Override
    public String encodeURL(String s)
    {
        throw new IllegalStateException();
    }

    @Override
    public String encodeRedirectURL(String s)
    {
        throw new IllegalStateException();
    }

    @Override
    public String encodeUrl(String s)
    {
        throw new IllegalStateException();
    }

    @Override
    public String encodeRedirectUrl(String s)
    {
        throw new IllegalStateException();
    }

    @Override
    public void sendError(int i, String s) throws IOException
    {
        status = i;
        message = s;
    }

    @Override
    public void sendError(int i) throws IOException
    {
        status = i;
    }

    @Override
    public void sendRedirect(String s) throws IOException
    {
        redirect = s;
    }

    @Override
    public void setDateHeader(String s, long l)
    {
        headers.put(s, DateUtil.toISO(l));
    }

    @Override
    public void addDateHeader(String s, long l)
    {
        headers.put(s, DateUtil.toISO(l));
    }

    @Override
    public void setHeader(String s, String s1)
    {
        headers.put(s,s1);
    }

    @Override
    public void addHeader(String s, String s1)
    {
        headers.put(s,s1);
    }

    @Override
    public void setIntHeader(String s, int i)
    {
        headers.put(s,String.valueOf(i));
    }

    @Override
    public void addIntHeader(String s, int i)
    {
        headers.put(s,String.valueOf(i));
    }

//  This will be required when we upgrade servlet-api to a more modern version
//    @Override
//    public String getHeader(String s)
//    {
//        return headers.get(s);
//    }
//
//    @Override
//    public Collection<String> getHeaders(String s)
//    {
//        return Collections.singleton(getHeader(s));
//    }
//
//    @Override
//    public Collection<String> getHeaderNames()
//    {
//        return headers.keySet();
//    }
//
    @Override
    public void setStatus(int i)
    {
        status = i;
    }

    @Override
    public void setStatus(int i, String s)
    {
        status = i;
        message = s;
    }

//    @Override
//    public int getStatus()
//    {
//        return status;
//    }
//
    @Override
    public String getCharacterEncoding()
    {
        return characterEncoding;
    }

    @Override
    public String getContentType()
    {
        return contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException
    {
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException
    {
        return printWriter;
    }

    @Override
    public void setCharacterEncoding(String s)
    {
        characterEncoding = s;
    }

    @Override
    public void setContentLength(int i)
    {
        contentLength = i;
    }

    @Override
    public void setContentType(String s)
    {
        contentType = s;
    }

    @Override
    public void setBufferSize(int i)
    {
    }

    @Override
    public int getBufferSize()
    {
        return 0;
    }

    @Override
    public void flushBuffer() throws IOException
    {
    }

    @Override
    public void resetBuffer()
    {
        printWriter.flush();
        os.reset();
    }

    @Override
    public boolean isCommitted()
    {
        return false;
    }

    @Override
    public void reset()
    {
        resetBuffer();
        status = 0;
        message = null;
//        headers.clear();
    }

    @Override
    public void setLocale(Locale locale)
    {
        this.locale = locale;
    }

    @Override
    public Locale getLocale()
    {
        return locale;
    }

    public String getBodyAsText()
    {
        printWriter.flush();
        return new String(os.toByteArray(), 0, os.size());
    }
}
