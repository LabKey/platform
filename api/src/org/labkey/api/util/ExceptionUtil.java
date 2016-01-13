/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.api.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ForbiddenProjectException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.RequestBasicAuthException;
import org.labkey.api.view.TermsOfUseException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartView;
import org.springframework.dao.DataAccessResourceFailureException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * User: rossb
 * Date: Oct 26, 2006
 */
public class ExceptionUtil
{
    private static final JobRunner _jobRunner = new JobRunner(1);
    private static final Logger _logStatic = Logger.getLogger(ExceptionUtil.class);


    private ExceptionUtil()
    {
    }


    public static String renderStackTrace(StackTraceElement[] stackTrace)
    {
        StringBuilder trace = new StringBuilder();

        for (int i = 2; i < stackTrace.length; i++)
        {
            String line = stackTrace[i].toString();
            if (line.startsWith("javax.servlet.http.HttpServlet.service("))
                break;
            trace.append("\n\tat ");
            trace.append(line);
        }

        return trace.toString();
    }


    @NotNull
    public static Throwable unwrapException(@NotNull Throwable ex)
    {
        Throwable cause=ex;

        while (null != cause)
        {
            ex = cause;
            cause = null;

            if (ex.getClass() == RuntimeException.class || ex.getClass() == UnexpectedException.class || ex.getClass() == RuntimeSQLException.class || ex instanceof InvocationTargetException || ex instanceof com.google.gwt.user.server.rpc.UnexpectedException)
            {
                cause = ex.getCause();
            }
            else if (ex.getClass() == ServletException.class && ((ServletException)ex).getRootCause() != null)
            {
                ex = ((ServletException)ex).getRootCause();
            }
            else if (ex instanceof BatchUpdateException)
            {
                cause = ((BatchUpdateException)ex).getNextException();
            }
        }

        return ex;
    }


    public static String renderException(Throwable e)
    {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = PageFlowUtil.filter(sw.toString());
        s = s.replaceAll(" ", "&nbsp;");
        s = s.replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
        return "<pre>\n" + s + "</pre>\n";
    }


    public static String getUnauthorizedMessage(ViewContext context)
    {
        return "<table width=\"100%\"><tr><td align=left>" +
                (context.getUser().isGuest() ? "Please sign in to see this data." : "You do not have permission to see this data.") +
                "</td></tr></table>";
    }


