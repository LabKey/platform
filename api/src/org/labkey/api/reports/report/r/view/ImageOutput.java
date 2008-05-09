package org.labkey.api.reports.report.r.view;

import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class ImageOutput extends AbstractParamReplacement
{
    public static final String ID = "imgout:";

    public ImageOutput()
    {
        super(ID);
    }

    public File convertSubstitution(File directory) throws Exception
    {
        if (directory != null)
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.jpg", directory);
        else
            _file = File.createTempFile(RReport.FILE_PREFIX, "Result.jpg");

        return _file;
    }

    public HttpView render(ViewContext context)
    {
        return new ImgReportView(this);
    }

    public static class ImgReportView extends ROutputView
    {
        ImgReportView(ParamReplacement param)
        {
            super(param);
            setLabel("Image output");
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (getFile() != null && getFile().exists())
            {
                if (getFile().length() > 0)
                {
                    File imgFile = moveToTemp(getFile());
                    if (imgFile != null)
                    {
                        String key = "temp:" + GUID.makeGUID();
                        getViewContext().getRequest().getSession(true).setAttribute(key, imgFile);

                        out.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"1\">");
                        renderTitle(model, out);
                        if (isCollapse())
                            out.write("<tr style=\"display:none\"><td>");
                        else
                            out.write("<tr><td>");
                        out.write("<img border=0 id=\"resultImage\" src=\"");

                        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer());
                        url.addParameters(PageFlowUtil.map("sessionKey", key, "deleteFile", Boolean.toString(true), "cacheFile", "true"));

                        out.write(url.getLocalURIString());
                        out.write("\">");
                        out.write("</td></tr>");
                        out.write("</table>");
                    }
                }
                else
                    getFile().delete();
            }
        }

        private File moveToTemp(File file)
        {
            try {
                File newFile = File.createTempFile("RReportImg", "tmp");
                newFile.delete();

                if (file.renameTo(newFile))
                    return newFile;
            }
            catch (IOException ioe)
            {
                throw new RuntimeException(ioe);
            }
            return null;
        }
    }
}
