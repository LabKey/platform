package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

/**
 * Streams content directly to the response without reading it all into memory.
 * User: jeckels
 * Date: Nov 6, 2011
 */
public class ReaderView extends WebPartView
{
    private final BufferedReader _reader;
    private final boolean _htmlEncode;
    private final String _prefix;
    private final String _suffix;

    /**
     * @param reader content source
     * @param htmlEncodeContent whether the content should be HTML encoded before it is sent to the client
     * @param prefix prefix to write before the content. Regardless of the value of htmlEncodeContent, the prefix will not be encoded.
     * @param suffix suffix to write after the content. Regardless of the value of htmlEncodeContent, the suffix will not be encoded.
     */
    public ReaderView(Reader reader, boolean htmlEncodeContent, @Nullable String prefix, @Nullable String suffix)
    {
        _htmlEncode = htmlEncodeContent;
        _prefix = prefix;
        _suffix = suffix;
        _reader = new BufferedReader(reader);
    }

    /**
     * @param in content source
     * @param htmlEncodeContent whether the content should be HTML encoded before it is sent to the client
     * @param prefix prefix to write before the content. Regardless of the value of htmlEncodeContent, the prefix will not be encoded.
     * @param suffix suffix to write after the content. Regardless of the value of htmlEncodeContent, the suffix will not be encoded.
     */
    public ReaderView(InputStream in, boolean htmlEncodeContent, @Nullable String prefix, @Nullable String suffix)
    {
        this(new InputStreamReader(in), htmlEncodeContent, prefix, suffix);
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        try
        {
            if (_prefix != null)
            {
                out.write(_prefix);
            }
            String line;
            while ((line = _reader.readLine()) != null)
            {
                if (_htmlEncode)
                {
                    out.println(PageFlowUtil.filter(line));
                }
                else
                {
                    out.println(line);
                }
            }
            if (_suffix != null)
            {
                out.write(_suffix);
            }
        }
        finally
        {
            try { _reader.close(); } catch (IOException ignored) {}
        }
    }
}
