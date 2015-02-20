/*
 * Copyright (c) 2004-2015 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.module.FirstRequestHandler;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SafeFlushResponseWrapper;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.impersonation.ImpersonationContextFactory;
import org.labkey.api.security.impersonation.UnauthorizedImpersonationException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HttpsUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewServlet;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;


@SuppressWarnings({"UnusedDeclaration"})
public class AuthFilter implements Filter
{
    private static final Object FIRST_REQUEST_LOCK = new Object();
    private static boolean _firstRequestHandled = false;
    private static volatile boolean _sslChecked = false;

    public void init(FilterConfig filterConfig) throws ServletException
    {
    }


    public void destroy()
    {
    }


    // This is the first (and last) LabKey code invoked on a request.
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        ViewServlet.setAsRequestThread();
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = new SafeFlushResponseWrapper((HttpServletResponse)response);

        Throwable t = ModuleLoader.getInstance().getStartupFailure();

        if (t != null)
        {
            ExceptionUtil.handleException(req, resp, t, null, true);
            return;
        }

        // allow CSRFUtil early access to req/resp if it wants to write cookies
        CSRFUtil.getExpectedToken(req, resp);

        // No startup failure, so check for SSL redirection
        if (!req.getScheme().equalsIgnoreCase("https") && AppProps.getInstance().isSSLRequired())
        {
            // We can't redirect posts (we'll lose the post body), so return an error code
            if ("post".equalsIgnoreCase(req.getMethod()))
            {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Can't POST to an http URL; POSTs to this server require https");
                return;
            }

            StringBuffer originalURL = req.getRequestURL();
            if (req.getQueryString() != null)
            {
                originalURL.append("?");
                originalURL.append(req.getQueryString());
            }
            URL url = new URL(originalURL.toString());
            int port = AppProps.getInstance().getSSLPort();
            if (port == 443)
            {
                port = -1;
            }
            url = new URL("https", url.getHost(), port, url.getFile());

            // If this is the first time redirecting to SSL, attempt a direct connection to the SSL URL first.
            // If connection fails, treat it like a startup failure. #10968 & #11103.
            if (!_sslChecked)
            {
                if (AppProps.getInstance().isDevMode())
                {
                    Pair<String, Integer> sslResult = HttpsUtil.testSslUrl(url, "This LabKey Server instance is configured to require secure connections, but it does not appear to be responding to HTTPS requests at " + url + "  Please see https://www.labkey.org/wiki/home/Documentation/page.view?name=stagingServerTips for details on how to turn off the setting without having to log into the server.");

                    // Non-null indicates that some problem occurred...
                    if (null != sslResult)
                    {
                        String message = sslResult.first;
                        Integer responseCode = sslResult.second;

                        // No response code means we couldn't connect to SSL, so display a configuration exception.
                        // Any other reponse (401, 404, etc.) means we were able to connect via SSL and it's just a
                        // bad URL or unauthorized or something normal. This addresses #19628
                        if (null == responseCode)
                        {
                            //noinspection ThrowableInstanceNeverThrown
                            ConfigurationException ce = new ConfigurationException(message);
                            ModuleLoader.getInstance().setStartupFailure(ce);
                            ExceptionUtil.handleException(req, resp, ce, null, true);
                            return;
                        }
                    }
                }

                _sslChecked = true;
            }

            resp.sendRedirect(url.toString());
            return;
        }

        // Must be done early so init exceptions get logged to mothership, authentication gets initialized before
        // basic auth is attempted in this filter, etc.
        ensureFirstRequestHandled(req);

        User user = (User) req.getUserPrincipal();

        if (null == user)
        {
            UnauthorizedImpersonationException e = null;

            try
            {
                user = SecurityManager.getAuthenticatedUser((HttpServletRequest) request);
            }
            catch (UnauthorizedImpersonationException uie)
            {
                // Impersonating admin must have had permissions revoked. Save away the details now, we'll then stash
                // the admin user in the request, then render unauthorized impersonation exception, then stop impersonating.
                ImpersonationContextFactory factory = uie.getFactory();
                user = factory.getAdminUser();
                e = uie;
            }

            if (null != user)
                UserManager.updateActiveUser(user);

            req = new AuthenticatedRequest(req, user);

            if (null != e)
            {
                // Render unauthorized impersonation exception so admin knows what's going on
                ExceptionUtil.handleException(req, resp, e, null, false);
                SecurityManager.stopImpersonating(req, e.getFactory());    // Needs to happen after rendering exception page, otherwise session gets messed up
                return;
            }
        }

        QueryService.get().setEnvironment(QueryService.Environment.USER, null==user ? User.guest : user);

        try
        {
            SecurityLogger.pushSecurityContext("AuthFilter " + req.getRequestURI(), user);
            chain.doFilter(req, resp);
        }
        finally
        {
            SecurityLogger.popSecurityContext();
            QueryService.get().clearEnvironment();
            
            // Clear all the request attributes that have been set.  This helps memtracker.  See #10747.
            assert clearRequestAttributes(req);
        }
    }


    private boolean clearRequestAttributes(HttpServletRequest request)
    {
        Enumeration attributeNames = request.getAttributeNames();

        while (attributeNames.hasMoreElements())
        {
            String name = (String)attributeNames.nextElement();
            if (name.startsWith("org.apache.tomcat."))
                continue;
            request.removeAttribute(name);
        }

        return true;
    }


    private void ensureFirstRequestHandled(HttpServletRequest request)
    {
        synchronized(FIRST_REQUEST_LOCK)
        {
            if (_firstRequestHandled)
                return;

            AppProps.getInstance().initializeFromRequest(request);
            FirstRequestHandler.handleFirstRequest(request);
            _firstRequestHandled = true;
        }
    }
}
