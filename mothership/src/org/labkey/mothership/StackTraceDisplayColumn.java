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

    private List<String> trimStackTrace(List<String> fullTrace)
    {
        List<String> result = new ArrayList<String>();
        int i = fullTrace.size() - 1;
        while (i >= 0)
        {
            String line = fullTrace.get(i);
            if (!line.startsWith("\tat java.") &&
                !line.startsWith("\tat javax.") &&
                !line.startsWith("\tat org.apache."))
            {

            }
            i--;
        }
        return null;
    }

    private void renderContents(RenderContext ctx, Writer out, int maxLines) throws IOException
    {
        String stackTrace = (String)getValue(ctx);
        out.write("<pre>");
        StringBuilder sb = new StringBuilder();
        LineNumberReader reader = new LineNumberReader(new StringReader(stackTrace));
        List<String> lines = new ArrayList<String>();
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
