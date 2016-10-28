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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebTheme;
import org.labkey.api.view.WebThemeManager;

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

        out.println("<!DOCTYPE html>");
        out.println("<html><head>");

        // If it's a startup failure we likely don't have a database, so don't try to handle containers.  Instead, hard-code
        // some reasonable styles.
        if (!_startupFailure)
        {
            out.println(PageFlowUtil.getStandardIncludes(getViewContext(), null));

            c = getViewContext().getContainer();
        }
        else
        {
            out.println("<style type=\"text/css\">\n" +
                    "<!--\n" +
                    "body {font-family:verdana,arial,helvetica,sans-serif;)}\n" +
                    "-->\n" +
                    "</style>");
        }

        if (null == c)
        {
            try
            {
                c = ContainerManager.getRoot();
            }
            catch (Throwable t)
            {
                // Exception at initial bootstrap (e.g., database not supported) might result in no root
            }
        }

        // need the theme to apply style to ext-based body element
        WebTheme theme = null == c ? WebTheme.DEFAULT : WebThemeManager.getTheme(c);

        //NOTE: Selenium tests expect errors to start with error number and include word "Error" in title
        out.println("<title>" + PageFlowUtil.filter(_renderer.getTitle()) + "</title>");
        out.println("</head><body style=\"margin:40px; background-color:#" + theme.getLinkColor() + "\">");
        _renderer.renderStart(out);

        if (null != _renderer.getHeading())
        {
            out.println("<h3 class=\"labkey-error\">" + PageFlowUtil.filter(_renderer.getHeading()) + "</h3>");
        }

        // Why don't we just use c below?
        Container contextContainer = getContextContainer();

        if (null == contextContainer)
            contextContainer = c;

        // These buttons are useless if the server fails to start up.  Also, they try to hit a database that probably doesn't exist.
        if (!_startupFailure)
        {
            if (_renderer.getStatus() == HttpServletResponse.SC_UNAUTHORIZED)
            {
                if (getViewContext().getUser().isGuest())
                {
                    ActionURL url = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(getViewContext().getContainer(), null);
                    out.println("<p>You are not currently logged in. You may need to <a href=\"" + url + "\">log in</a> to gain the necessary permissions.</a>");
                }
                LookAndFeelProperties props = LookAndFeelProperties.getInstance(contextContainer);
                if (!StringUtils.isBlank(props.getSupportEmail()))
                {
                    out.println("<p>If you believe you should have permission to perform this action, please <a href=\"mailto:");
                    out.println(PageFlowUtil.filter(props.getSupportEmail()));
                    out.println("?subject=Permissions on ");
                    if (!StringUtils.isBlank(props.getShortName()))
                    {
                        out.println(PageFlowUtil.filter(props.getShortName()));
                    }
                    out.println("\">email your system administrator</a>.</p>");
                }
            }

            if (_popup)
            {
                out.print(PageFlowUtil.button("Close").href("#").onClick("window.close(); return false;"));
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
        if (_includeHomeButton)
        {
            out.print(PageFlowUtil.button("Home").href(AppProps.getInstance().getHomePageActionURL()));
            out.print("&nbsp;");
        }
        if (_includeBackButton)
        {
            out.print(PageFlowUtil.generateBackButton());
            out.print("&nbsp;");
        }
        if (_includeFolderButton && !c.isRoot())
        {
            ActionURL folderURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
            out.print(PageFlowUtil.button("Folder").href(folderURL));
            out.print("&nbsp;");
        }
        if (_includeStopImpersonatingButton)
        {
            ActionURL logoutURL = PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(c, getViewContext().getActionURL());
            out.print(PageFlowUtil.button("Stop Impersonating").href(logoutURL));
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
