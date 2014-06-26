/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.action.ApiUsageException;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

/**
 * A mock version of a response that holds a reference to a "real" response. This is useful because it
 * lets us buffer our response but also send content to the client as a ping to make sure that it's still connected.
 * User: jeckels
 * Date: 3/21/13
 */
public class MockHttpResponseWithRealPassthrough extends MockHttpServletResponse
{
    /** Limit buffer size to 128MB. That's more HTML than we really want to be returning as part of an AJAX request anyway */
    public static final int MAX_BUFFER_SIZE = 128 * 1024 * 1024;
    private final HttpServletResponse _response;
    private PrintWriter _writer;

    public MockHttpResponseWithRealPassthrough(HttpServletResponse response)
    {
        _response = response;
        // We use the response's internal buffer to keep track of the size of the response. That lets us simply call
        // isCommitted() to see if it's been exceeded, instead of having to track the number of bytes ourselves.
        setBufferSize(MAX_BUFFER_SIZE);
    }

    public HttpServletResponse getResponse()
    {
        return _response;
    }

    @Override
    public PrintWriter getWriter() throws UnsupportedEncodingException
    {
        if (_writer == null)
        {
            _writer = new SizeLimitingPrintWriter(getUnderlyingWriter());
        }
        return _writer;
    }

    protected PrintWriter getUnderlyingWriter() throws UnsupportedEncodingException
    {
        return super.getWriter();
    }

    /** Special subclass to signal this specific condition */
    public static class SizeLimitExceededException extends ApiUsageException
    {
        public SizeLimitExceededException()
        {
            super("Buffer limit of " + MAX_BUFFER_SIZE + " exceeded");
        }
    }

    /** Checks the size of the current output to make sure it hasn't exceeded the limit before appending the new content */
    private class SizeLimitingPrintWriter extends PrintWriter
    {
        private PrintWriter _out;

        public SizeLimitingPrintWriter(PrintWriter out)
        {
            super(out);
            _out = out;
        }

        @Override
        public void write(int c)
        {
            checkSize();
            _out.write(c);
        }

        private void checkSize()
        {
            if (isCommitted())
            {
                throw new SizeLimitExceededException();
            }
        }

        @Override
        public void write(char[] buf, int off, int len)
        {
            checkSize();
            _out.write(buf, off, len);
        }

        @Override
        public void write(char[] buf)
        {
            checkSize();
            _out.write(buf);
        }

        @Override
        public void write(String s, int off, int len)
        {
            checkSize();
            _out.write(s, off, len);
        }

        @Override
        public void write(String s)
        {
            checkSize();
            _out.write(s);
        }

        @Override
        public void print(boolean b)
        {
            checkSize();
            _out.print(b);
        }

        @Override
        public void print(char c)
        {
            checkSize();
            _out.print(c);
        }

        @Override
        public void print(int i)
        {
            checkSize();
            _out.print(i);
        }

        @Override
        public void print(long l)
        {
            checkSize();
            _out.print(l);
        }

        @Override
        public void print(float f)
        {
            checkSize();
            _out.print(f);
        }

        @Override
        public void print(double d)
        {
            checkSize();
            _out.print(d);
        }

        @Override
        public void print(char[] s)
        {
            checkSize();
            _out.print(s);
        }

        @Override
        public void print(String s)
        {
            checkSize();
            _out.print(s);
        }

        @Override
        public void print(Object obj)
        {
            checkSize();
            _out.print(obj);
        }

        @Override
        public void println()
        {
            checkSize();
            _out.println();
        }

        @Override
        public void println(boolean x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(char x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(int x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(long x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(float x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(double x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(char[] x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(String x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public void println(Object x)
        {
            checkSize();
            _out.println(x);
        }

        @Override
        public PrintWriter printf(String format, Object... args)
        {
            checkSize();
            return _out.printf(format, args);
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args)
        {
            checkSize();
            return _out.printf(l, format, args);
        }

        @Override
        public PrintWriter format(String format, Object... args)
        {
            checkSize();
            return _out.format(format, args);
        }

        @Override
        public PrintWriter format(Locale l, String format, Object... args)
        {
            checkSize();
            return _out.format(l, format, args);
        }

        @Override
        public PrintWriter append(CharSequence csq)
        {
            checkSize();
            return _out.append(csq);
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end)
        {
            checkSize();
            return _out.append(csq, start, end);
        }

        @Override
        public PrintWriter append(char c)
        {
            checkSize();
            return _out.append(c);
        }
    }
}
