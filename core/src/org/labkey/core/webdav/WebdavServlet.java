/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.core.webdav;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 17, 2008
 * Time: 2:03:32 PM
 */

import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import java.io.IOException;


public class WebdavServlet extends HttpServlet
{
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String fullPath = StringUtils.trimToEmpty(request.getPathInfo());

        // Store the original URL in case we need to redirect for authentication
        if (request.getAttribute(ViewServlet.ORIGINAL_URL) == null)
        {
            URLHelper helper = new URLHelper(request);
            request.setAttribute(ViewServlet.ORIGINAL_URL, helper.getURIString());
        }

        String method = request.getMethod();
        if (method.equals("GET") || method.equals("POST"))
        {
            String m = request.getHeader("method");
            if (m == null && method.equals("GET"))
                m = request.getParameter("method");
            if (null != m)
                method = m;
        }
        String dispatchUrl = "/" + DavController.name + "/" + method.toLowerCase() + ".view?path=" + PageFlowUtil.encodePath(fullPath);

        if (0==1) // dispatch
        {
            // NOTE other parameters seem to get magically propagated...
            RequestDispatcher r = request.getRequestDispatcher(dispatchUrl);
            r.forward(request, response);
        }
        else // direct (if DavController doesn't depends on ViewServlet)
        {
            ActionURL url = new ActionURL(dispatchUrl);
            url.setScheme(request.getScheme());
            url.setHost(request.getServerName());
            url.setPort(request.getServerPort());
            ViewContext context = new ViewContext();
            context.setRequest(request);
            context.setResponse(response);
            context.setActionURL(url);
            DavController dav = new DavController();
            dav.setViewContext(context);
            dav.setResourcePath(fullPath);

            int stackSize = HttpView.getStackSize();
            try
            {
                HttpView.initForRequest(context, request, response);
                dav.handleRequest(request, response);
            }
            finally
            {
                HttpView.resetStackSize(stackSize);
            }
        }
    }


    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
    }
}
