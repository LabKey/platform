/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.api.view;

import org.apache.commons.lang.StringUtils;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public abstract class TabStripView extends HttpView
{
    public static final String TAB_PARAM = "tabId";

    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        List<TabInfo> tabs = getTabList();
        JspView header = new JspView<List<TabInfo>>("/org/labkey/api/view/tabHeader.jsp", tabs);
        header.setFrame(WebPartView.FrameType.NONE);
        include(header);

        String tabId = getViewContext().getActionURL().getParameter(TAB_PARAM);
        if (StringUtils.isEmpty(tabId) && !tabs.isEmpty())
            tabId = tabs.get(0).getId();

        HttpView tabView = getTabView(tabId);
        if (tabView != null)
            include(tabView);
        else
            include(new HtmlView("No handler for view: " + tabId));

        include(new HttpView() {
            protected void renderInternal(Object model, PrintWriter out) throws Exception {
                out.write("</td></tr></table>");
            }
        });
    }

    protected abstract List<TabInfo> getTabList();
    protected abstract HttpView getTabView(String tabId) throws Exception;

    public static class TabInfo
    {
        private String _id;
        private String _name;
        private ActionURL _url;

        public TabInfo(String name, String id, ActionURL url)
        {
            _name = name;
            _id = id;
            _url = url;
        }

        public String getId()
        {
            return _id;
        }

        public void setId(String id)
        {
            _id = id;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public ActionURL getUrl()
        {
            return _url;
        }

        public void setUrl(ActionURL url)
        {
            _url = url;
        }

        public String render(ViewContext context)
        {
            return("<a href=\"" + getUrl().replaceParameter("tabId", getId()).getLocalURIString() + "\">" + getName() + "&nbsp;</a>");
        }
    }
}