/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.security;

import java.io.IOException;
import java.security.Principal;
import java.net.URL;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.Logger;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.FirstRequestHandler;
import org.labkey.api.module.SafeFlushResponseWrapper;


public class AuthFilter implements Filter
{
    private static Logger _log = Logger.getLogger(AuthFilter.class);
    private static boolean _firstRequestHandled = false;
    private static final Object _firstRequestLock = new Object();


    public void init(FilterConfig filterConfig) throws ServletException
    {
    }


    public void destroy()
    {
    }


    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = new SafeFlushResponseWrapper((HttpServletResponse)response);

        Throwable t = ModuleLoader.getInstance().getStartupFailure();

        if (t != null)
        {
            ExceptionUtil.handleException(req, resp, t, null, true);
            return;
        }

        // No startup failure, so check for SSL redirection
        if (!req.getScheme().toLowerCase().equals("https") && AppProps.getInstance().isSSLRequired())
        {
            StringBuffer originalURL = req.getRequestURL();
            if (req.getQueryString() != null)
            {
                originalURL.append("?");
                originalURL.append(req.getQueryString());
            }
            URL url = new URL(originalURL.toString());
            url = new URL("https", url.getHost(), AppProps.getInstance().getSSLPort(), url.getFile());
            resp.sendRedirect(url.toString());
            return;
        }

        ensureFirstRequestHandled(req);

        User user = (User) req.getUserPrincipal();

        if (null == user)
        {
            user = SecurityManager.getAuthenticatedUser((HttpServletRequest) request);

            if (null != user)
                UserManager.updateActiveUser(user);

            req = new AuthenticatedRequest(req, user);
        }

        chain.doFilter(req, resp);
    }


    // CONSIDER: move to org.labkey.api.security.AuthenticatedRequest
    public static class AuthenticatedRequest extends HttpServletRequestWrapper
    {
        User _user;

        public AuthenticatedRequest(HttpServletRequest request, User user)
        {
            super(request);
            _user = null == user ? User.guest : user;
        }


        public Principal getUserPrincipal()
        {
            return _user;
        }
    }


    private void ensureFirstRequestHandled(HttpServletRequest request)
    {
        // Must be done early so init exceptions get logged to mothership, authentication gets initialized before
        // basic auth is attempted in this filter, etc.
        synchronized (_firstRequestLock)
        {
            if (_firstRequestHandled)
                return;

            AppProps.getInstance().initializeBaseServerUrl(request);
            AppProps.getInstance().setContextPath(request);
            FirstRequestHandler.handleFirstRequest(request);
            _firstRequestHandled = true;
        }
    }
}
