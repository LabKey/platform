/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;

/**
 * User: Karl Lum
 * Date: May 7, 2008
 */
public abstract class DownloadOutputView extends ROutputView
{
    private String _fileType;
    private AttachmentParent _parent;
    private String _lastError;

    DownloadOutputView(ParamReplacement param, AttachmentParent parent, String fileType)
    {
        super(param);
        _parent = parent;
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
    protected String renderInternalAsString(File file) throws Exception
    {
        String downloadUrl = null;

        if (file != null && file.exists() && (file.length() > 0))
        {
            File newFile = moveToTemp(file, "RReportPdf");
            // file hasn't been saved yet
            String key = "temp:" + GUID.makeGUID();
            getViewContext().getRequest().getSession(true).setAttribute(key, newFile);
            downloadUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer()).
                    addParameters(PageFlowUtil.map("sessionKey", key, "deleteFile", "false", "attachment", "true")).getLocalURIString();
        }
        return downloadUrl;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        for (File file : getFiles())
        {
            String downloadUrl = null;
            boolean errorWritten = false;

            try
            {
                downloadUrl = renderInternalAsString(file);
            }
            catch (IOException e)
            {
                out.write(renderException(e));
                errorWritten = true;
            }

            // if we "failed" because the file doesn't exist then no
            // exception is thrown; just return immediately.
            if (null == downloadUrl && !errorWritten)
                return;

            out.write("<table class=\"labkey-output\">");
            renderTitle(model, out);
            if (isCollapse())
                out.write("<tr style=\"display:none\"><td>");
            else
                out.write("<tr><td>");

            if (null != downloadUrl)
            {
                out.write("<a href=\"");
                out.write(downloadUrl);
                out.write("\">");
                out.write(_fileType);
                out.write(" output file (click to download)</a>");
            }

            out.write("</td></tr>");
            out.write("</table>");
        }
    }
}
