/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

package org.labkey.query.reports;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.query.reports.ReportsController.DownloadAction;
import org.labkey.query.reports.ReportsController.DownloadReportFileAction;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ListIterator;

/**
 * User: Mark Igra
 * Date: Jul 6, 2006
 * Time: 5:08:19 PM
 */
public class AttachmentReport extends BaseRedirectReport implements DynamicThumbnailProvider
{
    public static final String TYPE = "Study.attachmentReport";     // Misnomer (it's no longer part of study), but keep this for backward compatibility
    public static final String FILE_PATH = "filePath";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Attachment Report";
    }

    @Override
    public @Nullable String getUrl(Container c)
    {
        String entityId = getEntityId();

        //Can't throw because table layer calls this in uninitialized state...
        if (null == c || null == entityId)
            return null;

        // Server filePath attachment report type
        if (null != getFilePath())
        {
            ActionURL url = new ActionURL(DownloadReportFileAction.class, c);
            url.addParameter("reportId", getReportId().toString());
            return url.getLocalURIString();
        }

        // Uploaded attachment attachment report type
        Attachment latest = getLatestVersion();
        if (null != latest)
            return latest.getDownloadUrl(DownloadAction.class).getLocalURIString();

        return null;
    }

    public @Nullable Attachment getLatestVersion()
    {
        if (null == getEntityId())
            return null;

        List<Attachment> attachments = AttachmentService.get().getAttachments(this);

        if (attachments.isEmpty())
            return null;

        ListIterator<Attachment> iter = attachments.listIterator(attachments.size());

        // Iterate in reverse order and return the first non-thumbnail attachment we find
        while (iter.hasPrevious())
        {
            Attachment current = iter.previous();
            if (!current.getName().equals(ThumbnailService.THUMBNAIL_FILENAME))
                return current;
        }

        // Something went horribly wrong... I guess we only have a thumbnail
        return attachments.get(attachments.size() - 1);
    }

    public void setFilePath(String filePath)
    {
        getDescriptor().setProperty(FILE_PATH, filePath);
    }

    public String getFilePath()
    {
        return getDescriptor().getProperty(FILE_PATH);
    }

    /*
    UNDONE: The createAttachmentReport.jsp doesn't support editing yet.
    @Override
    public ActionURL getEditReportURL(ViewContext context)
    {
        return ReportsController.getAttachmentReportURL(context.getContainer(), context.getActionURL()).
            addParameter(ReportDescriptor.Prop.reportId, getReportId().toString());
    }
    */

    enum Type
    {
        PDF
        {
            @Override
            String getStaticThumbnailName()
            {
                return "pdf";
            }

            @Override
            Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException
            {
                DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                if (null != svc)
                {
                    InputStream pdfStream = report.getInputStream();
                    BufferedImage image = svc.pdfToImage(pdfStream, 0);

                    return ImageUtil.renderThumbnail(image);
                }

                return null;
            }
        },

        Image
        {
            @Override
            String getStaticThumbnailName()
            {
                return "image";
            }

            @Override
            Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException
            {
                InputStream imageSteam = report.getInputStream();
                BufferedImage image = ImageIO.read(imageSteam);

                return ImageUtil.renderThumbnail(image);
            }
        },

        Document
        {
            @Override
            String getStaticThumbnailName()
            {
                return "wordprocessing";
            }

            @Override
            Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException
            {
                return null;
            }
        },

        Spreadsheet
        {
            @Override
            String getStaticThumbnailName()
            {
                return "spreadsheet";
            }

            @Override
            Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException
            {
                return null;
            }
        },

        Presentation
        {
            @Override
            String getStaticThumbnailName()
            {
                return "presentation";
            }

            @Override
            Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException
            {
                return null;
            }
        },

        SVG
        {
            @Override
            String getStaticThumbnailName()
            {
                return "image";
            }

            @Override
            Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException
            {
                DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                if (null != svc)
                {
                    ThumbnailOutputStream os = new ThumbnailOutputStream();

                    try
                    {
                        svc.svgToPng(PageFlowUtil.getStreamContentsAsString(report.getInputStream()), os, 256.0f);

                        return os.getThumbnail("image/png");
                    }
                    catch (TranscoderException e)
                    {
                        Logger.getLogger(AttachmentReport.class).error("Couldn't generate thumbnail", e);
                    }
                }

                return null;
            }
        },

        Other
        {
            @Override
            String getStaticThumbnailName()
            {
                return "unknown";
            }

            @Override
            Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException
            {
                return null;
            }
        };

        abstract String getStaticThumbnailName();
        abstract Thumbnail getDynamicThumbnail(AttachmentReport report) throws IOException;

        private static Type getForContentType(String contentType)
        {
            if (null == contentType)
                return Type.Other;

            if ("application/pdf".equals(contentType))
                return Type.PDF;

            if ("application/msword".equals(contentType) ||
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType))
                return Type.Document;

            if ("application/ms-excel".equals(contentType) ||
                "application/vnd.ms-excel".equals(contentType) ||
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType))
                return Type.Spreadsheet;

            if ("application/mspowerpoint".equals(contentType) ||
                "application/vnd.ms-powerpoint".equals(contentType) ||
                "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(contentType))
                return Type.Presentation;

            if ("image/svg+xml".equals(contentType))
                return Type.SVG;

            if (contentType.startsWith("image/"))
                return Type.Image;

            return Type.Other;
        }
    }

    @Override
    public Thumbnail getStaticThumbnail()
    {
        Type type = Type.getForContentType(getContentType());
        InputStream is = AttachmentReport.class.getResourceAsStream(type.getStaticThumbnailName() + ".png");
        return new Thumbnail(is, "image/png");
    }

    @Override
    public String getStaticThumbnailCacheKey()
    {
        Type type = Type.getForContentType(getContentType());
        return "AttachmentReport:" + type.name();
    }

    @Override
    public Thumbnail generateDynamicThumbnail(@Nullable ViewContext context)
    {
        Type type = Type.getForContentType(getContentType());

        try
        {
            return type.getDynamicThumbnail(this);
        }
        catch (Throwable e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }

        return null;
    }

    private InputStream getInputStream() throws FileNotFoundException
    {
        // TODO: Again, subclasses would be nice...
        String filepath = getFilePath();

        if (null != filepath)
        {
            return new FileInputStream(filepath);
        }
        else
        {
            Attachment latest = getLatestVersion();

            if (null == latest)
                throw new FileNotFoundException();

            return AttachmentService.get().getInputStream(this, latest.getName());
        }
    }

    private String getContentType()
    {
        String extension = getExtension();

        if (null != extension)
        {
            MimeMap mm = new MimeMap();
            return mm.getContentType(extension);
        }
        else
        {
            return null;
        }
    }

    // TODO: Should subclass AttachmentReport, one for actual attachment and one for server filepath
    private @Nullable String getExtension()
    {
        String filePath = getFilePath();

        if (null != filePath)
            return Attachment.getFileExtension(getFilePath());

        Attachment latest = getLatestVersion();

        if (null != latest)
            return latest.getFileExtension();

        return null;
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return "Reports:" + getReportId();
    }

}
