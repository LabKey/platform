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

package org.labkey.core.webdav;

/**
 * User: matthewb
 * Date: Apr 17, 2008
 * Time: 2:03:32 PM
 */

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResolverImpl;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;


public class WebdavServlet extends HttpServlet
{
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        if (null != _serverHeader)
            response.setHeader("Server", _serverHeader);
        response.setBufferSize(32768);

        String fullPath = (null==request.getServletPath()?"":request.getServletPath()) + (null==request.getPathInfo()?"":request.getPathInfo());

        URLHelper helper;
        try
        {
            helper = new URLHelper(request);
        }
        catch (IllegalArgumentException x)
        {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, x.getMessage());
            return;
        }

        // Store the original URL in case we need to redirect for authentication
        if (null == request.getAttribute(ViewServlet.ORIGINAL_URL_STRING))
        {
            request.setAttribute(ViewServlet.ORIGINAL_URL_URLHELPER, helper);
            request.setAttribute(ViewServlet.ORIGINAL_URL_STRING, helper.getURIString());
        }

        String method = request.getMethod();
        if ("GET".equals(method) || "POST".equals(method))
        {
            String m = request.getHeader("Method");
            if (m == null)
                m = helper.getParameter("method");
            if (!StringUtils.isEmpty(m) && !StringUtils.equals(method,m))
            {
                // POST can map to mutating methods, but GET can not
                m = m.toUpperCase();
                if ("POST".equals(method) || "PROPFIND".equals(m) || "OPTIONS".equals(m) || "JSON".equals(m) || "LASTERROR".equals(m) || "ZIP".equals(m) || "GET".equals(m) || "MD5SUM".equals(m))
                    method = m;
                else
                {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            }
        }

        if (("POST".equals(request.getMethod()) || "POST".equals(method)) && "POST".equals(AppProps.getInstance().getCSRFCheck()))
        {
            CSRFUtil.validate(request, response);
        }

        if ("GET".equals(method) && "/".equals(fullPath) && AppProps.getInstance().getSiteWelcomePageUrlString() != null)
        {
            try
            {
                URLHelper url = new URLHelper(AppProps.getInstance().getSiteWelcomePageUrlString());
                Path path = url.getParsedPath();
                if (!AppProps.getInstance().getContextPath().isEmpty() && path.startsWith(AppProps.getInstance().getParsedContextPath()))
                    path = path.subpath(1, path.size());
                String queryString = StringUtils.trimToEmpty(url.getQueryString());
                RequestDispatcher r = request.getRequestDispatcher(path.toString("/", null) + queryString);
                r.forward(request, response);
                if (response.getStatus() != HttpStatus.SC_NOT_FOUND)
                    return;
            }
            catch (URISyntaxException e)
            {
            }
            Logger.getRootLogger().error("Improper welcome URL: " + AppProps.getInstance().getSiteWelcomePageUrlString());
            try {response.reset();}catch(IllegalStateException x){/*pass*/}
        }

        ActionURL dispatchUrl = new ActionURL("/" + DavController.name + "/" + method.toLowerCase() + ".view");
        dispatchUrl.addParameters(helper.getParameters());
        dispatchUrl.replaceParameter("path",fullPath);
        dispatchUrl.setScheme(request.getScheme());
        dispatchUrl.setHost(request.getServerName());
        dispatchUrl.setPort(request.getServerPort());

        ViewContext context = new ViewContext();
        context.setRequest(request);
        context.setResponse(response);
        context.setActionURL(dispatchUrl);
        DavController dav = new DavController();
        dav.setResolver(_resolver);
        dav.setViewContext(context);
        dav.setUrlResourcePath(fullPath);
        int stackSize = HttpView.getStackSize();
        // Only track non-GET requests
        try (RequestInfo t = "GET".equalsIgnoreCase(method) ? null : MemTracker.get().startProfiler(request, method.toUpperCase() + " " + fullPath))
        {
            HttpView.initForRequest(context, request, response);
            dav.handleRequest(request, response);
        }
        finally
        {
            HttpView.resetStackSize(stackSize);
        }
    }


    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        String resolver = config.getInitParameter("resolver");
        if (ModuleStaticResolverImpl.class.getName().equals(resolver))
            _resolver = ModuleStaticResolverImpl.get();
        else if (WebdavResolverImpl.class.getName().equals(resolver))
            _resolver = WebdavResolverImpl.get();
        else
            throw new IllegalArgumentException("resolver");
//        _serverHeader =  "Labkey/" + AppProps.getInstance().getLabkeyVersionString();
    }

    WebdavResolver _resolver;
    String _serverHeader;
}
