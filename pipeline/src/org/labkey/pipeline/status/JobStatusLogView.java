package org.labkey.pipeline.status;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ReaderView;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

/**
 * User: cnathe
 * Date: Feb 17, 2012
 */
public class JobStatusLogView extends ReaderView
{
    boolean _highlightingError = false;

    public JobStatusLogView(InputStream in, boolean htmlEncodeContent, @Nullable String prefix, @Nullable String suffix)
    {

        super(in, htmlEncodeContent, prefix, suffix);
    }

    @Override
    public void outputLine(PrintWriter out, String line)
    {
        String[] tokens = line.split(" ");
        // for a normal log file INFO, ERROR, etc. line, the type will be the 5th token
        if (tokens.length > 4)
        {
            String type = tokens[4];
            if (!_highlightingError && type.equals("ERROR:"))
            {
                _highlightingError = true;
                out.write("<font class=\"labkey-error\">");
            }
            else if (_highlightingError && (type.equals("INFO") || type.equals("DEBUG:") || type.equals("WARN")))
            {
                _highlightingError = false;
                out.write("</font>");
            }
        }
        super.outputLine(out, line);
    }
}
