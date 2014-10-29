/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.api.reports.report.r.view;

import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.GUID;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
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

    public ImageOutput(String id)
    {
        super(id);
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
        return new ImgReportView(this, canDeleteFile());
    }

    @Override
    public ScriptOutput renderAsScriptOutput() throws Exception
    {
        ImgReportView view = new ImgReportView(this, canDeleteFile());
        String image = view.renderInternalAsString();

        if (null != image)
            return new ScriptOutput(ScriptOutput.ScriptOutputType.image, getName(), image);

        return null;
    }

    protected boolean canDeleteFile()
    {
        Report report = getReport();

        if (report != null)
        {
            if (BooleanUtils.toBoolean(report.getDescriptor().getProperty(ReportDescriptor.Prop.cached)) ||
                BooleanUtils.toBoolean(report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground)))
                return false;
        }

        return true;
    }

    public static class ImgReportView extends ROutputView
    {
        private boolean _deleteFile;

        ImgReportView(ParamReplacement param, boolean deleteFile)
        {
            super(param);
            setLabel("Image output");
            _deleteFile = deleteFile;
        }

        @Override
        protected String renderInternalAsString() throws Exception
        {
            String imgUrl = null;

            if (exists())
            {
                File imgFile;
                if (!_deleteFile)
                    imgFile = getFile();
                else
                    imgFile = moveToTemp(getFile(), "RReportImg");

                if (imgFile != null)
                {
                    String key = "temp:" + GUID.makeGUID();
                    getViewContext().getRequest().getSession(true).setAttribute(key, imgFile);
                    ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer());
                    url.addParameters(PageFlowUtil.map("sessionKey", key, "deleteFile", Boolean.toString(_deleteFile), "cacheFile", "true"));
                    imgUrl = url.getLocalURIString();
                }
            }
            else
            {
                LOG.debug("No file exists for parameter: " + getName());
            }

            return imgUrl;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            String imgUrl = renderInternalAsString();

            if (null != imgUrl)
            {
                out.write("<table class=\"labkey-output\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td>");
                else
                    out.write("<tr><td>");
                out.write("<img id=\"" + getUniqueId("resultImage") + "\" src=\"");
                out.write(PageFlowUtil.filter(imgUrl));
                out.write("\">");
                out.write("</td></tr>");
                out.write("</table>");
            }
        }
    }

    @Override
    public Thumbnail renderThumbnail(ViewContext context) throws IOException
    {
        return ImageUtil.renderThumbnail(ImageIO.read(getFile()));
    }
}
