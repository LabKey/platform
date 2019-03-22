/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.api.security;

import org.labkey.api.util.PageFlowUtil;

import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

/**
 * User: matthewb
 * Date: Feb 5, 2009
 * Time: 9:34:32 AM
  */
public class AuthenticatedResponse extends HttpServletResponseWrapper
{
    PrintWriter _safeWriter = null;

    public AuthenticatedResponse(HttpServletResponse response)
    {
        super(response);
    }

    @Override
    public PrintWriter getWriter() throws IOException
    {
        if (_safeWriter == null)
            _safeWriter = new SafePrintWriter(super.getWriter());
        return _safeWriter;
    }

    public class SafePrintWriter extends PrintWriter
    {
        PrintWriter _writer;
        SafePrintWriter(PrintWriter writer)
        {
            super((Writer)null);
            _writer = writer;
        }

        @Override
        public void flush()
        {
            _writer.flush();
        }

        @Override
        public void close()
        {
            _writer.close();
        }

        @Override
        public boolean checkError()
        {
            return _writer.checkError();
        }

        @Override
        public void write(int c)
        {
            _writer.write(c);
        }

        @Override
        public void write(char[] buf, int off, int len)
        {
            _writer.write(buf, off, len);
        }

        @Override
        public void write(char[] buf)
        {
            _writer.write(buf);
        }

        @Override
        public void write(String s, int off, int len)
        {
            _writer.write(s, off, len);
        }

        @Override
        public void write(String s)
        {
            _writer.write(s);
        }

        @Override
        public void print(boolean b)
        {
            _writer.print(b);
        }

        @Override
        public void print(char c)
        {
            _writer.print(c);
        }

        @Override
        public void print(int i)
        {
            _writer.print(i);
        }

        @Override
        public void print(long l)
        {
            _writer.print(l);
        }

        @Override
        public void print(float f)
        {
            _writer.print(f);
        }

        @Override
        public void print(double d)
        {
            _writer.print(d);
        }

        @Override
        public void print(char[] s)
        {
            _writer.print(s);
        }

        @Override
        public void print(String s)
        {
            _writer.print(s);
        }

        @Override
        public void print(Object obj)
        {
            _writer.print(obj);
        }

        @Override
        public void println()
        {
            _writer.println();
        }

        @Override
        public void println(boolean x)
        {
            _writer.println(x);
        }

        @Override
        public void println(char x)
        {
            _writer.println(x);
        }

        @Override
        public void println(int x)
        {
            _writer.println(x);
        }

        @Override
        public void println(long x)
        {
            _writer.println(x);
        }

        @Override
        public void println(float x)
        {
            _writer.println(x);
        }

        @Override
        public void println(double x)
        {
            _writer.println(x);
        }

        @Override
        public void println(char[] x)
        {
            _writer.println(x);
        }

        @Override
        public void println(String x)
        {
            _writer.println(x);
        }

        @Override
        public void println(Object x)
        {
            _writer.println(x);
        }

        @Override
        public PrintWriter printf(String format, Object... args)
        {
            return format(format, args);
        }

        @Override
        public PrintWriter printf(Locale l, String format, Object... args)
        {
            return format(l, format, args);
        }

        @Override
        public PrintWriter format(String format, Object... args)
        {
            return _writer.printf(PageFlowUtil.filter(String.format(format,args)));
        }

        @Override
        public PrintWriter format(Locale l, String format, Object... args)
        {
            return _writer.printf(PageFlowUtil.filter(String.format(l, format, args)));
        }

        @Override
        public PrintWriter append(CharSequence csq)
        {
            return _writer.append(csq);
        }

        @Override
        public PrintWriter append(CharSequence csq, int start, int end)
        {
            return _writer.append(csq, start, end);
        }

        @Override
        public PrintWriter append(char c)
        {
            return _writer.append(c);
        }
    }
}
