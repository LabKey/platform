/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import java.io.PrintWriter;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 7, 2008
 */
public abstract class DownloadOutputView extends ROutputView
{
    private String _fileType;
    private AttachmentParent _parent;

    DownloadOutputView(ParamReplacement param, AttachmentParent parent, String fileType)
    {
        super(param);
        _parent = parent;
        _fileType = fileType;
        setLabel("Attachment output");
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        if (getFile() != null && getFile().exists() && (getFile().length() > 0))
        {
            if (_parent.getEntityId() != null)
            {
                AttachmentFile form = new FileAttachmentFile(getFile());
                AttachmentService.get().deleteAttachment(_parent, getFile().getName(), null);
                AttachmentService.get().addAttachments(_parent, Collections.singletonList(form), null);
            }
            out.write("<table class=\"labkey-output\">");
            renderTitle(model, out);
            if (isCollapse())
                out.write("<tr style=\"display:none\"><td>");
            else
                out.write("<tr><td>");

            if (_parent.getEntityId() != null)
            {
                for (Attachment a : AttachmentService.get().getAttachments(_parent))
                {
                    if (getFile().getName().equals(a.getName()))
                    {
                        out.write("<a href=\"");
                        out.write(PageFlowUtil.filter(a.getDownloadUrl(PageFlowUtil.urlProvider(ReportUrls.class).getDownloadClass())));
                        out.write("\">");
                        out.write(_fileType);
                        out.write(" output file (click to download)</a>");
                        break;
                    }
                }
            }
            else
            {
                // file hasn't been saved yet
                String key = "temp:" + GUID.makeGUID();
                getViewContext().getRequest().getSession(true).setAttribute(key, getFile());

                out.write("<a href=\"");
                out.write(PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer()).
                        addParameters(PageFlowUtil.map("sessionKey", key, "deleteFile", "false", "attachment", "true")).getLocalURIString());
                out.write("\">");
                out.write(_fileType);
                out.write(" output file (click to download)</a>");
            }
            out.write("</td></tr>");
            out.write("</table>");
        }
    }
}
