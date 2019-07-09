/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.jsp;

import org.jetbrains.annotations.NotNull;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.io.Writer;

public class JspWriterWrapper extends JspWriter
{
    private final JspWriter _jspWriter;

    JspWriterWrapper(JspWriter jspWriter)
    {
        super(0, true);
        _jspWriter = jspWriter;
    }

    @Override
    public void newLine() throws IOException
    {
        _jspWriter.newLine();
    }

    @Override
    public void print(boolean b) throws IOException
    {
        _jspWriter.print(b);
    }

    @Override
    public void print(char c) throws IOException
    {
        _jspWriter.print(c);
    }

    @Override
    public void print(int i) throws IOException
    {
        _jspWriter.print(i);
    }

    @Override
    public void print(long l) throws IOException
    {
        _jspWriter.print(l);
    }

    @Override
    public void print(float v) throws IOException
    {
        _jspWriter.print(v);
    }

    @Override
    public void print(double v) throws IOException
    {
        _jspWriter.print(v);
    }

    @Override
    public void print(char[] chars) throws IOException
    {
        _jspWriter.print(chars);
    }

    @Override
    public void print(String s) throws IOException
    {
        _jspWriter.print(s);
    }

    @Override
    public void print(Object o) throws IOException
    {
        _jspWriter.print(o);
    }

    @Override
    public void println() throws IOException
    {
        _jspWriter.println();
    }

    @Override
    public void println(boolean b) throws IOException
    {
        _jspWriter.println(b);
    }

    @Override
    public void println(char c) throws IOException
    {
        _jspWriter.println(c);
    }

    @Override
    public void println(int i) throws IOException
    {
        _jspWriter.println(i);
    }

    @Override
    public void println(long l) throws IOException
    {
        _jspWriter.println(l);
    }

    @Override
    public void println(float v) throws IOException
    {
        _jspWriter.println(v);
    }

    @Override
    public void println(double v) throws IOException
    {
        _jspWriter.println(v);
    }

    @Override
    public void println(char[] chars) throws IOException
    {
        _jspWriter.println(chars);
    }

    @Override
    public void println(String s) throws IOException
    {
        _jspWriter.println(s);
    }

    @Override
    public void println(Object o) throws IOException
    {
        _jspWriter.println(o);
    }

    @Override
    public void clear() throws IOException
    {
        _jspWriter.clear();
    }

    @Override
    public void clearBuffer() throws IOException
    {
        _jspWriter.clearBuffer();
    }

    @Override
    public void flush() throws IOException
    {
        _jspWriter.flush();
    }

    @Override
    public void close() throws IOException
    {
        _jspWriter.close();
    }

    @Override
    public int getBufferSize()
    {
        return _jspWriter.getBufferSize();
    }

    @Override
    public int getRemaining()
    {
        return _jspWriter.getRemaining();
    }

    @Override
    public boolean isAutoFlush()
    {
        return _jspWriter.isAutoFlush();
    }

    @Override
    public void write(int c) throws IOException
    {
        _jspWriter.write(c);
    }

    @Override
    public void write(@NotNull char[] cbuf) throws IOException
    {
        _jspWriter.write(cbuf);
    }

    @Override
    public void write(@NotNull char[] cbuf, int off, int len) throws IOException
    {
        _jspWriter.write(cbuf, off, len);
    }

    @Override
    public void write(@NotNull String str) throws IOException
    {
        _jspWriter.write(str);
    }

    @Override
    public void write(@NotNull String str, int off, int len) throws IOException
    {
        _jspWriter.write(str, off, len);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException
    {
        return _jspWriter.append(csq);
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException
    {
        return _jspWriter.append(csq, start, end);
    }

    @Override
    public Writer append(char c) throws IOException
    {
        return _jspWriter.append(c);
    }
}
