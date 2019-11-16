/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.settings.TemplateResourceHandler;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.NotFoundException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * User: jeckels
 * Date: Feb 6, 2006
 */
public class ImageServlet extends HttpServlet
{
    private Calendar getExpiration()
    {
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 10);
        return cal;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
    {
        try
        {
            String imageName = "";
            ResourceURL url;

            try
            {
                url = new ResourceURL(request);
            }
            catch (Exception x)
            {
                throw new NotFoundException();
            }

            String file = url.getParsedPath().getName();
            if (file.lastIndexOf('.') > 0)
            {
                imageName = file.substring(0, file.lastIndexOf('.'));
            }
            if ("logo".equals(imageName))
            {
                TemplateResourceHandler.LOGO.sendResource(request, response);
            }
            else if ("logo-mobile".equals(imageName))
            {
                TemplateResourceHandler.LOGO_MOBILE.sendResource(request, response);
            }
            else if ("favicon".equals(imageName))
            {
                TemplateResourceHandler.FAVICON.sendResource(request, response);
            }
            else if (imageName.startsWith("auth_"))
            {
                String configuration = url.getParameter("configuration");
                if (null == configuration)
                    throw new NotFoundException("Auth logo request is missing configuration parameter - " + url);

                int rowId = Integer.valueOf(configuration);
                SSOAuthenticationConfiguration ssoConfiguration = AuthenticationManager.getSSOConfiguration(rowId);

                if (null == ssoConfiguration)
                    throw new NotFoundException("Auth logo request specifies an unknown configuration - " + url);

                sendAuthLogo(imageName, ssoConfiguration, response);
            }
            else
            {
                throw new NotFoundException("Unknown image requested - " + imageName);
            }
        }
        catch (Throwable e)
        {
            ExceptionUtil.handleException(request, response, e, null, false);
        }
    }


    protected void sendAuthLogo(String name, SSOAuthenticationConfiguration configuration, HttpServletResponse response) throws IOException, ServletException
    {
        String cacheKey = name + "_" + configuration;
        CacheableWriter writer = AttachmentCache.getAuthLogo(cacheKey);

        if (writer == null)
        {
            writer = CacheableWriter.noDocument;

            // rootContainer will be null if the database isn't bootstrapped yet (??)
            if (configuration.getContainerId() != null)
            {
                Attachment attachment = AttachmentCache.lookupAttachment(configuration, name);
                if (attachment != null)
                {
                    writer = new CacheableWriter();
                    AttachmentService.get().writeDocument(writer, configuration, attachment.getName(), false);
                }
                AttachmentCache.cacheAuthLogo(cacheKey, writer);
            }
        }

        if (writer != CacheableWriter.noDocument)
        {
            writer.writeToResponse(response, getExpiration());
        }
    }
}
