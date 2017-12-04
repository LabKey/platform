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
package org.labkey.api.settings;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.LookAndFeelResourceAttachmentParent;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.NotFoundException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * User: adam
 * Date: Aug 6, 2008
 * Time: 10:23:38 AM
 */
public enum TemplateResourceHandler
{
    LOGO
    {
        protected String getResourceName()
        {
            return "logo.image";
        }

        protected String getDefaultLink(Container c)
        {
            return "/_images/lk-noTAG-" + resolveLogoThemeName(c) + ".svg";
        }

        protected CacheableWriter getWriterForContainer(Container c) throws IOException, ServletException
        {
            // container will be null if the database isn't bootstrapped yet
            CacheableWriter writer = (null == c ? null : AttachmentCache.getCachedLogo(c));

            if (writer == null)
            {
                writer = CacheableWriter.noDocument;

                if (c != null)
                {
                    LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
                    Attachment attachment = AttachmentCache.lookupLogoAttachment(c);
                    if (attachment != null)
                    {
                        writer = new CacheableWriter();
                        AttachmentService.get().writeDocument(writer, parent, attachment.getName(), false);
                    }
                    AttachmentCache.cacheLogo(parent.getContainer(), writer);
                }
            }

            return writer;
        }
    },

    LOGO_MOBILE
    {
        protected String getResourceName()
        {
            return "logo-mobile.image";
        }

        protected String getDefaultLink(Container c)
        {
            return "/_images/mobile-logo-" + resolveLogoThemeName(c) + ".svg";
        }

        protected CacheableWriter getWriterForContainer(Container c) throws IOException, ServletException
        {
            // container will be null if the database isn't bootstrapped yet
            CacheableWriter writer = (null == c ? null : AttachmentCache.getCachedLogo(c));

            if (writer == null)
            {
                writer = CacheableWriter.noDocument;

                if (c != null)
                {
                    LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
                    Attachment attachment = AttachmentCache.lookupMobileLogoAttachment(c);
                    if (attachment != null)
                    {
                        writer = new CacheableWriter();
                        AttachmentService.get().writeDocument(writer, parent, attachment.getName(), false);
                    }
                    AttachmentCache.cacheLogo(parent.getContainer(), writer);
                }
            }

            return writer;
        }
    },

    FAVICON
    {
        protected String getResourceName()
        {
            return "favicon.image";
        }

        protected String getDefaultLink(Container c)
        {
            return "/_images/favicon.ico";
        }

        protected CacheableWriter getWriterForContainer(Container c) throws IOException, ServletException
        {
            // rootContainer will be null if the database isn't bootstrapped yet
            CacheableWriter writer = (null == c ? null : AttachmentCache.getCachedFavIcon(c));
            if (writer == null)
            {
                writer = CacheableWriter.noDocument;

                if (c != null)
                {
                    LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
                    Attachment attachment = AttachmentCache.lookupFavIconAttachment(parent);
                    if (attachment != null)
                    {
                        writer = new CacheableWriter();
                        AttachmentService.get().writeDocument(writer, parent, AttachmentCache.FAVICON_FILE_NAME, false);
                    }
                    AttachmentCache.cacheFavIcon(parent.getContainer(), writer);
                }
            }

            return writer;
        }
    };

    abstract protected String getResourceName();
    abstract protected String getDefaultLink(Container c);
    abstract protected CacheableWriter getWriterForContainer(Container c) throws IOException, ServletException;

    private static String resolveLogoThemeName(Container c)
    {
        String themeName = PageFlowUtil.resolveThemeName(c);
        if ("seattle".equalsIgnoreCase(themeName))
            return "seattle";
        return "overcast";
    }

    private Calendar getExpiration()
    {
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 35);
        return cal;
    }


    public ResourceURL getURL(Container c)
    {
        return new ResourceURL(getResourceName(), LookAndFeelProperties.getSettingsContainer(c));
    }


    public void sendResource(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        ResourceURL url = new ResourceURL(request);
        Path containerPath = url.getParsedPath().getParent();
        Container c = LookAndFeelProperties.getSettingsContainer(ContainerManager.getForPath(containerPath));  // Shouldn't be requesting anything but root & project, but just in case

        if (c == null)
        {
            throw new NotFoundException("No such container: " + containerPath);
        }

        CacheableWriter writer = getWriter(c);

        if (writer != CacheableWriter.noDocument)
        {
            writer.writeToResponse(response, getExpiration());
        }
        else
        {
            response.setDateHeader("Expires", getExpiration().getTimeInMillis());
            request.getRequestDispatcher(getDefaultLink(c)).forward(request, response);
        }
    }


    private CacheableWriter getWriter(Container c) throws IOException, ServletException
    {
        CacheableWriter writer = getWriterForContainer(c);

        if (CacheableWriter.noDocument == writer && !c.isRoot())
            writer = getWriter(c.getParent());

        return writer;
    }
}
