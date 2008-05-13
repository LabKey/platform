/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.attachments;

import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.ContainerManager.RootContainer;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebTheme;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * User: jeckels
 * Date: Feb 6, 2006
 */
public class ImageServlet extends HttpServlet
{
    static final CacheableWriter noDocument = new CacheableWriter();

    private Calendar getExpiration()
    {
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 10);
        return cal;
    }

    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String imageName = "";
        String uri = request.getRequestURI();
        String file = uri.substring(uri.lastIndexOf('/') + 1);
        if (file.lastIndexOf('.') > 0)
        {
            String name = file.substring(0, file.lastIndexOf('.'));
            imageName = PageFlowUtil.decode(name);
        }
        try
        {
            if ("logo".equals(imageName))
            {
                sendLogo(request, response);
            }
            else if ("favicon".equals(imageName))
            {
                sendFavIcon(request, response);
            }
            else if ("gradient".equals(imageName))
            {
                sendGradient(request, response);
            }
            else if (imageName.startsWith("auth_"))
            {
                sendAuthLogo(imageName, response);
            }
            else
            {
                throw new ServletException("Unknown image requested - " + imageName);
            }
        }
        catch (SQLException e)
        {
            throw new ServletException(e);
        }
    }

    private void sendFavIcon(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, ServletException
    {
        CacheableWriter writer = AttachmentCache.getCachedFavIcon();
        if (writer == null)
        {
            writer = noDocument;

            // rootContainer will be null if the database isn't bootstrapped yet
            RootContainer rootContainer = RootContainer.get();
            if (rootContainer != null)
            {
                Attachment attachment = AttachmentCache.lookupFavIconAttachment();
                if (attachment != null)
                {
                    writer = new CacheableWriter();
                    AttachmentService.get().writeDocument(writer, rootContainer, AttachmentCache.FAVICON_FILE_NAME, false);
                }
                AttachmentCache.cacheFavIcon(writer);
            }
        }

        if (writer != noDocument)
        {
            writer.writeToResponse(response, getExpiration());
        }
        else
        {
            response.setDateHeader("Expires", getExpiration().getTimeInMillis());
            request.getRequestDispatcher("/_images/favicon.ico").include(request, response);
        }
    }


    protected void sendGradient(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        CacheableWriter writer;
        String lightColor = request.getParameter("lightColor");
        String darkColor = request.getParameter("darkColor");
        if (null==lightColor && null==darkColor) {
            writer = AttachmentCache.getCachedGradient();
            if (writer == null)
            {
                WebTheme theme = WebTheme.getTheme();
                Color light = theme.getGradientLightColor();
                Color dark = theme.getGradientDarkColor();
                writer = createGradient(light,dark);
                AttachmentCache.cacheGradient(writer);
            }
        } else {
            // for web theme defintion
            // this is a transient gradient
            Color light = new Color(Integer.parseInt(lightColor, 16));
            Color dark = new Color(Integer.parseInt(darkColor, 16));
            writer = createGradient(light,dark);
        }
        writer.writeToResponse(response, getExpiration());
    }


    private CacheableWriter createGradient(Color light,Color dark)
            throws IOException
    {
        final int height = 18;
        BufferedImage bi = new BufferedImage(1, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = bi.createGraphics();
        g.setColor(light);
        final int constantHeight = 8;
        g.drawLine(0, 0, 0, constantHeight);
        float redIncrement = ((float)dark.getRed() - light.getRed()) / (height - constantHeight);
        float greenIncrement = ((float)dark.getGreen() - light.getGreen()) / (height - constantHeight);
        float blueIncrement = ((float)dark.getBlue() - light.getBlue()) / (height - constantHeight);
        for (int i = 0; i <= height - constantHeight; i++)
        {
            Color c = new Color((int)(light.getRed() + redIncrement * i),
                                (int)(light.getGreen() + greenIncrement * i),
                                (int)(light.getBlue() + blueIncrement * i));
            g.setColor(c);
            g.drawLine(0, i + constantHeight, 0, i + constantHeight);
        }

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bOut);
        return new CacheableWriter("image/png", bOut.toByteArray());
    }


    protected void sendLogo(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, SQLException
    {
        CacheableWriter writer = AttachmentCache.getCachedLogo();
        if (writer == null)
        {
            writer = noDocument;

            // rootContainer will be null if the database isn't bootstrapped yet
            RootContainer rootContainer = RootContainer.get();
            if (rootContainer != null)
            {
                Attachment attachment = AttachmentCache.lookupLogoAttachment();
                if (attachment != null)
                {
                    writer = new CacheableWriter();
                    AttachmentService.get().writeDocument(writer, rootContainer, attachment.getName(), false);
                }
                AttachmentCache.cacheLogo(writer);
            }
        }

        if (writer != noDocument)
        {
            writer.writeToResponse(response, getExpiration());
        }
        else
        {
            response.setDateHeader("Expires", getExpiration().getTimeInMillis());
            request.getRequestDispatcher("/_images/defaultlogo.gif").include(request, response);
        }
    }


    protected void sendAuthLogo(String name, HttpServletResponse response) throws SQLException, IOException, ServletException
    {
        CacheableWriter writer = AttachmentCache.getAuthLogo(name);

        if (writer == null)
        {
            writer = noDocument;

            // rootContainer will be null if the database isn't bootstrapped yet
            RootContainer rootContainer = RootContainer.get();
            if (rootContainer != null)
            {
                Attachment attachment = AttachmentCache.lookupAttachment(name);
                if (attachment != null)
                {
                    writer = new CacheableWriter();
                    AttachmentService.get().writeDocument(writer, rootContainer, attachment.getName(), false);
                }
                AttachmentCache.cacheAuthLogo(name, writer);
            }
        }

        if (writer != noDocument)
        {
            writer.writeToResponse(response, getExpiration());
        }
    }
}
