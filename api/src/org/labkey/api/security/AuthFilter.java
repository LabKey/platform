/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SafeFlushResponseWrapper;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.impersonation.ImpersonationContextFactory;
import org.labkey.api.security.impersonation.UnauthorizedImpersonationException;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HttpUtil;
import org.labkey.api.util.HttpsUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.PageConfig;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Random;


@SuppressWarnings({"UnusedDeclaration"})
public class AuthFilter implements Filter
{
    private static final Object FIRST_REQUEST_LOCK = new Object();
    private static boolean _firstRequestHandled = false;
    private static volatile boolean _sslChecked = false;
    private static SecurityPointcutService _securityPointcut = null;

    @Override
    public void init(FilterConfig filterConfig)
    {
    }

    // This is the first (and last) LabKey code invoked on a request.
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        ViewServlet.setAsRequestThread();
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = new SafeFlushResponseWrapper((HttpServletResponse) response);

        if (null != _securityPointcut)
        {
            if (!_securityPointcut.beforeProcessRequest(req, resp))
                return;
        }

        if (ModuleLoader.getInstance().isStartupComplete())
        {
            if (!"ALLOW".equals(AppProps.getInstance().getXFrameOption()))
                resp.setHeader("X-Frame-Options", AppProps.getInstance().getXFrameOption());
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("Referrer-Policy", "origin-when-cross-origin" );
        }

        Throwable t = ModuleLoader.getInstance().getStartupFailure();

        if (t != null)
        {
            ExceptionUtil.handleException(req, resp, t, null, true);
            return;
        }

        // Versions of tomcat prior to 7.0.67 would automatically redirect if a URL with just a context path without
        // a trailing slash was entered ie: http://www.labkey.org/labkey would automatically redirect to: http://www.labkey.org/labkey/
        // We need to handle it here otherwise the begin page redirect in index.html will fail: issue 25395

        // getServletPath() will return an empty String when a URL with a context path but no trailing slash is requested
        if (req.getServletPath().isEmpty())
        {
            // now check if this is the case where the request URL contains only the context path
            if (req.getContextPath() != null && req.getRequestURL().toString().endsWith(req.getContextPath()))
            {
                StringBuilder redirectURL = new StringBuilder();
                redirectURL.append(req.getRequestURL()).append("/");
                if (!StringUtils.isBlank(req.getQueryString()))
                {
                    redirectURL.append("?").append(req.getQueryString());
                }
                resp.sendRedirect(redirectURL.toString());
                return;
            }
        }

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
            if (!StringUtils.isBlank(req.getQueryString()))
            {
                originalURL.append("?");
                originalURL.append(req.getQueryString());
            }
            URL url = new URL(originalURL.toString());
            int port = AppProps.getInstance().getSSLPort();

            // Check the SSL configuration if this is the first time doing an SSL redirect. Note: The redirect and check must
            // happen before ensureFirstRequestHandled() so AppProps gets initialized with the SSL scheme & port. That means
            // this check can't be handled in a FirstRequestListener.
            if (!_sslChecked)
            {
                HttpsUtil.checkSslRedirectConfiguration(req, port);
                _sslChecked = true;
            }

