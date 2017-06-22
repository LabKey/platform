/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: Karl Lum
 * Date: May 7, 2008
 */
public class PdfOutput extends DownloadParamReplacement
{
    public static final String ID = "pdfout:";

    public PdfOutput()
    {
        super(ID);
    }

    @Override
    protected File getSubstitution(File directory) throws Exception
    {
        return getSubstitution(directory, ".pdf");
    }

    public HttpView render(ViewContext context)
    {
        if (getReport() instanceof AttachmentParent)
            return new PdfReportView(this, getReport());
        else
            return new HtmlView(DownloadParamReplacement.UNABlE_TO_RENDER);
    }

    @Override
    public ScriptOutput renderAsScriptOutput(File file) throws Exception
    {
        if (getReport() instanceof AttachmentParent)
            return renderAsScriptOutput(file, new PdfReportView(this, getReport()),
                    ScriptOutput.ScriptOutputType.pdf);
        else
            return renderAsScriptOutputError();
    }

    public static class PdfReportView extends DownloadOutputView
    {
        PdfReportView(ParamReplacement param, AttachmentParent parent)
        {
            super(param, parent, "PDF");
        }
    }

    @Override
    public @Nullable Thumbnail renderThumbnail(ViewContext context) throws IOException
    {
        DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

        if (null == svc)
            return null;

        for (File file : getFiles())
        {
            // just render the first file, in most cases this is appropriate
            if (file.exists())
            {
                InputStream pdfStream = new FileInputStream(file);
                BufferedImage image = svc.pdfToImage(pdfStream, 0);

                return ImageUtil.renderThumbnail(image);
            }
        }
        return null;
    }
}
