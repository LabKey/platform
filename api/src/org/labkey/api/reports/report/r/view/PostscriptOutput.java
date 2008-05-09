package org.labkey.api.reports.report.r.view;

import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class PostscriptOutput extends AbstractParamReplacement
{
    public static final String ID = "psout:";

    public PostscriptOutput()
    {
        super(ID);
    }

    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = new File(directory, getName().concat(".ps"));

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        if (getReport() instanceof AttachmentParent)
            return new PostscriptReportView(this, (AttachmentParent)getReport());
        else
            return new HtmlView("Unable to render this output, no report associated with this replacement param");
    }

    public static class PostscriptReportView extends DownloadOutputView
    {
        PostscriptReportView(ParamReplacement param, AttachmentParent parent)
        {
            super(param, parent, "Postscript");
        }
    }
}
