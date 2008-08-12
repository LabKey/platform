/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.core;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelAppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.PageFlowUtil.Content;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;
import org.labkey.api.admin.CoreUrls;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: jeckels
 * Date: Jan 4, 2007
 */
public class CoreController extends SpringActionController
{
    private static final long SECS_IN_DAY = 60 * 60 * 24;
    private static final long MILLIS_IN_DAY = 1000 * SECS_IN_DAY;

    private static Content _printCssContent = null;
    private static Map<Container, Content> _themeStylesheetCache = new ConcurrentHashMap<Container, Content>();
    private static Map<Container, Content> _customStylesheetCache = new ConcurrentHashMap<Container, Content>();
//    private static Map<Container, Content> _cssContent = new ConcurrentHashMap<Container, Content>();
    private static ActionResolver _actionResolver = new DefaultActionResolver(CoreController.class);

    public CoreController()
    {
        setActionResolver(_actionResolver);
    }

    public static class CoreUrlsImpl implements CoreUrls
    {
        private ActionURL getRevisionURL(Class<? extends Controller> actionClass, Container c)
        {
            ActionURL url = new ActionURL(actionClass, c);
            url.addParameter("revision", AppProps.getInstance().getLookAndFeelRevision());
            return url;
        }

        public ActionURL getThemeStylesheetURL()
        {
            return getRevisionURL(ThemeStylesheetAction.class, ContainerManager.getRoot());
        }

        public ActionURL getThemeStylesheetURL(Container c)
        {
            Container project = c.getProject();
            LookAndFeelAppProps laf = LookAndFeelAppProps.getInstance(project);

            if (laf.hasProperties())
                return getRevisionURL(ThemeStylesheetAction.class, project);
            else
                return null;
        }

        public ActionURL getCustomStylesheetURL()
        {
            return null;  // TODO
        }

        public ActionURL getCustomStylesheetURL(Container c)
        {
            return null;  // TODO
        }

        public ActionURL getPrintStylesheetURL()
        {
            return getRevisionURL(PrintStylesheetAction.class, ContainerManager.getRoot());
        }
    }

    abstract class BaseStylesheetAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            Content content = getContent(request, response);

            response.setContentType("text/css");
            response.setDateHeader("Expires", System.currentTimeMillis() + MILLIS_IN_DAY * 10);
            response.setDateHeader("Last-Modified", content.modified);
            if (StringUtils.trimToEmpty(request.getHeader("Accept-Encoding")).contains("gzip"))
            {
                response.setHeader("Content-Encoding", "gzip");
                response.getOutputStream().write(content.encoded);
            }
            else
            {
                response.getWriter().write(content.content);
            }
        }

        abstract Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception;
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class ThemeStylesheetAction extends BaseStylesheetAction
    {
        Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            Container c = getViewContext().getContainer();
            Content content = _themeStylesheetCache.get(c);
            Integer dependsOn = AppProps.getInstance().getLookAndFeelRevision();
            if (null == content || !dependsOn.equals(content.dependencies) || null != request.getParameter("nocache") || AppProps.getInstance().isDevMode())
            {
                JspView view = new JspView("/org/labkey/core/themeStylesheet.jsp");
                view.setFrame(WebPartView.FrameType.NONE);
                content = PageFlowUtil.getViewContent(view, request, response);
                content.dependencies = dependsOn;
                content.encoded = compressCSS(content.content);
                _themeStylesheetCache.put(c, content);
            }

            return content;
        }
    }

    // TODO: Replace this action with a static file, printStylesheet.css?
    @RequiresPermission(ACL.PERM_NONE)
    public class PrintStylesheetAction extends BaseStylesheetAction
    {
        Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            Content content = _printCssContent;

            if (null == content || null != request.getParameter("nocache") || AppProps.getInstance().isDevMode())
            {
                JspView view = new JspView("/org/labkey/core/printstyle.jsp");
                view.setFrame(WebPartView.FrameType.NONE);
                content = PageFlowUtil.getViewContent(view, request, response);
                content.encoded = compressCSS(content.content);
                _printCssContent = content;
            }

            return content;
        }
    }


    private byte[] compressCSS(String s)
    {
        String c = s.trim();
        // this works but probably unnecesary with gzip
        //c = c.replaceAll("\\s+", " ");
        //c = c.replaceAll("\\s*}\\s*", "}\r\n");
        return PageFlowUtil.gzip(c);
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class ContainerRedirectAction extends SimpleRedirectAction<RedirectForm>
    {
        public ActionURL getRedirectURL(RedirectForm form) throws Exception
        {
            Container targetContainer = ContainerManager.getForId(form.getContainerId());
            if (targetContainer == null)
            {
                HttpView.throwNotFound();
            }
            ActionURL url = getViewContext().getActionURL().clone();
            url.deleteParameter("action");
            url.deleteParameter("pageflow");
            url.deleteParameter("containerId");
            url.setPageFlow(form.getPageflow());
            url.setAction(form.getAction());
            url.setExtraPath(targetContainer.getPath());

            return url;
        }
    }


    public static class RedirectForm
    {
        private String _containerId;
        private String _action;
        private String _pageflow;

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public String getContainerId()
        {
            return _containerId;
        }

        public void setContainerId(String containerId)
        {
            _containerId = containerId;
        }

        public String getPageflow()
        {
            return _pageflow;
        }

        public void setPageflow(String pageflow)
        {
            _pageflow = pageflow;
        }
    }
}
