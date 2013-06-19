/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.Writer;
import java.io.IOException;
import java.io.StringReader;
import java.io.LineNumberReader;
import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Apr 24, 2006
 */
public class StackTraceDisplayColumn extends DataColumn
{
    private static final int MAX_LINES_TO_SHOW = 10;

    //Careful, a renderer without a resultset is only good for input forms
    public StackTraceDisplayColumn(ColumnInfo col)
    {
        super(col);
        setEditable(false);
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        renderContents(ctx, out, Integer.MAX_VALUE);
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderContents(ctx, out, Integer.MAX_VALUE);
    }

    private void renderContents(RenderContext ctx, Writer out, int maxLines) throws IOException
    {
        String stackTrace = (String)getValue(ctx);
        out.write("<pre style=\"margin: 4px 0px\">");
        StringBuilder sb = new StringBuilder();
        LineNumberReader reader = new LineNumberReader(new StringReader(stackTrace));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null)
        {
            lines.add(line);
        }

        for (int i = 0; i < lines.size(); i++)
        {
            if (i < maxLines)
            {
                sb.append(lines.get(i));
                sb.append("\n");
            }
        }

        if (reader.getLineNumber() >= maxLines)
        {
            sb.append("... (");
            sb.append(reader.getLineNumber());
            sb.append(" lines total)");
        }
        out.write(PageFlowUtil.filter(sb.toString()));
        out.write("</pre>");
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderContents(ctx, out, MAX_LINES_TO_SHOW);
    }
}
