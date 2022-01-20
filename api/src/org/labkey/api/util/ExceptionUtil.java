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

package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.RequestBasicAuthException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.DavException;
import org.springframework.dao.DataAccessResourceFailureException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * User: rossb
 * Date: Oct 26, 2006
 */
public class ExceptionUtil
{
    public static final String REQUEST_EXCEPTION_ATTRIBUTE = ExceptionUtil.class.getName() + "$exception";

    private static final JobRunner JOB_RUNNER = new JobRunner("Mothership Reporting", 1);
    private static final Logger LOG = LogHelper.getLogger(ExceptionUtil.class, "Handles rendering of errors during requests");
    // Allow 10 report submissions to mothership per minute
    private static final RateLimiter _reportingRateLimiter = new RateLimiter("exception reporting", 10, TimeUnit.MINUTES);

    private ExceptionUtil()
    {
    }


    public static String renderStackTrace(@Nullable StackTraceElement[] stackTrace)
    {
        if (stackTrace == null)
        {
            return MiniProfiler.NO_STACK_TRACE_AVAILABLE;
        }
        StringBuilder trace = new StringBuilder();

        for (int i = 2; i < stackTrace.length; i++)
        {
            String line = String.valueOf(stackTrace[i]);
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


    public static HtmlString renderException(Throwable e)
    {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = PageFlowUtil.filter(sw.toString());
        s = s.replaceAll(" ", "&nbsp;");
        s = s.replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
        return HtmlString.unsafe("<pre class='exception-stacktrace'>\n" + s + "</pre>\n");
    }


    public static HtmlString getUnauthorizedMessage(ViewContext context)
    {
        return HtmlString.unsafe("<table width=\"100%\"><tr><td align=left>" +
                (context.getUser().isGuest() ? "Please sign in to see this data." : "You do not have permission to see this data.") +
                "</td></tr></table>");
    }

    public static WebPartView<?> getErrorWebPartView(int responseStatus, String message, Throwable ex,
                                                  HttpServletRequest request)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, true, false);
        return new WebPartErrorView(renderer);
    }


