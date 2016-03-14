/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

/**
 * Streams content directly to the response without reading it all into memory. Backed by a {@link Reader}.
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
        super(FrameType.PORTAL);
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
            if (getPrefix() != null)
            {
                out.write(getPrefix());
            }
            String line;
            while ((line = getReader().readLine()) != null)
            {
                outputLine(out, line);
            }
            if (getSuffix() != null)
            {
                out.write(getSuffix());
            }
        }
        finally
        {
            try { getReader().close(); } catch (IOException ignored) {}
        }
    }

    public void outputLine(PrintWriter out, String line)
    {
        if (isHtmlEncode())
        {
            out.println(PageFlowUtil.filter(line, true, true));
        }
        else
        {
            out.println(line);
        }
    }

    public BufferedReader getReader()
    {
        return _reader;
    }

    public boolean isHtmlEncode()
    {
        return _htmlEncode;
    }

    public String getPrefix()
    {
        return _prefix;
    }

    public String getSuffix()
    {
        return _suffix;
    }
}
