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

package org.labkey.api.reports.report.r.view;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * User: Karl Lum
 * Date: May 7, 2008
 */
public abstract class DownloadOutputView extends ROutputView
{
    private final String _fileType;

    DownloadOutputView(ParamReplacement param, String fileType)
    {
        super(param);
        _fileType = fileType;
        setLabel("Attachment output");
    }

    protected String renderException(Exception e)
    {
        String message = "Error, unable to upload file: " + e.getMessage();
        if (e.getMessage().contains("larger than the maximum"))
        {
            message += ". Contact your administrator to have the maximum file size increased.";
        }

        return message;
    }

    @Override
    protected String renderInternalAsString(FileLike file) throws IOException
    {
        String downloadUrl = null;

        if (file != null && file.exists() && (file.getSize() > 0))
        {
            FileLike newFile = moveToTemp(file);
            // file hasn't been saved yet
            String key = ImageUtil.setFileInSession(getViewContext().getRequest(), newFile);
            downloadUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer()).
                    addParameters(PageFlowUtil.map(
                            ImageUtil.FILE_SESSION_PARAM, key,
                            ImageUtil.DELETE_FILE_PARAM, "false",
                            ImageUtil.ATTACHMENT_PARAM, "true")).getLocalURIString();
        }
        return downloadUrl;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws IOException
    {
        for (FileLike file : getFiles())
        {

            String downloadUrl = renderInternalAsString(file);
            String filename = file.getName();

            // if we "failed" because the file doesn't exist then no
            // exception is thrown; just return immediately.
            if (null == downloadUrl)
                return;

            out.write("<table class=\"labkey-output\">");
            renderTitle(out);
            if (isCollapse())
                out.write("<tr style=\"display:none\"><td>");
            else
                out.write("<tr><td>");

            out.write("<a href=\"");
            out.write(downloadUrl);
            out.write("\">");
            if (StringUtils.stripToNull(filename) == null)
            {
                out.write(_fileType);
                out.write(" output file (click to download)");
            }
            else
            {
                out.write(filename);
            }
            out.write("</a>");

            out.write("</td></tr>");
            out.write("</table>");
        }
    }
}
