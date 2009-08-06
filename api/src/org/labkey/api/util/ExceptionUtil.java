/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;

/**
 * User: rossb
 * Date: Oct 26, 2006
 */
public class ExceptionUtil
{
    private static final JobRunner _jobRunner = new JobRunner(1);
    private static Logger _log = Logger.getLogger(ExceptionUtil.class);


    private ExceptionUtil()
    {
    }


    public static String renderStackTrace(StackTraceElement[] stackTrace)
    {
        StringBuilder trace = new StringBuilder();

        for (int i = 2; i < stackTrace.length; i++)
        {
            trace.append("\n\tat ");
            trace.append(stackTrace[i]);
        }

        return trace.toString();
    }


    public static Throwable unwrapException(Throwable ex)
    {
        Throwable cause=ex;

        while (null != cause)
        {
            ex = cause;
            cause = null;

            if (ex.getClass() == RuntimeException.class || ex.getClass() == ServletException.class || ex.getClass() == RuntimeSQLException.class || ex instanceof InvocationTargetException || ex instanceof com.google.gwt.user.server.rpc.UnexpectedException)
            {
                cause = ex.getCause();
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
                                                 HttpServletRequest request, boolean isPart, boolean isStartupFailure)
    {
        if (!isStartupFailure && responseStatus == HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            logExceptionToMothership(request, ex);

        if (isPart)
            return new WebPartErrorRenderer(responseStatus, message, ex, isStartupFailure);
        else
            return new ErrorRenderer(responseStatus, message, ex, isStartupFailure);
    }

    /** request may be null if this is coming from a background thread */
    public static void logExceptionToMothership(HttpServletRequest request, Throwable ex)
    {
        ex = unwrapException(ex);
        if (isClientAbortException(ex) || ex instanceof RedirectException || ex instanceof NotFoundException || ex instanceof UnauthorizedException || ex instanceof ActionURLException)
        {
            // We don't need to log any of these
            return;
        }

        String originalURL = request == null ? null : (String) request.getAttribute(ViewServlet.ORIGINAL_URL);
        ExceptionReportingLevel level = AppProps.getInstance().getExceptionReportingLevel();
        if (level != ExceptionReportingLevel.NONE &&
                ex != null &&
                // Need this extra check to make sure we're not in an infinite loop if there's
                // an exception when trying to submit an exception
                (originalURL == null || !originalURL.contains("/Mothership/_mothership/reportException")))
        {
            try
            {
                MothershipReport report = new MothershipReport("reportException");
                StringWriter stringWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stringWriter, true);
                ex.printStackTrace(printWriter);
                if (ex instanceof ServletException && ((ServletException)ex).getRootCause() != null)
                {
                    printWriter.println("Nested ServletException cause is:");
                    ((ServletException)ex).getRootCause().printStackTrace(printWriter);
                }
                report.addParam("browser", request == null ? null : request.getHeader("User-Agent"));

                String stackTrace = stringWriter.getBuffer().toString();
                for (Throwable t = ex ; t != null ; t = t.getCause())
                {
                    String sqlState = null;

                    if (t instanceof RuntimeSQLException)
                    {
                        // Unwrap RuntimeSQLExceptions
                        t = ((RuntimeSQLException)t).getSQLException();
                    }

                    if (t instanceof SQLException)
                    {
                        SQLException sqlException = (SQLException) t;
                        sqlState = sqlException.getSQLState();
                        String extraInfo = CoreSchema.getInstance().getSqlDialect().getExtraInfo(sqlException);
                        if (extraInfo != null)
                        {
                            stackTrace = stackTrace + "\n" + extraInfo;
                        }
                    }

                    if (sqlState != null)
                    {
                        report.addParam("sqlState", sqlState);
                        break;
                    }
                }

                report.addParam("stackTrace", stackTrace);

                report.addServerSessionParams();
                if (originalURL != null)
                {
                    try
                    {
                        ActionURL url = new ActionURL(originalURL);
                        report.addParam("pageflowName", url.getPageFlow());
                        report.addParam("pageflowAction", url.getAction());
                    }
                    catch (IllegalArgumentException x)
                    {
                        // fall through
                    }
                }
                if (level == ExceptionReportingLevel.MEDIUM || level == ExceptionReportingLevel.HIGH)
                {
                    if (originalURL != null)
                    {
                        report.addParam("requestURL", originalURL);
                        report.addParam("referrerURL", request == null ? null : request.getHeader("Referer"));
                    }

                    if (level == ExceptionReportingLevel.HIGH)
                    {
                        User user = request == null ? null : (User) request.getUserPrincipal();
                        if (user == null)
                        {
                            report.addParam("username", "NOT SET");
                        }
                        else
                        {
                            report.addParam("username", user.getEmail() == null ? "Guest" : user.getEmail());
                        }
                    }
                }

                _jobRunner.execute(report);
            }
            catch (MalformedURLException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ErrorRenderer
    {
        final int status;
        final String message;
        final Throwable exception;
        final boolean isStartupFailure;

        ErrorRenderer(int status, String message, Throwable x, boolean isStartupFailure)
        {
            this.status = status;
            this.message = message;
            this.exception = x;
            this.isStartupFailure = isStartupFailure;
        }

        public void renderStart(PrintWriter out)
        {
            out.println("<table cellpadding=4 style=\"width:100%; height:100%\"><tr><td style=\"background-color:#ffffff;\" valign=top align=left>");
        }

        public void renderEnd(PrintWriter out)
        {
            out.println("</td></tr></table>");
        }

        public void renderContent(PrintWriter out, HttpServletRequest request) throws IOException, ServletException
        {
            if (null != exception)
            {
                boolean showDetails = AppProps.getInstance().isDevMode() || isStartupFailure;

                String exceptionMessage = null;
                if (isStartupFailure)
                {
                    exceptionMessage = "A failure occurred during LabKey Server startup.";
                }
                else
                {
                    try
                    {
                        exceptionMessage = exception.getMessage();
                    }
                    catch (Throwable x)
                    {
                        //
                    }
                }

                if (null != exceptionMessage)
                {
                    out.println("<b style=\"color:red;\">");
                    out.println(PageFlowUtil.filter(exceptionMessage));
                    out.println("</b><br>");
                }

                if (!showDetails)
                {
                    out.println("<p><div id='togglePanel' style='cursor:pointer' onclick='contentPanel.style.display=\"block\";togglePanel.style.display=\"none\"'>[<a href='#details'>Show more details</a>]</div>\n" +
                            "<div id=\"contentPanel\" style=\"display:none;\">");
                }

                renderException(exception, out);
                String s;

                // Show the request attributes and database details, but only if it's not a startup failure
                if (!isStartupFailure)
                {
                    out.println("<b>request attributes</b><br>");
                    for (Enumeration e = request.getAttributeNames(); e.hasMoreElements();)
                    {
                        try
                        {
                            s = e.nextElement().toString();
                            s = PageFlowUtil.filter("    " + s + " = " + request.getAttribute(s));
                            s = s.replaceAll("\n", "<br>");
                            out.println(s);
                        }
                        catch (Exception x)
                        {
                            //
                        }
                        out.println("<br>");
                    }

                    try
                    {
                        DbSchema core = CoreSchema.getInstance().getSchema();
                        DbScope scope = core.getScope();

                        if (null != core)
                        {
                            out.println("<br><table>\n");
                            out.println("<tr><td colspan=2><b>core schema database configuration</b></td></tr>\n");
                            out.println("<tr><td>Server URL</td><td>" + scope.getURL() + "</td></tr>\n");
                            out.println("<tr><td>Product Name</td><td>" + scope.getDatabaseProductName() + "</td></tr>\n");
                            out.println("<tr><td>Product Version</td><td>" + scope.getDatabaseProductVersion() + "</td></tr>\n");
                            out.println("<tr><td>Driver Name</td><td>" + scope.getDriverName() + "</td></tr>\n");
                            out.println("<tr><td>Driver Version</td><td>" + scope.getDriverVersion() + "</td></tr>\n");
                            out.println("</table>\n");
                        }
                    }
                    catch(Exception e)
                    {
                        //
                    }
                }

                if (!showDetails)
                {
                    out.println("</div>");
                }
            }
        }

        private void renderException(Throwable exception, PrintWriter out)
        {
            if (exception == null)
            {
                return;
            }

            out.print(ExceptionUtil.renderException(exception));

            if (exception instanceof ServletException)
            {
                renderException(((ServletException)exception).getRootCause(), out);
            }

            if (exception instanceof SQLException)
            {
                renderException(((SQLException)exception).getNextException(), out);
            }
        }
    }

    static class WebPartErrorRenderer extends ErrorRenderer
    {
        WebPartErrorRenderer(int status, String message, Throwable x, boolean isStartupFailure)
        {
            super(status, message, x, isStartupFailure);
        }

        public void renderStart(PrintWriter out)
        {
            out.println("<div style=\"height:200px; overflow:scroll;\">");
            if (null != this.message)
            {
                out.println("<h3 style=\"color:red;\">" + this.message + "</h3>");
            }
            super.renderStart(out);
        }

        public void renderEnd(PrintWriter out)
        {
            super.renderEnd(out);
            out.println("</div>");
        }
    }

    static class WebPartErrorView extends WebPartView
    {
        private final ErrorRenderer _renderer;

        WebPartErrorView(ErrorRenderer renderer)
        {
            super((Map)null);
            _renderer = renderer;
        }

        @Override
        protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            PrintWriter out = response.getWriter();
            _renderer.renderStart(out);
            _renderer.renderContent(out, request);
            _renderer.renderEnd(out);
        }
    }

    
    static class ErrorView extends HttpView
    {
        private final ErrorRenderer _renderer;
        private boolean _startupFailure;
        private boolean _popup;
        private boolean _includeHomeButton = true;
        private boolean _includeBackButton = true;
        private boolean _includeFolderButton = true;
        private boolean _includeStopImpersonatingButton = false;

        ErrorView(ErrorRenderer renderer, boolean startupFailure)
        {
            this(renderer, startupFailure, false);
        }

        ErrorView(ErrorRenderer renderer, boolean startupFailure, boolean popup)
        {
            _renderer = renderer;
            _startupFailure = startupFailure;
            _popup = popup;
        }


        @Override
        public void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            PrintWriter out = response.getWriter();

            doStartTag(out);
            _renderer.renderContent(out, request);
            doEndTag(out);
        }


        public void doStartTag(PrintWriter out)
        {
            Container c = null;

            out.println("<html><head>");

            // If it's a startup failure we likely don't have a database, so don't try to handle containers
            if (!_startupFailure)
            {
                c = getViewContext().getContainer();

                if (null == c)
                    c = ContainerManager.getRoot();

                out.println(PageFlowUtil.getStandardIncludes(c));
            }

            //NOTE: BaseSeleniumWebTest requires errors to start with error number and include word "Error" in title
            String title = "" + _renderer.status + ": Error Page";
            if (null != _renderer.message)
                title += " -- " + _renderer.message;
            out.println("<title>" + PageFlowUtil.filter(title) + "</title>");
            out.println("</head><body style=\"margin:40px; background-color:#336699\">");
            _renderer.renderStart(out);
            if (null != _renderer.message)
            {
                out.println("<h3 style=\"color:red;\">" + PageFlowUtil.filter(_renderer.message) + "</h3>");
            }

            // These buttons are useless if the server fails to start up.  Also, they try to hit a database that probably doesn't exist.
            if (!_startupFailure)
            {
                if (_popup)
                {
                    out.print(PageFlowUtil.generateButton("Close", "#", "window.close(); return false;"));
                }
                else
                {
                    if (_includeHomeButton)
                    {
                        out.print(PageFlowUtil.generateButton("Home", AppProps.getInstance().getHomePageActionURL()));
                        out.print("&nbsp;");
                    }
                    if (_includeBackButton)
                    {
                        out.print(PageFlowUtil.generateButton("Back", "javascript:window.history.back();"));
                        out.print("&nbsp;");
                    }
                    if (_includeFolderButton && !c.isRoot())
                    {
                        ActionURL folderURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
                        out.print(PageFlowUtil.generateButton("Folder", folderURL));
                        out.print("&nbsp;");
                    }
                    if (_includeStopImpersonatingButton)
                    {
                        ActionURL logoutURL = PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(c, getViewContext().getActionURL().getLocalURIString());
                        out.print(PageFlowUtil.generateButton("Stop Impersonating", logoutURL));
                    }
                }
                out.println("<br>");
            }
        }

        public void doEndTag(PrintWriter out)
        {
            _renderer.renderEnd(out);
            out.println("</body></html>");
        }

        public void setIncludeHomeButton(boolean includeHomeButton)
        {
            _includeHomeButton = includeHomeButton;
        }

        public void setIncludeBackButton(boolean includeBackButton)
        {
            _includeBackButton = includeBackButton;
        }

        public void setIncludeFolderButton(boolean includeFolderButton)
        {
            _includeFolderButton = includeFolderButton;
        }

        public void setIncludeStopImpersonatingButton(boolean includeStopImpersonatingButton)
        {
            _includeStopImpersonatingButton = includeStopImpersonatingButton;
        }
    }


    public static boolean isClientAbortException(Throwable ex)
    {
        if (ex != null)
        {
            if (ex.getClass().getName().endsWith("ClientAbortException") ||
                ex.getClass().getName().endsWith("FileUploadException"))
            {
                return true;
            }
            if (ex.getCause() != ex)
            {
                return isClientAbortException(ex.getCause());
            }
        }
        return false;
    }

    // This is called by SpringActionController (to display unhandled exceptions) and called directly by AuthFilter.doFilter() (to display startup errors and bypass normal request handling)
    public static ActionURL handleException(HttpServletRequest request, HttpServletResponse response, Throwable ex, String message, boolean startupFailure)
    {
        DbScope.rollbackAllTransactions();

        // First, get rid of RuntimeException, InvocationTargetException, etc. wrappers
        ex = unwrapException(ex);

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
            try {response.getOutputStream().flush();} catch (Exception x) {}
            try {response.getWriter().flush();} catch (Exception x) {}
            try {response.flushBuffer();} catch (Exception x) {}
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
                // This is fine, just can't clear the existing reponse as its
                // been at least partially written back to the client
            }
        }

        boolean resetResponse = true;
        boolean isForbiddenProject = false;  // Hack

        ErrorView errorView;

        // check for unauthorized guest, go to login
        if (ex instanceof UnauthorizedException)
        {
            User user = (User) request.getUserPrincipal();

            // If user has not logged in or agreed to terms, not really unauthorized yet...
            if (user.isGuest() || ex instanceof TermsOfUseException)
            {
                if (ex instanceof RequestBasicAuthException)
                {
                    response.setHeader("WWW-Authenticate", "Basic realm=\"" + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription() + "\"");
                    responseStatus = HttpServletResponse.SC_UNAUTHORIZED;
                    message = "You must log in to view this content.";
                    resetResponse = false;
                    ex = null;
                }
                else if (request.getMethod().equalsIgnoreCase("GET"))
                {
                    UnauthorizedException uae = (UnauthorizedException)ex;

                    ActionURL redirect;
                    if (uae instanceof TermsOfUseException)
                    {
                        redirect = PageFlowUtil.urlProvider(LoginUrls.class).getAgreeToTermsURL(HttpView.getContextContainer(), HttpView.getContextURL());
                    }
                    else
                    {
                        redirect = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(HttpView.getContextContainer(), HttpView.getContextURL());
                    }

                    return redirect;
                }
            }

            // Hack so we can set the right buttons in ErrorView -- ex gets nulled out before we have the view 
            if (ex instanceof ForbiddenProjectException)
            {
                isForbiddenProject = true;
            }
        }

        if (ex instanceof NotFoundException || ex instanceof UnauthorizedException)
        {
            if (ex instanceof NotFoundException)
            {
                responseStatus = HttpServletResponse.SC_NOT_FOUND;
                if (ex instanceof NotFoundException && ex.getMessage() != null)
                    message = ex.getMessage();
                else
                    message = responseStatus + ": Page not Found";                
            }
            else
            {
                responseStatus = HttpServletResponse.SC_UNAUTHORIZED;
                message = ex.getMessage();
            }
            ex = null;
        }
        else if (ex instanceof SQLException)
        {
            responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = SqlDialect.GENERIC_ERROR_MESSAGE;

            if (ex instanceof BatchUpdateException)
            {
                if (null != ((BatchUpdateException)ex).getNextException())
                    ex = ((BatchUpdateException)ex).getNextException();
            }
        }

        if (null == message && null != ex)
        {
            responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            message = responseStatus + ": Unexpected server error";
        }

        errorView = ExceptionUtil.getErrorView(responseStatus, message, ex, request, startupFailure);

        if (responseStatus == HttpServletResponse.SC_NOT_FOUND)
        {
            _log.warn(null == message ? "" : message, ex);
        }
        else if(responseStatus != HttpServletResponse.SC_UNAUTHORIZED) //don't log unauthorized (basic-auth challenge)
        {
            _log.error("Unhandled exception: " + (null == message ? "" : message), ex);
        }

        if (isForbiddenProject)
        {
            // Not allowed in the project... don't offer Home or Folder buttons, provide "Stop Impersonating" button 
            errorView.setIncludeHomeButton(false);
            errorView.setIncludeFolderButton(false);
            errorView.setIncludeStopImpersonatingButton(true);
        }

        if (response.isCommitted())
        {
            // This is fine, just can't clear the existing reponse as its
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
                catch (IOException e)
                {
                    // Give up at this point
                }
            }
        }
        else
        {
            try
            {
                if (resetResponse)
                {
                    response.reset();
                }
                response.setContentType("text/html");
                response.setStatus(responseStatus);
                errorView.render(request, response);
                return null;
            }
            catch (IllegalStateException x)
            {
            }
            catch (Exception x)
            {
                _log.error("Global.handleException", x);
            }
        }

        return null;
    }


    public static void doErrorRedirect(HttpServletResponse response, String url)
    {
        response.setStatus(301);
        response.setHeader("Location", url);

        // backup strategy!
        try
        {
            PrintWriter out = response.getWriter();
            out.println("\"> --></script><script type=\"text/javascript\">");
            out.println("window.location = '" + url + "';");
            out.println("</script>");
        }
        catch (IOException x)
        {
            _log.error("doErrorRedirect", x);
        }
    }
}
