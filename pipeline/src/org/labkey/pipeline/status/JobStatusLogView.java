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
    public JobStatusLogView(InputStream in, boolean htmlEncodeContent, @Nullable String prefix, @Nullable String suffix)
    {

        super(in, htmlEncodeContent, prefix, suffix);
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
            boolean highlightError = false;
            while ((line = getReader().readLine()) != null)
            {
                String[] tokens = line.split(" ");
                // for a normal long file INFO, ERROR, etc. line, the type will be the 5th token
                if (tokens.length > 5)
                {
                    String type = tokens[4];
                    if (!highlightError && type.equals("ERROR:"))
                    {
                        highlightError = true;
                        out.write("<BR/><font class=\"labkey-error\">");
                    }
                    else if (highlightError && (type.equals("INFO") || type.equals("DEBUG:") || type.equals("WARN")))
                    {
                        highlightError = false;
                        out.write("</font><BR/>");
                    }
                }

                if (isHtmlEncode())
                {
                    out.println(PageFlowUtil.filter(line));
                }
                else
                {
                    out.println(line);
                }
            }
            if (highlightError)
            {
                out.write("</font>");
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
}