    public static ErrorRenderer getErrorRenderer(int responseStatus, String message, Throwable ex,
                             @Nullable HttpServletRequest request, boolean isPart, boolean isStartupFailure)
    {
        String errorCode = null;
        if (!isStartupFailure && responseStatus == HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        {
            errorCode = logExceptionToMothership(request, ex);
        }

        if (isPart)
            return new WebPartErrorRenderer(responseStatus, errorCode, message, ex, isStartupFailure);
        else
            return new ErrorRenderer(responseStatus, errorCode, message, ex, isStartupFailure);
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

    /** @param request may be null if this is coming from a background thread or init  */
    public static String logExceptionToMothership(@Nullable HttpServletRequest request, Throwable ex)
    {
        return logExceptionToMothership(request, ex, true);
    }

    /** @param request may be null if this is coming from a background thread or init  */
    public static String logExceptionToMothership(@Nullable HttpServletRequest request, Throwable ex, boolean writeToLog4J)
    {
        if (ViewServlet.isShuttingDown())
            return null;

        ex = unwrapException(ex);

        if (isIgnorable(ex))
            return null;

        String requestURL = request == null ? null : (String) request.getAttribute(ViewServlet.ORIGINAL_URL_STRING);
        // Need this extra check to make sure we're not in an infinite loop if there's
        // an exception when trying to submit an exception
        if (requestURL != null && MothershipReport.isMothershipExceptionReport(requestURL))
            return null;

        String extraInfo = "";
        String sqlState = null;
        for (Throwable t = ex ; t != null ; t = t.getCause())
        {
            if (t instanceof DataAccessResourceFailureException)
            {
                // Don't report exceptions from database connectivity issues
                return null;
            }
            if (t instanceof RuntimeSQLException)
            {
                // Unwrap RuntimeSQLExceptions
                t = ((RuntimeSQLException)t).getSQLException();
            }

            if (t instanceof SQLException sqlException)
            {
                if (sqlException.getMessage() != null && sqlException.getMessage().contains("terminating connection due to administrator command"))
                {
                    // Don't report exceptions from Postgres shutting down
                    return null;
                }
                sqlState = sqlException.getSQLState();
                String extraSqlInfo = CoreSchema.getInstance().getSqlDialect().getExtraInfo(sqlException);
                if (extraSqlInfo != null)
                {
                    extraInfo = extraSqlInfo;
                }
            }

            if (t instanceof DeadlockPreventingException)
            {
                String extraSqlInfo = CoreSchema.getInstance().getSqlDialect().getOtherDatabaseThreads();
                if (extraSqlInfo != null)
                {
                    extraInfo = extraSqlInfo;
                }
            }

            if (sqlState != null)
                break;
        }

        String errorCode = null;
        try
        {
            // Once to labkey.org, if so configured
            errorCode = sendReport(createReportFromThrowable(request, ex, requestURL, MothershipReport.Target.remote, getExceptionReportingLevel(), null, sqlState, extraInfo));

            // And once to the local server, if so configured. If submitting to both labkey.org and the local server, the errorCode will be the same.
            if (isSelfReportExceptions())
            {
                String newErrorCode = sendReport(createReportFromThrowable(request, ex, requestURL, MothershipReport.Target.local, ExceptionReportingLevel.HIGH, errorCode, sqlState, extraInfo));
                if (null == errorCode)
                    errorCode = newErrorCode; // which may still be null, if server is configured to not send reports to either location.
            }
        }
        finally
        {
            if (writeToLog4J)
            {
                String message = "Exception detected";
                if (null != errorCode)
                    message += " and logged to mothership with error code: " + errorCode;
                LOG.error(message, ex);
                String decorations = getExtendedMessage(ex);

                if (!extraInfo.isBlank() || !decorations.isBlank())
                {
                    String logMessage = "Additional exception info:";
                    if (!extraInfo.isBlank())
                    {
                        logMessage += "\n" + extraInfo;
                    }
                    if (!decorations.isBlank())
                    {
                        logMessage += "\n" + decorations;
                    }
                    if (HttpView.hasCurrentView())
                    {
                        ViewContext viewContext = HttpView.currentContext();
                        logMessage += "\nCurrent URL: " + viewContext.getActionURL();
                        if (null != viewContext.getUser())
                        {
                            logMessage += "\nCurrent user: " + (viewContext.getUser().isGuest() ? "Guest" : viewContext.getUser().getEmail());
                        }
                    }
                    LOG.error(logMessage);
                }
            }
        }

        return errorCode;
    }

    private static String sendReport(MothershipReport report)
    {
        if (null != report)
        {
            JOB_RUNNER.execute(report);
            return report.getErrorCode();
        }
        return null;
    }

    /** Figure out exactly what text for the stack trace and other details we should submit */
    public static MothershipReport createReportFromThrowable(@Nullable HttpServletRequest request, Throwable ex, String requestURL, MothershipReport.Target target, ExceptionReportingLevel level, @Nullable String errorCode, @Nullable String sqlState, @Nullable String extraInfo)
    {
        if (!shouldSend(level, target.isLocal()))
            return null;

        Map<Enum<?>, String> decorations = getExceptionDecorations(ex);

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
        if (extraInfo != null)
        {
            stackTrace += "\n" + extraInfo;
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

        return createReportFromStacktrace(stackTrace, exceptionMessage, browser, sqlState, requestURL, referrerURL, username, target, level, errorCode);
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
        String errorCode = null;
        try
        {
            // Once to labkey.org, if so configured
            ExceptionReportingLevel level = getExceptionReportingLevel();
            if (shouldSend(level, false))
            {
                errorCode = sendReport(createReportFromStacktrace(stackTrace, exceptionMessage, browser, sqlState, requestURL, referrerURL, username, MothershipReport.Target.remote, level, null));
            }

            // And once to the local server, if so configured
            if (isSelfReportExceptions() && shouldSend(ExceptionReportingLevel.HIGH, true))
            {
                String newErrorCode = sendReport(createReportFromStacktrace(stackTrace, exceptionMessage, browser, sqlState, requestURL, referrerURL, username, MothershipReport.Target.local, ExceptionReportingLevel.HIGH, errorCode));
                if (null == errorCode)
                    errorCode = newErrorCode;
            }
        }
        finally
        {
            String message = "Client exception detected";
            if (null != errorCode)
            {
                message += " and logged to mothership with error code: ";
            }
            LOG.error(message + "\n" +
                    requestURL + "\n" +
                    referrerURL + "\n" +
                    browser + "\n" +
                    stackTrace
            );
        }
    }

    private static boolean shouldSend(ExceptionReportingLevel level, boolean local)
    {
        boolean send = true;
        
        if (level == ExceptionReportingLevel.NONE)
        {
            send = false;
        }
        else if (local && ModuleLoader.getInstance().isUpgradeInProgress())
        {
            LOG.error("Not logging exception to local mothership because upgrade is in progress");
            send = false;
        }
        // In dev mode, don't report to labkey.org if the Mothership module is installed.
        else if (!local && AppProps.getInstance().isDevMode() && MothershipReport.isShowSelfReportExceptions())
        {
            send = false;
        }
        else if (!local && _reportingRateLimiter.add(1, false) > 0)
        {
            MothershipReport.incrementDroppedExceptionCount();
            send = false;
        }

        return send;
    }

    private static MothershipReport createReportFromStacktrace(String stackTrace, String exceptionMessage, String browser, String sqlState, String requestURL, String referrerURL, String username, MothershipReport.Target target, ExceptionReportingLevel level, @Nullable String errorCode)
    {
        try
        {
            MothershipReport report = new MothershipReport(MothershipReport.Type.ReportException, target, errorCode);
            report.addServerSessionParams();
            report.addParam("stackTrace", stackTrace);
            report.addParam("sqlState", sqlState);
            report.addParam("browser", browser);

            Map<String, Map<String, Object>> modulesMap = new HashMap<>();
            UsageReportingLevel.putModulesBuildInfo(modulesMap);
            report.setMetrics(Collections.singletonMap("modules", modulesMap));

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
                report.addHostName();

                if (level == ExceptionReportingLevel.HIGH)
                {
                    if (username == null)
                        username = "NOT SET";
                    report.addParam("username", username);
                }
            }

            return report;
        }
        catch (MalformedURLException | URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static boolean isIgnorable(Throwable ex)
    {
        Map<Enum<?>, String> decorations = getExceptionDecorations(ex);
        
        return ex == null ||
                null != decorations.get(ExceptionInfo.SkipMothershipLogging) ||
                ex instanceof SkipMothershipLogging ||
                isClientAbortException(ex) ||
                (ex instanceof IllegalStateException && "Page needs a session and none is available".equalsIgnoreCase(ex.getMessage())) ||
                JOB_RUNNER.getJobCount() > 10;
    }

    static class WebPartErrorView extends WebPartView<Object>
    {
        private final ErrorRenderer _renderer;

        WebPartErrorView(ErrorRenderer renderer)
        {
            super(FrameType.DIV);
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
            if (ex.getClass().equals(IllegalStateException.class) && ex.getMessage() != null &&
                    ("Cannot create a session after the response has been committed".equals(ex.getMessage()) ||
                        "Cannot call sendError() after the response has been committed".equals(ex.getMessage()) ||
                            ex.getMessage().contains("Session already invalidated")))

            {
                return true;
            }
            if (ex.getClass().equals(SocketException.class) && "Connection reset".equalsIgnoreCase(ex.getMessage()))
            {
                return true;
            }
            // Bug 15371 and 34605
            if (ex.getClass().equals(IOException.class) && ex.getMessage() != null && (ex.getMessage().contains("disconnected client") || ex.getMessage().contains("Socket read failed")))
            {
                return true;
            }
            // Bug 32056
            if (ex.getClass().equals(EOFException.class))
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

    public static ActionURL handleException(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, Throwable ex, @Nullable String message, boolean startupFailure)
    {
        return handleException(request, response, ex, message, startupFailure, SearchService.get(), LOG, null);
    }

    // This is called by SpringActionController (to display unhandled exceptions) and called directly by AuthFilter.doFilter() (to display startup errors and bypass normal request handling)
    public static ActionURL handleException(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, Throwable ex, @Nullable String message, boolean startupFailure, @Nullable PageConfig pageConfig)
    {
        return handleException(request, response, ex, message, startupFailure, SearchService.get(), LOG, pageConfig);
    }

    static ActionURL handleException(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, Throwable ex, @Nullable String message, boolean startupFailure,
        SearchService ss, Logger log, @Nullable PageConfig pageConfig)
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
        request.setAttribute(REQUEST_EXCEPTION_ATTRIBUTE, ex);

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
        ErrorRenderer.ErrorType errorType = ErrorRenderer.ErrorType.execution;

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
                // mostly to make security scanners happy
                if (ModuleLoader.getInstance().isStartupComplete())
                {
                    if (!"ALLOW".equals(AppProps.getInstance().getXFrameOptions()))
                        response.setHeader("X-Frame-Options", AppProps.getInstance().getXFrameOptions());
                    response.setHeader("X-XSS-Protection", "1; mode=block");
                    response.setHeader("X-Content-Type-Options", "nosniff");
                }
            }
            catch (IllegalStateException x)
            {
                // This is fine, just can't clear the existing response as its
                // been at least partially written back to the client
            }
        }

        User user = (User) request.getUserPrincipal();
        boolean isGET = "GET".equals(request.getMethod());
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
        else if (ex instanceof ApiUsageException)
        {
            responseStatus = HttpServletResponse.SC_BAD_REQUEST;
            errorType = ErrorRenderer.ErrorType.notFound;
            if (ex.getMessage() != null)
            {
                message = ex.getMessage();
                responseStatusMessage = message;
            }
            else
                message = responseStatus + ": API usage error - bad request";
        }
        else if (ex instanceof BadRequestException)
        {
            responseStatus = ((BadRequestException) ex).getStatus();
            errorType = ErrorRenderer.ErrorType.notFound;
            message = ex.getMessage();
            unhandledException = null;
        }
        else if (ex instanceof NotFoundException)
        {
            responseStatus = HttpServletResponse.SC_NOT_FOUND;
            errorType = ErrorRenderer.ErrorType.notFound;
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
        else if (ex instanceof UnauthorizedException uae)
        {
            // This header allows for requests to explicitly ask to not get basic auth headers back
            // useful for when the page wants to handle 401s itself
            String headerHint = request.getHeader("X-ONUNAUTHORIZED");

            boolean isGuest = user.isGuest();
            UnauthorizedException.Type type = uae.getType();
            boolean overrideBasicAuth = "UNAUTHORIZED".equals(headerHint);
            boolean isCSRFViolation = uae instanceof CSRFException;

            // check for redirect to login page -- unauthorized guest
            if (isGET)
            {
                // If user has not logged in or agreed to terms, not really unauthorized yet...
                if (!isCSRFViolation && isGuest && type == UnauthorizedException.Type.redirectToLogin && !overrideBasicAuth)
                {
                    // Issue 43307: If this is a locked project then just show the login page in the root. Register,
                    // forgot my password, profile update, password reset, etc. aren't going to work right in a locked
                    // project.
                    Container c = HttpView.getContextContainer();
                    if (c.getLockState().isLocked())
                        c = ContainerManager.getRoot();

                    // Issue 43387 - Retain original container info on login redirect URL, even if it resolved to something else
                    ActionURL loginURL = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(c, HttpView.getContextURLHelper());
                    Path originalContainerPath = (Path)request.getAttribute(ViewServlet.ORIGINAL_URL_CONTAINER_PATH);
                    if (originalContainerPath != null)
                    {
                        loginURL.setExtraPath(originalContainerPath.toString("/", ""));
                    }
                    return loginURL;
                }
            }

            // we know who you are, you're just forbidden from seeing it (unless bad CSRF, silly kids)
            responseStatus = isGuest || isCSRFViolation ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN;
            errorType = ErrorRenderer.ErrorType.permission;

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
        else if (ex instanceof ConfigurationException)
        {
            errorType = ErrorRenderer.ErrorType.configuration;
        }

        if (null == message && null != unhandledException)
        {
            responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = ex.getMessage();
        }

        //don't log unauthorized (basic-auth challenge), forbiddens, or simple not found (404s)
        if (null != unhandledException && responseStatus != HttpServletResponse.SC_BAD_REQUEST)
        {
            log.error("Unhandled exception: " + message, unhandledException);
        }

        ApiResponseWriter.Format responseFormat = ApiResponseWriter.getResponseFormat(request, null);

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
        else if (responseFormat != null)
        {
            try
            {
                response.setContentType(request.getContentType());
                response.setCharacterEncoding("utf-8");

                if (null == responseStatusMessage)
                    response.setStatus(responseStatus);
                else
                    response.setStatus(responseStatus, responseStatusMessage);

                for (Map.Entry<String, String> entry : headers.entrySet())
                    response.addHeader(entry.getKey(), entry.getValue());

                ApiSimpleResponse errorResponse = new ApiSimpleResponse("success", false);

                if (responseStatusMessage != null || message != null)
                    errorResponse.put("exception", StringUtils.defaultString(message,responseStatusMessage));

                ApiResponseWriter writer = responseFormat.createWriter(response, null, null);

                errorResponse.render(writer);
                writer.close();
            }
            catch (Exception x)
            {
                log.error("Global.handleException", x);
            }
        }
        else
        {
            response.setContentType("text/html");
            response.setStatus(responseStatus);
            for (Map.Entry<String, String> entry : headers.entrySet())
                response.addHeader(entry.getKey(), entry.getValue());
            renderErrorPage(ex, responseStatus, message, request, response, pageConfig, errorType, log, startupFailure);
        }

        return null;
    }

    private static void renderErrorPage(Throwable ex, int responseStatus, String message, HttpServletRequest request, HttpServletResponse response, PageConfig originalConfig, ErrorRenderer.ErrorType errorType, Logger log, boolean startupFailure)
    {
        ErrorRenderer renderer = getErrorRenderer(responseStatus, message, ex, request, false, startupFailure);

        if (ex instanceof UnauthorizedException)
        {
           errorType = ErrorRenderer.ErrorType.permission;
        }

        if (ex instanceof DavException)
        {
            errorType = ErrorRenderer.ErrorType.notFound;
        }

        // Setup a fresh PageConfig, as we don't want to pull in ClientDependencies or other config that might have
        // been initialized from the "real" page that we ultimately didn't render due to an error
        PageConfig pageConfig = new PageConfig();

        // Issue 41891: do not add google analytics on error pages.
        pageConfig.setAllowTrackingScript(PageConfig.TrueFalse.False);

        if (originalConfig == null)
        {
            pageConfig.setTemplate(PageConfig.Template.Home);
        }
        else
        {
            pageConfig.setTemplate(originalConfig.getTemplate());
            pageConfig.setFrameOption(originalConfig.getFrameOption());
        }

        ViewContext context = HttpView.currentContext();
        if (context == null)
        {
            // context is null in cases of garbage urls, this helps in rendering error page in App template
            context = new ViewContext();
            context.setRequest(request);
            context.setResponse(response);
        }

        HttpView<?> errorView;
        try
        {
            renderer.setErrorType(errorType);

            Path originalContainerPath = (Path)request.getAttribute(ViewServlet.ORIGINAL_URL_CONTAINER_PATH);

            // Issue 43387 - don't render the full header if the container referenced in the request isn't the same
            if (ex instanceof UnauthorizedException && (context.getContainer() == null || !context.getContainer().getParsedPath().equals(originalContainerPath)))
            {
                renderHeaderlessReactErrorPage(ex, responseStatus, request, response, pageConfig, log, renderer, context, null);
            }
            else
            {
                if (HttpView.hasCurrentView())
                {
                    errorView = pageConfig.getTemplate().getTemplate(context, new ErrorView(renderer), pageConfig);
                }
                else
                {
                    // context can be null for configuration exceptions depending on how far server got through initialization
                    errorView = PageConfig.Template.Body.getTemplate(new ViewContext(request, response, new ActionURL(ActionURL.getBaseServerURL())), new ErrorView(renderer), pageConfig);
                }

                addDependenciesAndRender(responseStatus, pageConfig, errorView, ex, request, response);
            }
        }
        catch (ConfigurationException ce)
        {
            throw ce;
        }
        catch (Exception e)
        {
            // non config exceptions like SqlScriptException that occur during startup
            if (null != ModuleLoader.getInstance().getStartupFailure())
            {
                throw new ConfigurationException(ex.getMessage(), ex);
            }

            renderHeaderlessReactErrorPage(ex, responseStatus, request, response, pageConfig, log, renderer, context, e);
        }
    }

    private static void renderHeaderlessReactErrorPage(Throwable ex, int responseStatus, HttpServletRequest request, HttpServletResponse response, PageConfig pageConfig, Logger log, ErrorRenderer renderer, ViewContext context, @Nullable Exception errorRenderException)
    {
        HttpView<?> errorView;
        // try to render just the react app
        try
        {
            errorView = PageConfig.Template.App.getTemplate(context, new ErrorView(renderer), pageConfig);
            addDependenciesAndRender(responseStatus, pageConfig, errorView, ex, request, response);
        }
        catch (Exception exc)
        {
            if (errorRenderException != null)
            {
                log.error("Global.handleException", errorRenderException);
            }
            log.error("Failed to create App template", exc);
        }
    }

    public static void renderErrorView(ViewContext context, PageConfig pageConfig, ErrorRenderer.ErrorType errorType, int responseStatus, String message, @Nullable Throwable ex, boolean isPart, boolean isStartupFailure) throws Exception
    {
        ErrorRenderer renderer = ExceptionUtil.getErrorRenderer(responseStatus, message, ex, context.getRequest(), isPart, isStartupFailure);
        renderer.setErrorType(errorType);
        HttpView<?> errorView = PageConfig.Template.App.getTemplate(context, new ErrorView(renderer), pageConfig);
        if (null != errorView)
        {
            addDependenciesAndRender(responseStatus, pageConfig, errorView, ex, context.getRequest(), context.getResponse());
        }
    }

    private static void addDependenciesAndRender(int responseStatus, PageConfig pageConfig, HttpView<?> errorView, @Nullable Throwable ex, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (null == errorView)
        {
            LOG.error("Failed to create errorView in response to exception", ex);
            return;
        }
        pageConfig.addClientDependencies(errorView.getClientDependencies());

        var title = responseStatus + ": " + ErrorView.ERROR_PAGE_TITLE;
        if (null != ex)
        {
            if (null != ex.getMessage())
            {
                title += " -- " + ex.getMessage();
            }
            else
            {
                title += " -- " + ex;
            }
        }
        pageConfig.setTitle(title, false);
        response.setStatus(responseStatus);
        errorView.getView().render(errorView.getModel(), request, response);
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
            if (response.isCommitted())
            {
                PrintWriter out = response.getWriter();
                out.println("\"'>--></script><script type=\"text/javascript\">");
                out.println("window.location = '" + url + "';");
                out.println("</script>");
            }
        }
        catch (IOException x)
        {
            LOG.error("doErrorRedirect", x);
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


    private final static WeakHashMap<Throwable, HashMap<Enum<?>, String>> _exceptionDecorations = new WeakHashMap<>();
    
    public static boolean decorateException(Throwable t, Enum<?> key, String value, boolean overwrite)
    {
        t = unwrapException(t);
        synchronized (_exceptionDecorations)
        {
            HashMap<Enum<?>, String> m = _exceptionDecorations.computeIfAbsent(t, k -> new HashMap<>());
            if (overwrite || !m.containsKey(key))
            {
                LOG.debug("add decoration to " + t.getClass() + "@" + System.identityHashCode(t) + " " + key + "=" + value);
                m.put(key,value);
                return true;
            }
        }
        return false;
    }


    @NotNull
    public static Map<Enum<?>, String> getExceptionDecorations(Throwable start)
    {
        HashMap<Enum<?>, String> collect = new HashMap<>();
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
                HashMap<Enum<?>, String> m = _exceptionDecorations.get(th);
                if (null != m)
                    collect.putAll(m);
            }
        }
        return collect;
    }


    @Nullable
    public static String getExceptionDecoration(Throwable t, Enum<?> e)
    {
        // could optimize...
        return getExceptionDecorations(t).get(e);
    }


    @NotNull
    public static String getExtendedMessage(Throwable t)
    {
        StringBuilder sb = new StringBuilder(t.toString());
        for (Map.Entry<Enum<?>, String> e : getExceptionDecorations(t).entrySet())
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
        ExceptionResponse handleIt(final User user, Exception ex)
        {
            final MockServletResponse res = new MockServletResponse();
            InvocationHandler h = (o, method, objects) -> {
                // still calls in 'headers' for validation
                res.addHeader(method.getDeclaringClass().getSimpleName() + "." + method.getName(), objects.length==0 ? "" : objects.length==1 ? String.valueOf(objects[0]) : Arrays.toString(objects));
                return null;
            };
            SearchService dummySearch = (SearchService) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{SearchService.class}, h);
            Logger dummyLog = new org.apache.logging.log4j.core.Logger((LoggerContext) LogManager.getContext(), "mock logger", LogManager.getLogger("mock logger").getMessageFactory())
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
                public void error(String message, Throwable t)
                {
                    res.addHeader("Logger.error", null!=message? message :null!=t?t.getMessage():"");
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

                @Override
                public String getMethod()
                {
                    return "GET";
                }
            };
            ExceptionUtil.decorateException(ex, ExceptionInfo.SkipMothershipLogging, "true", true);
            ActionURL url = ExceptionUtil.handleException(req, res, ex, null, false, dummySearch, dummyLog, null);
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
            answer = handleIt(guest, new UnauthorizedException("Not on my watch"));
            assertNotNull("expect return url for login redirect", answer.redirect);
            assertEquals(0, answer.response.status);  // status not set

            // Non-Guest Unauthorized
            answer = handleIt(me, new UnauthorizedException("Not on my watch"));
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_FORBIDDEN, answer.response.status);

            // Guest Basic Unauthorized
            answer = handleIt(guest, new RequestBasicAuthException());
            assertNull("BasicAuth should not redirect", answer.redirect);
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, answer.response.status);
            assertTrue(answer.response.headers.containsKey("WWW-Authenticate"));

            // Non-Guest Basic Unauthorized
            answer = handleIt(me, new RequestBasicAuthException());
            assertNull("BasicAuth should not redirect", answer.redirect);
            assertEquals(HttpServletResponse.SC_FORBIDDEN, answer.response.status);
            assertFalse(answer.response.headers.containsKey("WWW-Authenticate"));

            // Guest CSRF
            answer = handleIt(guest, new CSRFException(TestContext.get().getRequest()));
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, answer.response.status);

            // Non-Guest CSRF
            answer = handleIt(me, new CSRFException(TestContext.get().getRequest()));
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_UNAUTHORIZED, answer.response.status);
        }

