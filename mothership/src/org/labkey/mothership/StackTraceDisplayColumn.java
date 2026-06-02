/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.mothership;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.DOM;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.PRE;
import static org.labkey.api.util.DOM.at;

public class StackTraceDisplayColumn extends DataColumn
{
    private static final int MAX_LINES_TO_SHOW = 10;

    //Careful, a renderer without a ResultSet is only good for input forms
    public StackTraceDisplayColumn(ColumnInfo col)
    {
        super(col);
        setEditable(false);
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        renderContents(ctx, out, Integer.MAX_VALUE);
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
    {
        renderContents(ctx, out, Integer.MAX_VALUE);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        renderContents(ctx, out, MAX_LINES_TO_SHOW);
    }

    private void renderContents(RenderContext ctx, HtmlWriter out, int maxLines)
    {
        String stackTrace = (String)getValue(ctx);
        LineNumberReader reader = new LineNumberReader(new StringReader(stackTrace));
        List<String> lines = new ArrayList<>();
        try
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                lines.add(line);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        PRE(
            at(style, "margin: 4px 0px"),
            (DOM.Renderable) ret -> {
                for (int i = 0; i < lines.size(); i++)
                {
                    if (i < maxLines)
                    {
                        out.write(lines.get(i) + "\n");
                    }
                }

                if (reader.getLineNumber() >= maxLines)
                {
                    out.write("... (" + reader.getLineNumber() + " lines total)");
                }

                return ret;
            }
        ).appendTo(out);
    }
}
