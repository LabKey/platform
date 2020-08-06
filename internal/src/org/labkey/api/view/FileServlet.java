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

package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Must keep ActionURL in sync.
 */
public class FileServlet extends HttpServlet
{
    static Logger _log = LogManager.getLogger(FileServlet.class);
    private static final String PAGE_FLOW_ATTR = FileServlet.class.getName() + ".pageFlow";

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String pathInfo = StringUtils.trimToEmpty(request.getPathInfo());
        int index = pathInfo.lastIndexOf("/@");         // new style URL's: /files/<container>/@files/<path>/<name>
        if (index < 0)
            index = pathInfo.lastIndexOf('/');          // legacy style: /files/<container>/<name>

        if (index < 0)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        //pathInfo is /${extraPath}/${fileName}
        String fileNameParam = StringUtils.trimToNull(request.getParameter("fileName"));
        String fileName = pathInfo.substring(index + 1);
        String extraPath = pathInfo.substring(0, index);

        // Store the original URL in case we need to redirect for authentication
        if (request.getAttribute(ViewServlet.ORIGINAL_URL_STRING) == null)
        {
            URLHelper helper = new URLHelper(request);
            request.setAttribute(ViewServlet.ORIGINAL_URL_STRING, helper.getURIString());
            request.setAttribute(ViewServlet.ORIGINAL_URL_URLHELPER, helper);
        }

        //The servlet path looks like a pageflow. Stash this away so that
        //handlers can create links to other static files.
        //We assume that this servlet is mapped to /something/*
        String servletPath = request.getServletPath();
        request.setAttribute(PAGE_FLOW_ATTR, servletPath.substring(1));
        String dispatchUrl = "/FileContent" + extraPath + "/sendFile.view?" + (null == fileNameParam ? "fileName=" + PageFlowUtil.encode(fileName) : "");
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
