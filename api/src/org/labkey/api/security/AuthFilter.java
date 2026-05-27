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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Random;


@SuppressWarnings({"UnusedDeclaration"})
public class AuthFilter implements Filter
{
    private static final Object FIRST_REQUEST_LOCK = new Object();

    public static final String STRICT_TRANSPORT_SECURITY_HEADER_NAME = "Strict-Transport-Security";
    public static final String X_FRAME_OPTIONS_HEADER_NAME = "X-Frame-Options";
    public static final String X_CONTENT_TYPE_OPTIONS_HEADER_NAME = "X-Content-Type-Options";
    public static final String REFERRER_POLICY_HEADER_NAME = "Referrer-Policy";
    public static final String SERVER_HEADER_NAME = "Server";

    private static boolean _firstRequestHandled = false;
    private static volatile boolean _sslChecked = false;
    private static SecurityPointcutService _securityPointcut = null;
    private static String _serverHeader = null;


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
                resp.setHeader(X_FRAME_OPTIONS_HEADER_NAME, AppProps.getInstance().getXFrameOption());
            resp.setHeader(X_CONTENT_TYPE_OPTIONS_HEADER_NAME, "nosniff");
            resp.setHeader(REFERRER_POLICY_HEADER_NAME, "origin-when-cross-origin" );

            if (AppProps.getInstance().isIncludeServerHttpHeader())
            {
                if (_serverHeader == null)
                {
                    _serverHeader =  "LabKey/" + AppProps.getInstance().getReleaseVersion();
                }
                resp.setHeader(SERVER_HEADER_NAME, _serverHeader);
            }
        }

        Throwable t = ModuleLoader.getInstance().getStartupFailure();

        if (t != null)
        {
            ExceptionUtil.handleException(req, resp, t, null, true);
            return;
        }

        if (AppProps.getInstance().isSSLRequired())
        {
            // No startup failure, so check for SSL redirection
            if (!req.getScheme().equalsIgnoreCase("https"))
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
                ExceptionUtil.unsafeRedirect(resp, url.toString(), HttpServletResponse.SC_MOVED_PERMANENTLY);
                return;
            }
            else if (!AppProps.getInstance().isDevMode())
            {
                // Issue 51904: Strict-Transport-Security header when HTTPS is required
                // Avoid setting when in dev mode to make it easier to toggle HTTPS on and off again for local deployments
                resp.setHeader(STRICT_TRANSPORT_SECURITY_HEADER_NAME, "max-age=31536000;includeSubdomains");
            }
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
            Pair<User, HttpServletRequest> pair = SecurityManager.attemptAuthentication(req, resp);

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
            user = getGuestUser();
        else
            UserManager.updateRecentUser(user.isImpersonated() ? user.getImpersonatingUser() : user);

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
            UserManager.ensureSessionTracked(user, s);

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

    public static User getGuestUser()
    {
        if (AppProps.getInstance().isOptionalFeatureEnabled(AppProps.EXPERIMENTAL_NO_GUESTS))
            return User.nobody;
        else
            return User.guest;
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
