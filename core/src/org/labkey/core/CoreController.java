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
import org.labkey.api.util.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User: jeckels
 * Date: Jan 4, 2007
 */
public class CoreController extends SpringActionController
{
    private static final long SECS_IN_DAY = 60 * 60 * 24;
    private static final long MILLIS_IN_DAY = 1000 * SECS_IN_DAY;

    private static AtomicReference<PageFlowUtil.Content> _cssContent = new AtomicReference<PageFlowUtil.Content>();
    private static ActionResolver _actionResolver = new DefaultActionResolver(CoreController.class);

    public CoreController()
    {
        super();
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class StylesheetAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response) throws Exception
        {
            // This action gets called a LOT, so cache the generated .css
            PageFlowUtil.Content c = _cssContent.get();
            HttpServletRequest request = getViewContext().getRequest();
            Integer dependsOn = AppProps.getInstance().getLookAndFeelRevision();
            if (null == c || !dependsOn.equals(c.dependencies) || null != request.getParameter("nocache") || AppProps.getInstance().isDevMode())
            {
                JspView view = new JspView("/org/labkey/core/stylesheet.jsp");
                view.setFrame(WebPartView.FrameType.NONE);
                c = PageFlowUtil.getViewContent(view, request, response);
                c.dependencies = dependsOn;
                c.encoded = compressCSS(c.content);
                _cssContent.set(c);
            }

            response.setContentType("text/css");
            response.setDateHeader("Expires", System.currentTimeMillis() + MILLIS_IN_DAY * 10);
            response.setDateHeader("Last-Modified", c.modified);
            if (StringUtils.trimToEmpty(request.getHeader("Accept-Encoding")).contains("gzip"))
            {
                response.setHeader("Content-Encoding", "gzip");
                response.getOutputStream().write(c.encoded);
            }
            else
            {
                response.getWriter().write(c.content);
            }
        }
    }


    byte[] compressCSS(String s)
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
