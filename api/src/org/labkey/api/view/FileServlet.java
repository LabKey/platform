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

package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.labkey.api.util.logging.LogHelper;

import java.io.IOException;

/**
 * Must keep ActionURL in sync.
 */
public class FileServlet extends HttpServlet
{
    private static final Logger _log = LogHelper.getLogger(FileServlet.class, "Forwards requests from /files to the FileContent module");

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String pathInfo = StringUtils.trimToEmpty(request.getPathInfo());
        int index = pathInfo.lastIndexOf("/@"); // new style URL's: /files/<container>/@files/<path>/<name> or /files/<container>/@files/<path>/?fileName=<name>
        if (index < 0)
            index = pathInfo.lastIndexOf('/');  // legacy style: /files/<container>/<name> or /files/<container>/?fileName=<name>

        if (index < 0)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        //pathInfo is /<container>>/<name>
        String fileNameParam = StringUtils.trimToNull(request.getParameter("fileName"));
        String fileName = pathInfo.substring(index + 1);
        String containerPath = pathInfo.substring(0, index);
        Container c = ContainerManager.getForPath(containerPath);
        if (c == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Store the original URL in case we need to redirect for authentication
        if (request.getAttribute(ViewServlet.ORIGINAL_URL_STRING) == null)
        {
            URLHelper helper = new URLHelper(request);
            request.setAttribute(ViewServlet.ORIGINAL_URL_STRING, helper.getURIString());
            request.setAttribute(ViewServlet.ORIGINAL_URL_URLHELPER, helper);
        }

        SimpleMetricsService.get().increment("API", "FileServlet", "urlsDispatched");
        String dispatchUrl = containerPath + "/filecontent-sendFile.view?" + (null == fileNameParam ? "fileName=" + PageFlowUtil.encodeURIComponent(fileName) : "");
        _log.info("FileServlet dispatching {} to {}", request.getRequestURL(), dispatchUrl);
        // NOTE other parameters seem to get magically propagated...
        RequestDispatcher r = request.getRequestDispatcher(dispatchUrl);
        r.forward(request, response);
    }


    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        _log.info("FileServlet initialized");
    }

    @Override
    public void log(String s, Throwable t)
    {
        _log.error(s, t);
    }
}
