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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class PdfOutput extends DownloadParamReplacement
{
    public static final String ID = "pdfout:";

    public PdfOutput()
    {
        super(ID);
    }

    @Override
    protected @Nullable FileLike getSubstitution(FileLike directory)
    {
        return getSubstitution(directory, ".pdf");
    }

    @Override
    public HttpView<?> getView(ViewContext context)
    {
        if (getReport() != null)
            return new PdfReportView(this);
        else
            return HtmlView.of(DownloadParamReplacement.UNABLE_TO_RENDER);
    }

    @Override
    public ScriptOutput renderAsScriptOutput(FileLike file) throws IOException
    {
        if (getReport() != null)
            return renderAsScriptOutput(file, new PdfReportView(this),
                    ScriptOutput.ScriptOutputType.pdf);
        else
            return renderAsScriptOutputError();
    }

    public static class PdfReportView extends DownloadOutputView
    {
        PdfReportView(ParamReplacement param)
        {
            super(param, "PDF");
        }
    }

    @Override
    public @Nullable Thumbnail renderThumbnail(ViewContext context) throws IOException
    {
        DocumentConversionService svc = DocumentConversionService.get();

        if (null == svc)
            return null;

        for (FileLike file : getFiles())
        {
            // just render the first file, in most cases this is appropriate
            if (file.exists())
            {
                BufferedImage image = svc.pdfToImage(file.toNioPathForRead().toFile(), 0);
                return ImageUtil.renderThumbnail(image);
            }
        }
        return null;
    }
}
