/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.r.AbstractParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.RReport;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import javax.imageio.ImageIO;
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

    protected String getExtension()
    {
        return "jpg";
    }

    @Override
    protected @Nullable FileLike getSubstitution(FileLike directory) throws IOException
    {
        FileLike file;
        if (directory != null)
            file = FileUtil.createTempFile(RReport.FILE_PREFIX, "Result." + getExtension(), directory);
        else
            file = FileUtil.createTempFileLike(RReport.FILE_PREFIX, "Result." + getExtension());

        addFile(file);
        return file;
    }

    @Override
    public HttpView<?> getView(ViewContext context)
    {
        return new ImgReportView(this, canDeleteFile());
    }

    @Override
    public ScriptOutput renderAsScriptOutput(FileLike file) throws IOException
    {
        ImgReportView view = new ImgReportView(this, canDeleteFile());
        String image = view.renderInternalAsString(file);

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
        private final boolean _deleteFile;

        ImgReportView(ParamReplacement param, boolean deleteFile)
        {
            super(param);
            setLabel("Image output");
            _deleteFile = deleteFile;
        }

        @Override
        protected String renderInternalAsString(FileLike file) throws IOException
        {
            String imgUrl = null;

            if (existsWithContent(file))
            {
                FileLike imgFile;
                if (!_deleteFile)
                    imgFile = file;
                else
                    imgFile = moveToTemp(file);

                if (imgFile != null)
                {
                    String key = ImageUtil.setFileInSession(getViewContext().getRequest(), imgFile);
                    ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer());
                    url.addParameters(PageFlowUtil.map(ImageUtil.FILE_SESSION_PARAM, key, ImageUtil.DELETE_FILE_PARAM, Boolean.toString(_deleteFile), ImageUtil.CACHE_FILE_PARAM, "true"));
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
        protected void renderInternal(Object model, PrintWriter out) throws IOException
        {
            for (FileLike file : getFiles())
            {
                String imgUrl = renderInternalAsString(file);

                if (null != imgUrl)
                {
                    out.write("<table class=\"labkey-output\">");
                    renderTitle(out);
                    if (isCollapse())
                        out.write("<tr style=\"display:none\"><td>");
                    else
                        out.write("<tr><td>");
                    out.write("<img name=\"resultImage\" id=\"" + getUniqueId("resultImage") + "\" src=\"");
                    out.write(PageFlowUtil.filter(imgUrl));
                    out.write("\">");
                    out.write("</td></tr>");
                    out.write("</table>");
                }
            }
        }
    }

    @Override
    public Thumbnail renderThumbnail(ViewContext context) throws IOException
    {
        for (FileLike file : getFiles())
        {
            // just render the first file, in most cases this is appropriate
            if (file.exists())
                return ImageUtil.renderThumbnail(ImageIO.read(file.openInputStream()));
        }
        return null;
    }
}
