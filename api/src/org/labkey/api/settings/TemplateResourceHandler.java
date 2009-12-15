/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.ContainerParent;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.Path;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
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

        protected String getDefaultLink()
        {
            return "/_images/defaultlogo.gif";
        }

        protected CacheableWriter getWriterForContainer(Container c) throws SQLException, IOException, ServletException
        {
            // container will be null if the database isn't bootstrapped yet
            CacheableWriter writer = (null == c ? null : AttachmentCache.getCachedLogo(c));

            if (writer == null)
            {
                writer = noDocument;

                if (c != null)
                {
                    ContainerParent parent = new ContainerParent(c);
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

    FAVICON
    {
        protected String getResourceName()
        {
            return "favicon.image";
        }

        protected String getDefaultLink()
        {
            return "/_images/favicon.ico";
        }

        protected CacheableWriter getWriterForContainer(Container c) throws SQLException, IOException, ServletException
        {
            // rootContainer will be null if the database isn't bootstrapped yet
            CacheableWriter writer = (null == c ? null : AttachmentCache.getCachedFavIcon(c));
            if (writer == null)
            {
                writer = noDocument;

                if (c != null)
                {
                    ContainerParent parent = new ContainerParent(c);
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
    abstract protected String getDefaultLink();
    abstract protected CacheableWriter getWriterForContainer(Container c) throws SQLException, IOException, ServletException;

    public static final CacheableWriter noDocument = new CacheableWriter();

    private Calendar getExpiration()
    {
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 35);
        return cal;
    }


    public ResourceURL getURL(Container c)
    {
        try
        {
            Container settingsContainer = LookAndFeelProperties.getSettingsContainer(c);
            CacheableWriter writer = getWriterForContainer(settingsContainer);

            if (noDocument == writer)
                settingsContainer = ContainerManager.getRoot();

            return new ResourceURL(getResourceName(), settingsContainer);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (ServletException e)
        {
            throw new RuntimeException(e);
        }
    }


    public void sendResource(HttpServletRequest request, HttpServletResponse response) throws SQLException, IOException, ServletException
    {
        ResourceURL url = new ResourceURL(request);
        Path containerPath = url.getParsedPath().getParent();
        Container c = LookAndFeelProperties.getSettingsContainer(ContainerManager.getForPath(containerPath));  // Shouldn't be requesting anything but root & project, but just in case

        CacheableWriter writer = getWriter(c);

        if (writer != noDocument)
        {
            writer.writeToResponse(response, getExpiration());
        }
        else
        {
            response.setDateHeader("Expires", getExpiration().getTimeInMillis());
            request.getRequestDispatcher(getDefaultLink()).forward(request, response);
        }
    }


    private CacheableWriter getWriter(Container c) throws SQLException, IOException, ServletException
    {
        CacheableWriter writer = getWriterForContainer(c);

        if (noDocument == writer && !c.isRoot())
            writer = getWriter(c.getParent());

        return writer;
    }
}
