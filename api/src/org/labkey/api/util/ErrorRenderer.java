/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.apache.commons.collections4.IteratorUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Map;

public class ErrorRenderer
{
    private final int _status;
    private final String _heading;
    private final Throwable _exception;
    private final boolean _isStartupFailure;
    private final ErrorRendererProperties _errorRendererProps;
    private final String _title;

    ErrorRenderer(int status, String heading, Throwable x, boolean isStartupFailure)
    {
        _status = status;
        _exception = x;
        _isStartupFailure = isStartupFailure;
        _errorRendererProps = (x instanceof ErrorRendererProperties ? (ErrorRendererProperties)x : null);

        if (null == _errorRendererProps)
        {
            _heading = heading;
            _title = status + ": Error Page" + (null != heading ? " -- " + heading : "");
        }
        else
        {
            _heading = _errorRendererProps.getHeading(_isStartupFailure);
            _title = _errorRendererProps.getTitle();
        }
    }

    public void renderStart(PrintWriter out)
    {
        out.println("<table class=\"server-error\" cellpadding=4 style=\"width:100%; height:100%\"><tr><td style=\"background-color:#ffffff;\" valign=top align=left>");
    }

    public void renderEnd(PrintWriter out)
    {
        out.println("</td></tr></table>");
    }

    public void renderContent(PrintWriter out, HttpServletRequest request, @Nullable ButtonBarRenderer bbr) throws IOException, ServletException
    {
        ViewContext context = HttpView.currentContext();
        User user = null != context ? context.getUser() : null;

        if (null != _exception)
        {
            String exceptionMessage = null;

            if (null != _errorRendererProps)
            {
                out.println(_errorRendererProps.getMessageHtml());
            }
            else
            {
                if (_isStartupFailure)
                {
                    exceptionMessage = "A failure occurred during LabKey Server startup.";
                }
                else
                {
                    try
                    {
                        exceptionMessage = _exception.getMessage();
                    }
                    catch (Throwable x)
                    {
                        //
                    }
                }

                if (null != exceptionMessage)
                {
                    out.println("<b style=\"color:red;\" class=\"exception-message\">");
                    out.println(PageFlowUtil.filter(exceptionMessage));
                    out.println("</b><br><br>");
                }
            }
        }

        // Render the button bar, if present (regardless of whether exception has been nulled out)
        if (null != bbr)
            bbr.render(out);

        if (null != _exception)
        {
            boolean showDetails = AppProps.getInstance().isDevMode() || _isStartupFailure;

            if (!showDetails)
            {
                out.println("<p><div id='togglePanel' style='cursor:pointer' onclick='document.getElementById(\"contentPanel\").style.display=\"block\";document.getElementById(\"togglePanel\").style.display=\"none\";'>" + PageFlowUtil.textLink("Show more details", "#details") + "</div>\n" +
                        "<div id=\"contentPanel\" style=\"display:none;\">");
            }

            renderException(_exception, out);

            // Since there might be SQL in here, maybe don't dump for non-admin/dev
            if (null != user && user.isDeveloper())
            {
                Map<Enum,String> decorations = ExceptionUtil.getExceptionDecorations(_exception);
                for (Map.Entry<Enum,String> e : decorations.entrySet())
                {
                    out.print(PageFlowUtil.filter(e.getKey()));
                    out.print(" = ");
                    out.print(PageFlowUtil.filter(e.getValue()).replaceAll("\n", "<br>&nbsp;&nbsp;&nbsp;&nbsp;"));
                    out.println("<br>");
                }
                if (!decorations.isEmpty())
                    out.println("<br>");
            }

            // Show the request attributes and database details, but only if it's not a startup failure
            if (!_isStartupFailure && !(_exception instanceof HideConfigurationDetails))
            {
                out.println("<b>request attributes</b><br>");
                IteratorUtils.asIterator(request.getAttributeNames()).forEachRemaining(name -> {
                    try
                    {
                        String s = PageFlowUtil.filter("    " + name + " = " + request.getAttribute(name));
                        s = s.replaceAll("\n", "<br>&nbsp;&nbsp;&nbsp;&nbsp;");
                        out.println(s);
                    }
                    catch (Exception x)
                    {
                        //
                    }
                    out.println("<br>");
                });

                try
                {
                    DbSchema core = CoreSchema.getInstance().getSchema();
                    DbScope scope = core.getScope();

                    out.println("<br><table>\n");
                    out.println("<tr><td colspan=2><b>core schema database configuration</b></td></tr>\n");
                    out.println("<tr><td>Server URL</td><td>" + scope.getURL() + "</td></tr>\n");
                    out.println("<tr><td>Product Name</td><td>" + scope.getDatabaseProductName() + "</td></tr>\n");
                    out.println("<tr><td>Product Version</td><td>" + scope.getDatabaseProductVersion() + "</td></tr>\n");
                    out.println("<tr><td>Driver Name</td><td>" + scope.getDriverName() + "</td></tr>\n");
                    out.println("<tr><td>Driver Version</td><td>" + scope.getDriverVersion() + "</td></tr>\n");
                    out.println("</table>\n");
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

    public int getStatus()
    {
        return _status;
    }

    public String getHeading()
    {
        return _heading;
    }

    public Throwable getException()
    {
        return _exception;
    }

    public boolean isStartupFailure()
    {
        return _isStartupFailure;
    }

    public ErrorRendererProperties getErrorRendererProps()
    {
        return _errorRendererProps;
    }

    public String getTitle()
    {
        return _title;
    }
}
