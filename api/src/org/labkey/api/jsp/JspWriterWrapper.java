package org.labkey.api.jsp;

import org.jetbrains.annotations.NotNull;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.io.Writer;

public class JspWriterWrapper extends JspWriter
{
    private final JspWriter _jspWriter;

    public JspWriterWrapper(JspWriter jspWriter)
    {
        super(0, true);
        _jspWriter = jspWriter;
    }

    public void newLine() throws IOException
    {
        _jspWriter.newLine();
    }

    public void print(boolean b) throws IOException
    {
        _jspWriter.print(b);
    }

    public void print(char c) throws IOException
    {
        _jspWriter.print(c);
    }

    public void print(int i) throws IOException
    {
        _jspWriter.print(i);
    }

    public void print(long l) throws IOException
    {
        _jspWriter.print(l);
    }

    public void print(float v) throws IOException
    {
        _jspWriter.print(v);
    }

    public void print(double v) throws IOException
    {
        _jspWriter.print(v);
    }

    public void print(char[] chars) throws IOException
    {
        _jspWriter.print(chars);
    }

    public void print(String s) throws IOException
    {
        _jspWriter.print(s);
    }

    public void print(Object o) throws IOException
    {
        _jspWriter.print(o);
    }

    public void println() throws IOException
    {
        _jspWriter.println();
    }

    public void println(boolean b) throws IOException
    {
        _jspWriter.println(b);
    }

    public void println(char c) throws IOException
    {
        _jspWriter.println(c);
    }

    public void println(int i) throws IOException
    {
        _jspWriter.println(i);
    }

    public void println(long l) throws IOException
    {
        _jspWriter.println(l);
    }

    public void println(float v) throws IOException
    {
        _jspWriter.println(v);
    }

    public void println(double v) throws IOException
    {
        _jspWriter.println(v);
    }

    public void println(char[] chars) throws IOException
    {
        _jspWriter.println(chars);
    }

    public void println(String s) throws IOException
    {
        _jspWriter.println(s);
    }

    public void println(Object o) throws IOException
    {
        _jspWriter.println(o);
    }

    public void clear() throws IOException
    {
        _jspWriter.clear();
    }

    public void clearBuffer() throws IOException
    {
        _jspWriter.clearBuffer();
    }

    public void flush() throws IOException
    {
        _jspWriter.flush();
    }

    public void close() throws IOException
    {
        _jspWriter.close();
    }

    public int getBufferSize()
    {
        return _jspWriter.getBufferSize();
    }

    public int getRemaining()
    {
        return _jspWriter.getRemaining();
    }

    public boolean isAutoFlush()
    {
        return _jspWriter.isAutoFlush();
    }

    public static Writer nullWriter()
    {
        return Writer.nullWriter();
    }

    public void write(int c) throws IOException
    {
        _jspWriter.write(c);
    }

    public void write(@NotNull char[] cbuf) throws IOException
    {
        _jspWriter.write(cbuf);
    }

    public void write(@NotNull char[] cbuf, int off, int len) throws IOException
    {
        _jspWriter.write(cbuf, off, len);
    }

    public void write(@NotNull String str) throws IOException
    {
        _jspWriter.write(str);
    }

    public void write(@NotNull String str, int off, int len) throws IOException
    {
        _jspWriter.write(str, off, len);
    }

    public Writer append(CharSequence csq) throws IOException
    {
        return _jspWriter.append(csq);
    }

    public Writer append(CharSequence csq, int start, int end) throws IOException
    {
        return _jspWriter.append(csq, start, end);
    }

    public Writer append(char c) throws IOException
    {
        return _jspWriter.append(c);
    }
}
