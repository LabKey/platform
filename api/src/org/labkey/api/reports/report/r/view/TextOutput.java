package org.labkey.api.reports.report.r.view;

import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class TextOutput extends AbstractParamReplacement
{
    public static final String ID = "txtout:";

    public TextOutput()
    {
        super(ID);
    }

    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt", directory);
        else
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.txt");

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        return new TextOutputView(this);
    }

    public static class TextOutputView extends ROutputView
    {
        public TextOutputView(ParamReplacement param)
        {
            super(param);
            setLabel("Text output");
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (getFile() != null && getFile().exists() && (getFile().length() > 0))
            {
                out.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"1\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td><pre>");
                else
                    out.write("<tr><td><pre>");
                out.write(PageFlowUtil.filter(PageFlowUtil.getFileContentsAsString(getFile()), false, true));
                out.write("</pre></td></tr>");
                out.write("</table>");
            }
        }
    }
}