        @Test
        public void testRedirect()
        {
            User guest = UserManager.getGuestUser();
            ExceptionResponse answer;

            ActionURL url = new ActionURL("controller", "action", JunitUtil.getTestContainer());
            answer = handleIt(guest, new RedirectException(url));
            assertNull(answer.redirect);
            assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, answer.response.status);
            assertTrue(answer.response.headers.containsKey("Location"));
        }

        @Test
        public void testNotFound()
        {
            User guest = UserManager.getGuestUser();
            ExceptionResponse answer;

            answer = handleIt(guest, new NotFoundException("Not here"));
            assertNull("not found does not redirect", answer.redirect);
            assertEquals(HttpServletResponse.SC_NOT_FOUND, answer.response.status);
            assertTrue(answer.body.contains("Not here"));

            // simulate a search result not found
            HttpServletRequest req = TestContext.get().getRequest();
            ActionURL orig = new ActionURL("controller", "action", JunitUtil.getTestContainer());
            orig.addParameter("_docid", "fred");
            req.setAttribute(ViewServlet.ORIGINAL_URL_URLHELPER, orig);

            answer = handleIt(guest, new NotFoundException("Not here"));
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

            answer = handleIt(me, new NullPointerException());
            assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, answer.response.status);
            assertTrue(answer.response.headers.containsKey("Logger.error"));
        }

        @Test
        public void testUnwrap()
        {

        }
    }

    /**
     * Mock response for testing purposes.
     */
    static class MockServletResponse implements HttpServletResponse
    {
        Map<String, String> headers = new TreeMap<>();
        int status = 0;
        String message = null;
        String redirect = null;
        String contentType = null;
        String characterEncoding = null;
        int contentLength = 0;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Locale locale = null;
        PrintWriter printWriter = new PrintWriter(os);

        ServletOutputStream servletOutputStream = new ServletOutputStream()
        {
            @Override
            public void write(int i)
            {
                os.write(i);
            }
        };


        @Override
        public void addCookie(Cookie cookie)
        {
        }

        @Override
        public boolean containsHeader(String s)
        {
            return headers.containsKey(s);
        }

        @Override
        public String encodeURL(String s)
        {
            throw new IllegalStateException();
        }

        @Override
        public String encodeRedirectURL(String s)
        {
            throw new IllegalStateException();
        }

        @Override
        public String encodeUrl(String s)
        {
            throw new IllegalStateException();
        }

        @Override
        public String encodeRedirectUrl(String s)
        {
            throw new IllegalStateException();
        }

        @Override
        public void sendError(int i, String s)
        {
            status = i;
            message = s;
        }

        @Override
        public void sendError(int i)
        {
            status = i;
        }

        @Override
        public void sendRedirect(String s)
        {
            redirect = s;
        }

        @Override
        public void setDateHeader(String s, long l)
        {
            headers.put(s, DateUtil.toISO(l));
        }

        @Override
        public void addDateHeader(String s, long l)
        {
            headers.put(s, DateUtil.toISO(l));
        }

        @Override
        public void setHeader(String s, String s1)
        {
            headers.put(s,s1);
        }

        @Override
        public void addHeader(String s, String s1)
        {
            headers.put(s,s1);
        }

        @Override
        public void setIntHeader(String s, int i)
        {
            headers.put(s,String.valueOf(i));
        }

        @Override
        public void addIntHeader(String s, int i)
        {
            headers.put(s,String.valueOf(i));
        }

    //  This will be required when we upgrade servlet-api to a more modern version
    //    @Override
    //    public String getHeader(String s)
    //    {
    //        return headers.get(s);
    //    }
    //
    //    @Override
    //    public Collection<String> getHeaders(String s)
    //    {
    //        return Collections.singleton(getHeader(s));
    //    }
    //
    //    @Override
    //    public Collection<String> getHeaderNames()
    //    {
    //        return headers.keySet();
    //    }
    //
        @Override
        public void setStatus(int i)
        {
            status = i;
        }

        @Override
        public void setStatus(int i, String s)
        {
            status = i;
            message = s;
        }

    //    @Override
    //    public int getStatus()
    //    {
    //        return status;
    //    }
    //
        @Override
        public String getCharacterEncoding()
        {
            return characterEncoding;
        }

        @Override
        public String getContentType()
        {
            return contentType;
        }

        @Override
        public ServletOutputStream getOutputStream()
        {
            return servletOutputStream;
        }

        @Override
        public PrintWriter getWriter()
        {
            return printWriter;
        }

        @Override
        public void setCharacterEncoding(String s)
        {
            characterEncoding = s;
        }

        @Override
        public void setContentLength(int i)
        {
            contentLength = i;
        }

        @Override
        public void setContentType(String s)
        {
            contentType = s;
        }

        @Override
        public void setBufferSize(int i)
        {
        }

        @Override
        public int getBufferSize()
        {
            return 0;
        }

        @Override
        public void flushBuffer()
        {
        }

        @Override
        public void resetBuffer()
        {
            printWriter.flush();
            os.reset();
        }

        @Override
        public boolean isCommitted()
        {
            return false;
        }

        @Override
        public void reset()
        {
            resetBuffer();
            status = 0;
            message = null;
    //        headers.clear();
        }

        @Override
        public void setLocale(Locale locale)
        {
            this.locale = locale;
        }

        @Override
        public Locale getLocale()
        {
            return locale;
        }

        public String getBodyAsText()
        {
            printWriter.flush();
            return new String(os.toByteArray(), 0, os.size());
        }

        @Override
        public int getStatus()
        {
            return status;
        }

        @Override
        public String getHeader(String s)
        {
            return headers.get(s);
        }

        @Override
        public Collection<String> getHeaders(String s)
        {
            return null; // TODO
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            return headers.keySet();
        }
    }
}