    public static ErrorView getErrorView(int responseStatus, String message, Throwable ex,
                                        HttpServletRequest request, boolean startupFailure)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, false, startupFailure);
        return new ErrorView(renderer, startupFailure);
    }


    public static ErrorView getErrorView(int responseStatus, String message, Throwable ex,
                                        HttpServletRequest request, boolean startupFailure, boolean popup)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, false, startupFailure);
        return new ErrorView(renderer, startupFailure, popup);
    }


    public static WebPartView getErrorWebPartView(int responseStatus, String message, Throwable ex,
                                                  HttpServletRequest request)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, true, false);
        return new WebPartErrorView(renderer);
    }


    public static ErrorRenderer getErrorRenderer(int responseStatus, String message, Throwable ex,
                             @Nullable HttpServletRequest request, boolean isPart, boolean isStartupFailure)
    {
        if (!isStartupFailure && responseStatus == HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            logExceptionToMothership(request, ex);

        if (isPart)
            return new WebPartErrorRenderer(responseStatus, message, ex, isStartupFailure);
        else
            return new ErrorRenderer(responseStatus, message, ex, isStartupFailure);
    }

    private static ExceptionReportingLevel getExceptionReportingLevel()
    {
        // Assume reporting level HIGH during initial install. Admin hasn't made a choice yet plus early exceptions
        // (e.g., before root container is created) will cause AppProps to throw.
        boolean installing = ModuleLoader.getInstance().isUpgradeRequired() && ModuleLoader.getInstance().isNewInstall();
        return installing ? ExceptionReportingLevel.HIGH : AppProps.getInstance().getExceptionReportingLevel();
    }

    private static boolean isSelfReportExceptions()
    {
        // Assume false during initial install, as we likely not far enough along to be able to store the exception
        boolean installing = ModuleLoader.getInstance().isUpgradeRequired() && ModuleLoader.getInstance().isNewInstall();
        return !installing && AppProps.getInstance().isSelfReportExceptions();
    }

    /** @param request may be null if this is coming from a background thread or init */
    public static void logExceptionToMothership(@Nullable HttpServletRequest request, Throwable ex)
    {
        if (ViewServlet.isShuttingDown())
            return;

        ex = unwrapException(ex);

        if (isIgnorable(ex))
            return;

        String requestURL = request == null ? null : (String) request.getAttribute(ViewServlet.ORIGINAL_URL_STRING);
        // Need this extra check to make sure we're not in an infinite loop if there's
        // an exception when trying to submit an exception
        if (requestURL != null && MothershipReport.isMothershipExceptionReport(requestURL))
            return;

        _logStatic.error("Exception detected and logged to mothership", ex);

        // Once to labkey.org, if so configured
        logExceptionToMothership(request, ex, requestURL, false, getExceptionReportingLevel());

        // And once to the local server, if so configured
        if (isSelfReportExceptions())
        {
            logExceptionToMothership(request, ex, requestURL, true, ExceptionReportingLevel.HIGH);
        }
    }

    /** Figure out exactly what text for the stack trace and other details we should submit */
    private static void logExceptionToMothership(HttpServletRequest request, Throwable ex, String requestURL, boolean local, ExceptionReportingLevel level)
    {
        Map<Enum, String> decorations = getExceptionDecorations(ex);

        String exceptionMessage = null;
        if (!decorations.isEmpty() && (level == ExceptionReportingLevel.MEDIUM || level == ExceptionReportingLevel.HIGH))
            exceptionMessage = getExtendedMessage(ex);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter, true);
        ex.printStackTrace(printWriter);
        if (ex instanceof ServletException && ((ServletException)ex).getRootCause() != null)
        {
            printWriter.println("Nested ServletException cause is:");
            ((ServletException)ex).getRootCause().printStackTrace(printWriter);
        }
        String browser = request == null ? null : request.getHeader("User-Agent");

        String stackTrace = stringWriter.getBuffer().toString();
        String sqlState = null;
        for (Throwable t = ex ; t != null ; t = t.getCause())
        {
            if (t instanceof DataAccessResourceFailureException)
            {
                // Don't report exceptions from database connectivity issues
                return;
            }
            if (t instanceof RuntimeSQLException)
            {
                // Unwrap RuntimeSQLExceptions
                t = ((RuntimeSQLException)t).getSQLException();
            }

            if (t instanceof SQLException)
            {
                SQLException sqlException = (SQLException) t;
                if (sqlException.getMessage() != null && sqlException.getMessage().contains("terminating connection due to administrator command"))
                {
                    // Don't report exceptions from Postgres shutting down
                    return;
                }
                sqlState = sqlException.getSQLState();
                String extraInfo = CoreSchema.getInstance().getSqlDialect().getExtraInfo(sqlException);
                if (extraInfo != null)
                {
                    stackTrace = stackTrace + "\n" + extraInfo;
                }
            }

            if (sqlState != null)
                break;
        }

        String referrerURL = null;
        String username = "NOT SET";
        if (request != null)
        {
            referrerURL = request.getHeader("Referer");
            if (request.getUserPrincipal() != null)
            {
                User user = (User)request.getUserPrincipal();
                username = user.getEmail() == null ? "Guest" : user.getEmail();
            }
        }

        reportExceptionToMothership(stackTrace, exceptionMessage, browser, sqlState, requestURL, referrerURL, username, local, level);
    }

    /**
     * This has been separated from logExceptionToMothership() in order to provide more verbose server-side logging of client context
     */
    public static void logClientExceptionToMothership(
            String stackTrace,
            String exceptionMessage,
            String browser,
            String sqlState,
            String requestURL,
            String referrerURL,
            String username)
    {
        _logStatic.error("Client exception detected and logged to mothership:\n" +
            requestURL + "\n" +
            referrerURL + "\n" +
            browser + "\n" +
            stackTrace
        );

        // Once to labkey.org, if so configured
        reportExceptionToMothership(stackTrace, exceptionMessage, browser, sqlState, requestURL, referrerURL, username, false, getExceptionReportingLevel());

        // And once to the local server, if so configured
        if (isSelfReportExceptions())
        {
            reportExceptionToMothership(stackTrace, exceptionMessage, browser, sqlState, requestURL, referrerURL, username, true, ExceptionReportingLevel.HIGH);
        }
    }

    private static void reportExceptionToMothership(
            String stackTrace,
            String exceptionMessage,
            String browser,
            String sqlState,
            String requestURL,
            String referrerURL,
            String username,
            boolean local,
            ExceptionReportingLevel level)
    {
        if (level == ExceptionReportingLevel.NONE)
            return;

        if (local && ModuleLoader.getInstance().isUpgradeInProgress())
        {
            _logStatic.error("Not logging exception to local mothership because upgrade is in progress");
            return;
        }

        try
        {
            MothershipReport report = new MothershipReport(MothershipReport.Type.ReportException, local);
            report.addServerSessionParams();
            report.addParam("stackTrace", stackTrace);
            report.addParam("sqlState", sqlState);
            report.addParam("browser", browser);

            if (requestURL != null)
            {
                try
                {
                    ActionURL url = new ActionURL(requestURL);
                    report.addParam("pageflowName", url.getController());
                    report.addParam("pageflowAction", url.getAction());
                }
                catch (IllegalArgumentException x)
                {
                    // fall through
                }
            }

            if (level == ExceptionReportingLevel.MEDIUM || level == ExceptionReportingLevel.HIGH)
            {
                report.addParam("exceptionMessage", exceptionMessage);
                report.addParam("requestURL", requestURL);
                report.addParam("referrerURL", referrerURL);

                if (level == ExceptionReportingLevel.HIGH)
                {
                    if (username == null)
                        username = "NOT SET";
                    report.addParam("username", username);
                }
            }

            _jobRunner.execute(report);
        }
        catch (MalformedURLException | URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static boolean isIgnorable(Throwable ex)
    {
        Map<Enum, String> decorations = getExceptionDecorations(ex);
        
        return ex == null ||
                null != decorations.get(ExceptionInfo.SkipMothershipLogging) ||
                ex instanceof SkipMothershipLogging ||
                isClientAbortException(ex) ||
                _jobRunner.getJobCount() > 10;
    }

    static class WebPartErrorView extends WebPartView
    {
        private final ErrorRenderer _renderer;

        WebPartErrorView(ErrorRenderer renderer)
        {
            super();
            _renderer = renderer;
        }

        @Override
        protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            PrintWriter out = response.getWriter();
            _renderer.renderStart(out);
            _renderer.renderContent(out, request, null);
            _renderer.renderEnd(out);
        }
    }


    public static boolean isClientAbortException(Throwable ex)
    {
        if (ex != null)
        {
            String className = ex.getClass().getName();
            if (className.endsWith("SocketTimeoutException") ||
                className.endsWith("CancelledException") ||
                className.endsWith("ClientAbortException") ||
                className.endsWith("FileUploadException"))
            {
                return true;
            }
            if (ex.getClass().equals(IllegalStateException.class) && "Cannot create a session after the response has been committed".equals(ex.getMessage()))
            {
                return true;
            }
            if (ex.getClass().equals(SocketException.class) && "Connection reset".equalsIgnoreCase(ex.getMessage()))
            {
                return true;
            }
            // Bug 15371
            if (ex.getClass().equals(IOException.class) && ex.getMessage() != null && ex.getMessage().contains("disconnected client"))
            {
                return true;
            }

            // Recurse to see if the root exception is a client abort exception
            if (ex.getCause() != ex)
            {
                return isClientAbortException(ex.getCause());
            }
        }
        return false;
    }

    // This is called by SpringActionController (to display unhandled exceptions) and called directly by AuthFilter.doFilter() (to display startup errors and bypass normal request handling)
    public static ActionURL handleException(HttpServletRequest request, HttpServletResponse response, Throwable ex, @Nullable String message, boolean startupFailure)
    {
        SearchService ss = ServiceRegistry.get(SearchService.class);
        Logger log = _logStatic;
        return handleException(request, response, ex, message, startupFailure, ss, log);
    }

    static ActionURL handleException(HttpServletRequest request, HttpServletResponse response, Throwable ex, @Nullable String message, boolean startupFailure,
        SearchService ss, Logger log)
    {
        try
        {
            DbScope.closeAllConnectionsForCurrentThread();
        }
        catch (Throwable t)
        {
            // This will fail if, for example, we're dealing with a startup exception
        }

        // First, get rid of RuntimeException, InvocationTargetException, etc. wrappers
        ex = unwrapException(ex);

        // unhandledException indicates whether the exception is expected or not
        // assume it is unhandled and clear for Unauthorized, NotFound, etc
        Throwable unhandledException = ex;
        String responseStatusMessage = null;    // set to use non default status message

        if (isClientAbortException(ex))
        {
            // The client dropped the connection. We don't care about this case,
            // and don't need to send an error back to the browser either.
            return null;
        }

        int responseStatus = HttpServletResponse.SC_OK;

        if (response.isCommitted())
        {
            // if we can't reset(), flushing might make it slightly less likely to send half-written attributes etc
            try {response.getOutputStream().flush();} catch (Exception ignored) {}
            try {response.getWriter().flush();} catch (Exception ignored) {}
            try {response.flushBuffer();} catch (Exception ignored) {}
        }

        // Do redirects before response.reset() otherwise we'll lose cookies (e.g., login page)
        if (ex instanceof RedirectException)
        {
            String url = ((RedirectException) ex).getURL();
            doErrorRedirect(response, url);
            return null;
        }

        if (!response.isCommitted())
        {
            try
            {
                response.reset();
            }
            catch (IllegalStateException x)
            {
                // This is fine, just can't clear the existing response as its
                // been at least partially written back to the client
            }
        }

        User user = (User) request.getUserPrincipal();
        boolean isGET = "GET".equals(request.getMethod());

        ErrorView errorView;
        Map<String, String> headers = new TreeMap<>();

        if (ViewServlet.isShuttingDown())
        {
            try
            {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "The server is shutting down");
            }
            catch (IOException e)
            {
                // Do nothing
            }
            return null;
        }
        else if (ex instanceof NotFoundException)
        {
            responseStatus = HttpServletResponse.SC_NOT_FOUND;
            if (ex.getMessage() != null)
            {
                message = ex.getMessage();
                responseStatusMessage = message;
            }
            else
                message = responseStatus + ": Page not Found";

            URLHelper url = (URLHelper)request.getAttribute(ViewServlet.ORIGINAL_URL_URLHELPER);
            if (null != url && null != url.getParameter("_docid"))
            {
                if (null != ss)
                    ss.notFound(url);
            }
            unhandledException = null;
        }
        else if (ex instanceof UnauthorizedException)
        {
            UnauthorizedException uae = (UnauthorizedException) ex;

            // This header allows for requests to explictly ask to not get basic auth headers back
            // useful for when the page wants to handle 401's itself
            String headerHint = request.getHeader("X-ONUNAUTHORIZED");

            boolean isGuest = user.isGuest();
            UnauthorizedException.Type type = uae.getType();
            boolean overrideBasicAuth = "UNAUTHORIZED".equals(headerHint);
            boolean isCSRFViolation = uae instanceof CSRFException;
            boolean isTermsOfUseViolation = uae instanceof TermsOfUseException;

            // check for redirect to login.jsp -- unauthorized guest
            if (isGET)
            {
                // If user has not logged in or agreed to terms, not really unauthorized yet...
                if (!isCSRFViolation && (isGuest || isTermsOfUseViolation) && type == UnauthorizedException.Type.redirectToLogin && !overrideBasicAuth)
                {
                    ActionURL redirect;

                    if (isTermsOfUseViolation)
                    {
                        redirect = PageFlowUtil.urlProvider(LoginUrls.class).getAgreeToTermsURL(HttpView.getContextContainer(), HttpView.getContextURLHelper());
                    }
                    else
                    {
                        redirect = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(HttpView.getContextContainer(), HttpView.getContextURLHelper());
                    }
                    return redirect;
                }
            }

            // we know who you are, you're just forbidden from seeing it (unless bad CSRF, silly kids)
            responseStatus = isGuest || isCSRFViolation ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN;

            message = ex.getMessage();
            responseStatusMessage = message;

            if (isGuest && type == UnauthorizedException.Type.sendBasicAuth && !overrideBasicAuth)
            {
                headers.put("WWW-Authenticate", "Basic realm=\"" + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription() + "\"");

                if (isGET)
                    message = "You must log in to view this content.";
            }

            unhandledException = null;
        }
        else if (ex instanceof SQLException)
        {
            responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = SqlDialect.GENERIC_ERROR_MESSAGE;

            if (ex instanceof BatchUpdateException)
            {
                if (null != ((BatchUpdateException)ex).getNextException())
                    ex = ((BatchUpdateException)ex).getNextException();
                unhandledException = ex;
            }
        }

        if (null == message && null != unhandledException)
        {
            responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = responseStatus + ": Unexpected server error";
        }

        errorView = ExceptionUtil.getErrorView(responseStatus, message, unhandledException, request, startupFailure);

        //don't log unauthorized (basic-auth challenge), forbiddens, or simple not found (404s)
        if (responseStatus != HttpServletResponse.SC_UNAUTHORIZED &&
                responseStatus != HttpServletResponse.SC_FORBIDDEN &&
                responseStatus != HttpServletResponse.SC_NOT_FOUND)
        {
            log.error("Unhandled exception: " + (null == message ? "" : message), ex);
        }

        if (ex instanceof UnauthorizedException)
        {
            if (ex instanceof ForbiddenProjectException)
            {
                // Not allowed in the project... don't offer Home or Folder buttons
                errorView.setIncludeHomeButton(false);
                errorView.setIncludeFolderButton(false);
            }

            // Provide "Stop Impersonating" button if unauthorized while impersonating
            if (user.isImpersonated())
                errorView.setIncludeStopImpersonatingButton(true);
        }

        if (response.isCommitted())
        {
            // This is fine, just can't clear the existing response as it has
            // been at least partially written back to the client

            if (ex != null)
            {
                try
                {
                    response.getWriter().println("\"> --></script>");
                    response.getWriter().println();
                    response.getWriter().println();
                    response.getWriter().println("<pre>");
                    ex.printStackTrace(response.getWriter());
                    response.getWriter().println("</pre>");
                }
                catch (IOException | IllegalStateException e)
                {
                    // Give up at this point
                }
            }
        }
        else
        {
            try
            {
                response.reset();
                response.setContentType("text/html");
                if (null == responseStatusMessage)
                    response.setStatus(responseStatus);
                else
                    response.setStatus(responseStatus, responseStatusMessage);
                for (Map.Entry<String, String> entry : headers.entrySet())
                    response.addHeader(entry.getKey(), entry.getValue());
                errorView.render(request, response);
                return null;
            }
            catch (IllegalStateException ignored)
            {
            }
            catch (Exception x)
            {
                log.error("Global.handleException", x);
            }
        }

        return null;
    }


    public static void doErrorRedirect(HttpServletResponse response, String url)
    {
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        response.setDateHeader("Expires", 0);
        response.setHeader("Location", url);
        response.setContentType("text/html; charset=UTF-8");

        // backup strategy!
        try
        {
            PrintWriter out = response.getWriter();
            out.println("\"'>--></script><script type=\"text/javascript\">");
            out.println("window.location = '" + url + "';");
            out.println("</script>");
        }
        catch (IOException x)
        {
            _logStatic.error("doErrorRedirect", x);
        }
    }



    public enum ExceptionInfo
    {
        ResolveURL,     // suggestion for where to fix this e.g. sourceQuery.view
        ResolveText,    // text to go with the ResolveURL
        HelpURL,
        DialectSQL,
        LabkeySQL,
        QueryName,
        QuerySchema,
        SkipMothershipLogging,
        ExtraMessage
    }


    private final static WeakHashMap<Throwable, HashMap<Enum, String>> _exceptionDecorations = new WeakHashMap<>();
    
    public static boolean decorateException(Throwable t, Enum key, String value, boolean overwrite)
    {
        t = unwrapException(t);
        synchronized (_exceptionDecorations)
        {
            HashMap<Enum, String> m = _exceptionDecorations.get(t);
            if (null == m)
                _exceptionDecorations.put(t, m = new HashMap<>());
            if (overwrite || !m.containsKey(key))
            {
                _logStatic.debug("add decoration to " + t.getClass() + "@" + System.identityHashCode(t) + " " + key + "=" + value);
                m.put(key,value);
                return true;
            }
        }
        return false;
    }


    @NotNull
    public static Map<Enum, String> getExceptionDecorations(Throwable start)
    {
        HashMap<Enum, String> collect = new HashMap<>();
        LinkedList<Throwable> list = new LinkedList<>();

        Throwable next = unwrapException(start);
        while (null != next)
        {
            list.addFirst(next);
            next = getCause(next);
        }

        synchronized (_exceptionDecorations)
        {
            for (Throwable th : list)
            {
                HashMap<Enum, String> m = _exceptionDecorations.get(th);
                if (null != m)
                    collect.putAll(m);
            }
        }
        return collect;
    }


    @Nullable
    public static String getExceptionDecoration(Throwable t, Enum e)
    {
        // could optimize...
        return getExceptionDecorations(t).get(e);
    }


    @NotNull
    public static String getExtendedMessage(Throwable t)
    {
        StringBuilder sb = new StringBuilder(t.toString());
        for (Map.Entry<Enum, String> e : getExceptionDecorations(t).entrySet())
            sb.append("\n").append(e.getKey()).append("=").append(e.getValue());
        return sb.toString();
    }


    @Nullable
    public static Throwable getCause(Throwable t)
    {
        Throwable cause;
        if (t instanceof RuntimeSQLException)
            cause = ((RuntimeSQLException)t).getSQLException();
        else if (t instanceof ServletException)
            cause = ((ServletException)t).getRootCause();
        else if (t instanceof BatchUpdateException)
            cause = ((BatchUpdateException)t).getNextException();
        else
            cause = t.getCause();
        return cause==t ? null : cause;
    }


    static class ExceptionResponse
    {
        ActionURL redirect;
        MockServletResponse response;
        String body;
    }

    public static class TestCase extends Assert
    {
        ExceptionResponse handleIt(final User user, Exception ex, @Nullable String message)
        {
            final MockServletResponse res = new MockServletResponse();
            InvocationHandler h = (o, method, objects) -> {
                // still calls in 'headers' for validation
                res.addHeader(method.getDeclaringClass().getSimpleName() + "." + method.getName(), objects.length==0 ? "" : objects.length==1 ? String.valueOf(objects[0]) : objects.toString());
                return null;
            };
            SearchService dummySearch = (SearchService) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{SearchService.class}, h);
            Logger dummyLog = new Logger("mock logger")
            {
                @Override
                public void debug(Object message)
                {
                }
                @Override
                public void debug(Object message, Throwable t)
                {
                }
                @Override
                public void error(Object message)
                {
                }
                @Override
                public void error(Object message, Throwable t)
                {
                    res.addHeader("Logger.error", null!=message?String.valueOf(message):null!=t?t.getMessage():"");
                }
                @Override
                public void fatal(Object message)
                {
                }
                @Override
                public void fatal(Object message, Throwable t)
                {
                }
                @Override
                public void warn(Object message)
                {
                }
                @Override
                public void warn(Object message, Throwable t)
                {
                }
            };
            HttpServletRequestWrapper req = new HttpServletRequestWrapper(TestContext.get().getRequest())
            {
                @Override
                public Principal getUserPrincipal()
                {
                    return user;
                }
            };
            ExceptionUtil.decorateException(ex, ExceptionInfo.SkipMothershipLogging, "true", true);
            ActionURL url = ExceptionUtil.handleException(req, res, ex, message, false, dummySearch, dummyLog);
            ExceptionResponse ret = new ExceptionResponse();
            ret.redirect = url;
            ret.response = res;
            ret.body = res.getBodyAsText();
            return ret;
        }


        @Test
        public void testUnauthorized()
        {
            User guest = UserManager.getGuestUser();
            User me = TestContext.get().getUser();
            ExceptionResponse answer;

            // Guest Unauthorized
            answer = handleIt(guest, new UnauthorizedException("Not on my watch"), null);
            assertNotNull("expect return url for login redirect", answer.redirect);
            assertEquals(0, answer.response.status);  // status not set

            // Non-Guest Unauthorized
            answer = handleIt(me, new UnauthorizedException("Not on my watch"), null);
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_FORBIDDEN, answer.response.status);

            // Guest Basic Unauthorized
            answer = handleIt(guest, new RequestBasicAuthException(), null);
            assertNull("BasicAuth should not redirect", answer.redirect);
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, answer.response.status);
            assertTrue(answer.response.headers.containsKey("WWW-Authenticate"));

            // Non-Guest Basic Unauthorized
            answer = handleIt(me, new RequestBasicAuthException(), null);
            assertNull("BasicAuth should not redirect", answer.redirect);
            assertEquals(HttpServletResponse.SC_FORBIDDEN, answer.response.status);
            assertFalse(answer.response.headers.containsKey("WWW-Authenticate"));

            // Guest TermsOfUse
            answer = handleIt(guest, new TermsOfUseException(), null);
            assertNotNull("expect return url for terms of use redirect", answer.redirect);
            assertEquals(0, answer.response.status);  // status not set

            // Non-Guest TermsOfUse
            answer = handleIt(me, new TermsOfUseException(), null);
            assertNotNull("expect return url for terms of use redirect", answer.redirect);
            assertEquals(0, answer.response.status);  // status not set

            // Guest CSRF
            answer = handleIt(guest, new CSRFException(), null);
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, answer.response.status);

            // Non-Guest CSRF
            answer = handleIt(me, new CSRFException(), null);
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, answer.response.status);
        }

        @Test
        public void testRedirect()
        {
            User guest = UserManager.getGuestUser();
            User me = TestContext.get().getUser();
            ExceptionResponse answer;

            ActionURL url = new ActionURL("controller", "action", JunitUtil.getTestContainer());
            answer = handleIt(guest, new RedirectException(url), null);
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, answer.response.status);
            assertTrue(answer.response.headers.containsKey("Location"));
        }

        @Test
        public void testNotFound()
        {
            User guest = UserManager.getGuestUser();
            User me = TestContext.get().getUser();
            ExceptionResponse answer;

            answer = handleIt(guest, new NotFoundException("Not here"), null);
            assertNull("not found does not redirect", answer.redirect);
            assertEquals(HttpServletResponse.SC_NOT_FOUND, answer.response.status);
            assertTrue(answer.body.contains("Not here"));

            // simulate a search result not found
            HttpServletRequest req = TestContext.get().getRequest();
            ActionURL orig = new ActionURL("controller", "action", JunitUtil.getTestContainer());
            orig.addParameter("_docid", "fred");
            req.setAttribute(ViewServlet.ORIGINAL_URL_URLHELPER, orig);

            answer = handleIt(guest, new NotFoundException("Not here"), null);
            assertNull("not found does not redirect", answer.redirect);
            assertEquals(HttpServletResponse.SC_NOT_FOUND, answer.response.status);
            assertTrue(answer.body.contains("Not here"));
            assertTrue(answer.response.headers.containsKey("SearchService.notFound"));
        }

        @Test
        public void testServerError()
        {
            User me = TestContext.get().getUser();
            ExceptionResponse answer;

            answer = handleIt(me, new NullPointerException(), null);
            assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, answer.response.status);
            assertTrue(answer.response.headers.containsKey("Logger.error"));
        }

        @Test
        public void testUnwrap()
        {

        }
    }
}