            if (port == 443)
            {
                port = -1;
            }
            url = new URL("https", url.getHost(), port, url.getFile());
            // Use 301 redirect instead of a 302 to indicate it's a permanent move
            resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            resp.setHeader("Location", resp.encodeRedirectURL(url.toString()));
            return;
        }

        // allow CSRFUtil early access to req/resp if it wants to write cookies
        CSRFUtil.getExpectedToken(req, resp);

        // Must be done early so init exceptions get logged to mothership, authentication gets initialized before
        // basic auth is attempted in this filter, etc.
        ensureFirstRequestHandled(req);

        assert null == req.getUserPrincipal();

        User user = null;
        UnauthorizedImpersonationException e = null;

        try
        {
            Pair<User, HttpServletRequest> pair = SecurityManager.attemptAuthentication(req);

            if (null != pair)
            {
                user = pair.getKey();
                req = pair.getValue();
            }
        }
        catch (UnauthorizedImpersonationException uie)
        {
            // Impersonating admin must have had permissions revoked. Save away the details now, we'll then stash
            // the admin user in the request, then render unauthorized impersonation exception, then stop impersonating.
            ImpersonationContextFactory factory = uie.getFactory();
            user = factory.getAdminUser();
            e = uie;
        }
        catch (UnsupportedEncodingException uee)
        {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, uee.getMessage());
            return;
        }
        catch (UnauthorizedException ue)
        {
            ExceptionUtil.handleException(req, resp, ue, ue.getMessage(), false);
            return;
        }

        if (null == user)
        {
            if (AppProps.getInstance().isOptionalFeatureEnabled(AppProps.EXPERIMENTAL_NO_GUESTS))
                user = User.nobody;
            else
                user = User.guest;
        }
        else
            UserManager.updateRecentUser(user.isImpersonated() ? user.getImpersonatingUser() : user); // TODO: Sanity check this with Matt... treat impersonating admin as active, not impersonated user

        req = AuthenticatedRequest.create(req, user);

        if (null != e)
        {
            // Render unauthorized impersonation exception so admin knows what's going on
            ExceptionUtil.handleException(req, resp, e, null, false);
            SecurityManager.stopImpersonating(req, e.getFactory());    // Needs to happen after rendering exception page, otherwise session gets messed up
            ((AuthenticatedRequest) req).close();
            return;
        }

        QueryService.get().setEnvironment(QueryService.Environment.USER, user);

        if (AppProps.getInstance().isOptionalFeatureEnabled("experimental-unsafe-inline"))
        {
            String csp = StringUtils.trimToEmpty(((HttpServletResponse) response).getHeader("Content-Security-Policy"));
            String nonceDirectiveValue = "'nonce-" + PageConfig.getScriptNonceHeader(req) + "'";
            if (!csp.contains(nonceDirectiveValue))
            {
                if (!csp.contains("script-src "))
                {
                    if (StringUtils.isNotBlank(csp))
                        csp = StringUtils.appendIfMissing(csp, ";");
                    csp += "script-src 'unsafe-eval' http: https:  " + nonceDirectiveValue + ";";
                }
                ((HttpServletResponse) response).setHeader("Content-Security-Policy", csp);
            }
        }

        try
        {
            SecurityLogger.pushSecurityContext("AuthFilter " + req.getRequestURI(), user);
            addRandomHeader(req, resp);
            HttpUtil.trackClientApiRequests(req);
            chain.doFilter(req, resp);
        }
        finally
        {
            int status = resp.getStatus();
            if (null != _securityPointcut)
            {
                _securityPointcut.afterProcessRequest(req, resp);
            }

            // We don't get session creation events for sessions that were started earlier and serialized/deserialized
            // across Tomcat restarts. Ensure that all authenticated users have their sessions tracked, so we can
            // accurately assess if anyone is logged in
            HttpSession s = req.getSession(false);
            if (s != null && !user.isGuest())
            {
                UserManager.ensureSessionTracked(s);
            }

            SecurityLogger.popSecurityContext();
            QueryService.get().clearEnvironment();
            DebugInfoDumper.resetThreadDumpContext();

            // Clear all the request attributes that have been set. This helps memtracker.  See #10747.
            assert clearRequestAttributes(req);
            ((AuthenticatedRequest) req).close();
        }
    }

    private void addRandomHeader(HttpServletRequest req, HttpServletResponse resp)
    {
        // make response size  a bit random (compressed or not)
        StringBuilder sb = new StringBuilder(GUID.makeHash(req.getQueryString()));
        Random r = new Random();
        for (int i=r.nextInt(32) ; i>0 ; i--)
            sb.append((char)('A' + r.nextInt(26)));
        resp.addHeader("X-LK-NONCE", sb.toString());
    }



    private boolean clearRequestAttributes(HttpServletRequest request)
    {
        IteratorUtils.asIterator(request.getAttributeNames()).forEachRemaining(name -> {
            if (!name.startsWith("org.apache.tomcat."))
                request.removeAttribute(name);
        });

        return true;
    }


    private void ensureFirstRequestHandled(HttpServletRequest request)
    {
        synchronized (FIRST_REQUEST_LOCK)
        {
            if (_firstRequestHandled)
                return;

            AppProps.getInstance().ensureBaseServerUrl(request);
            ModuleLoader.getInstance().attemptStartBackgroundThreads();

            _securityPointcut = SecurityPointcutService.get();

            _firstRequestHandled = true;
        }
    }
}
