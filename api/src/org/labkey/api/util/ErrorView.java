/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.LoginUrls;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

class ErrorView extends HttpView
{
    private final ErrorRenderer _renderer;
    private final boolean _startupFailure;
    private final boolean _popup;

    private ButtonBarRenderer _bbr = null;

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
        _renderer.renderContent(out, request, _bbr);
        doEndTag(out);
    }


    public void doStartTag(PrintWriter out)
    {
        Container c = null;

        out.println("<html><head>");

        // If it's a startup failure we likely don't have a database, so don't try to handle containers.  Instead, hard-code
        // some reasonable styles.
        if (!_startupFailure)
        {
            c = getViewContext().getContainer();

            if (null == c)
                c = ContainerManager.getRoot();

            out.println(PageFlowUtil.getStandardIncludes(c));
        }
        else
        {
            out.println("<style type=\"text/css\">\n" +
                    "<!--\n" +
                    "body {font-family:verdana,arial,helvetica,sans-serif;)}\n" +
                    "-->\n" +
                    "</style>");
        }

        //NOTE: BaseSeleniumWebTest requires errors to start with error number and include word "Error" in title
        out.println("<title>" + PageFlowUtil.filter(_renderer.getTitle()) + "</title>");
        out.println("</head><body style=\"margin:40px; background-color:#336699\">");
        _renderer.renderStart(out);

        if (null != _renderer.getHeading())
        {
            out.println("<h3 style=\"color:red;\">" + PageFlowUtil.filter(_renderer.getHeading()) + "</h3>");
        }

        // These buttons are useless if the server fails to start up.  Also, they try to hit a database that probably doesn't exist.
        if (!_startupFailure)
        {
            if (_popup)
            {
                out.print(PageFlowUtil.generateButton("Close", "#", "window.close(); return false;"));
                out.println("<br>");
            }
            else
            {
                _bbr = new ErrorButtonBarRenderer(c);
            }
        }
    }

    private class ErrorButtonBarRenderer implements ButtonBarRenderer
    {
        private Container _c;

        ErrorButtonBarRenderer(Container c)
        {
            _c = c;
        }

        public void render(PrintWriter out)
        {
            doButtonBar(out, _c);
        }
    }

    private void doButtonBar(PrintWriter out, Container c)
    {
        out.println("<br>");

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

        out.println("<br>");
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
