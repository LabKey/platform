/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.study.reports;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.reports.ReportsController.DownloadAction;
import org.labkey.study.controllers.reports.ReportsController.DownloadReportFileAction;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * User: Mark Igra
 * Date: Jul 6, 2006
 * Time: 5:08:19 PM
 */
public class AttachmentReport extends RedirectReport implements DynamicThumbnailProvider
{
    public static final String TYPE = "Study.attachmentReport";
    public static final String FILE_PATH = "filePath";
    public static final String MODIFIED = "modified";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Study Attachment Report";
    }

    @Override
    public String getParams()
    {
        return getFilePath() == null ? null : "filePath=" + PageFlowUtil.encode(getFilePath());
    }

    @Override
    public void setParams(String params)
    {
        if (null == params)
            setFilePath(null);
        else
        {
            Map<String,String> paramMap = PageFlowUtil.mapFromQueryString(params);
            setFilePath(paramMap.get("filePath"));
        }
    }

    protected Container getContainer()
    {
        return ContainerManager.getForId(getDescriptor().getContainerId());
    }
    
    @Override
    public @Nullable String getUrl(ViewContext context)
    {
        Container c = getContainer();
        String entityId = getEntityId();

        //Can't throw because table layer calls this in uninitialized state...
        if (null == c || null == entityId)
            return null;

        if (null != getFilePath())
        {
            ActionURL url = new ActionURL(DownloadReportFileAction.class, getContainer());
            url.addParameter("reportId", getReportId().toString());
            return url.getLocalURIString();
        }

        Attachment latest = getLatestVersion();

        return null != latest ? latest.getDownloadUrl(DownloadAction.class).getLocalURIString() : null;
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

    public void setModified(Date modified)
    {
        getDescriptor().setProperty(MODIFIED, DateUtil.formatDate(modified));
    }

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
            Thumbnail getDynamicThumbnail(AttachmentReport report, String filename) throws IOException
            {
                DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

                if (null != svc)
                {
                    InputStream pdfStream = AttachmentService.get().getInputStream(report, filename);
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
            Thumbnail getDynamicThumbnail(AttachmentReport report, String filename) throws IOException
            {
                InputStream imageSteam = AttachmentService.get().getInputStream(report, filename);
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
            Thumbnail getDynamicThumbnail(AttachmentReport report, String filename) throws IOException
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
            Thumbnail getDynamicThumbnail(AttachmentReport report, String filename) throws IOException
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
            Thumbnail getDynamicThumbnail(AttachmentReport report, String filename) throws IOException
            {
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
            Thumbnail getDynamicThumbnail(AttachmentReport report, String filename) throws IOException
            {
                return null;
            }
        };

        abstract String getStaticThumbnailName();
        abstract Thumbnail getDynamicThumbnail(AttachmentReport report, String filename) throws IOException;

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
    public Thumbnail generateDynamicThumbnail(ViewContext context)
    {
        Type type = Type.getForContentType(getContentType());
        Attachment latest = getLatestVersion();

        if (null != latest)
        {
            try
            {
                return type.getDynamicThumbnail(this, latest.getName());
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        return null;
    }

    private String getContentType()
    {
        Attachment latest = getLatestVersion();

        if (null != latest)
        {
            String extension = latest.getFileExtension();
            MimeMap mm = new MimeMap();
            return mm.getContentType(extension);
        }
        else
        {
            return null;
        }
    }

    @Override
    public String getDynamicThumbnailCacheKey()
    {
        return "Reports:" + getReportId();
    }
}
