package org.labkey.api.writer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRender;

import java.io.IOException;
import java.io.Writer;

public class HtmlWriter implements Appendable
{
    private final Writer _writer;

    private HtmlWriter(Writer writer)
    {
        _writer = writer;
    }

    public static HtmlWriter of(Writer writer)
    {
        return new HtmlWriter(writer);
    }

    public Writer unwrap()
    {
        return _writer;
    }

    public void write(SafeToRender safeToRender)
    {
        try
        {
            _writer.write(safeToRender.toString());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void write(Number number) throws IOException
    {
        _writer.write(number.toString());
    }

    public void write(boolean b) throws IOException
    {
        _writer.write(Boolean.toString(b));
    }

    public void write(JSONObject json) throws IOException
    {
        _writer.write(json.toString());
    }

    public void write(JSONArray array) throws IOException
    {
        _writer.write(array.toString());
    }

    public void write(String s) throws IOException
    {
        _writer.write(PageFlowUtil.filter(s));
    }

    @Override
    public Appendable append(CharSequence csq) throws IOException
    {
        _writer.append(csq);
        return this;
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException
    {
        _writer.append(csq, start, end);
        return this;
    }

    @Override
    public Appendable append(char c) throws IOException
    {
        _writer.append(c);
        return this;
    }
}
