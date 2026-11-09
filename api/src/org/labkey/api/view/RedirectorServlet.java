/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.api.view;

import java.io.IOException;
import java.net.URL;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.labkey.api.util.ExceptionUtil;

/** Simple redirector to redirect and forward from legacy context paths to the root context */
public class RedirectorServlet extends HttpServlet
{
    private final String _legacyContextPath;

    public RedirectorServlet(String legacyContextPath)
    {
        if (!legacyContextPath.startsWith("/") || legacyContextPath.length() < 2)
        {
            throw new IllegalArgumentException("Legacy context path must start with / and cannot be the root context path. Invalid path: " + legacyContextPath + ", specified via context.legacyContextPath in application.properties");
        }
        _legacyContextPath = legacyContextPath;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        if ("get".equalsIgnoreCase(request.getMethod()))
        {
            // Send a redirect to let the client know there's a new preferred URL
            StringBuffer rawUrl = request.getRequestURL();
            // getRequestURL() doesn't include GET parameters, so append them if needed
            if (request.getQueryString() != null)
            {
                rawUrl.append("?").append(request.getQueryString());
            }
            URL originalUrl = new URL(rawUrl.toString());
            URL redirectUrl = new URL(originalUrl.getProtocol(),
                    originalUrl.getHost(),
                    originalUrl.getPort(),
                    originalUrl.getFile().replaceFirst(_legacyContextPath, ""));

            ExceptionUtil.unsafeRedirect(response, redirectUrl.toString(), HttpServletResponse.SC_MOVED_PERMANENTLY);
        }
        else
        {
            // For non-GETs, use a forward so that we don't lose POST parameters, etc
            String originalUri = request.getRequestURI();
            String forwardUri = originalUri.replaceFirst(_legacyContextPath, "");

            getServletContext().getRequestDispatcher(forwardUri).forward(request, response);
        }
    }
}
