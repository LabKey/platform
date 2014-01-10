/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.URLHelper;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

/**
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public abstract class TabStripView extends JspView<TabStripView>
{
    public static final String TAB_PARAM = "tabId";

    public String _prefix = "";
    protected List<NavTree> _tabs;
    protected String _selectedTabId = null;

    public TabStripView()
    {
        super("/org/labkey/api/view/tabstrip.jsp");
        setFrame(WebPartView.FrameType.NONE);
        setModelBean(this);
    }

    /** You can use this constructor, if you know which tab is selected and you don't want to subclass TabStripView */
    public TabStripView(List<NavTree> tabs, ModelAndView selectedView)
    {
        super("/org/labkey/api/view/tabstrip.jsp");
        setFrame(WebPartView.FrameType.NONE);
        setModelBean(this);

        _tabs = tabs;
        setBody(selectedView);
    }

    /** set prefix for tab ids and content div */
    public TabStripView(String prefix)
    {
        this();
        _prefix = prefix + "_";
    }

    /** Subclasses may override this method */
    public List<NavTree> getTabList()
    {
        return _tabs;
    }


    public void setSelectedTabId(String tabId)
    {
        _selectedTabId = tabId;
    }

    public String getSelectedTabId()
    {
        if (null != _selectedTabId)
            return _selectedTabId;
        
        String tabId = getViewContext().getActionURL().getParameter(TAB_PARAM);
        if (StringUtils.isEmpty(tabId))
        {
            List<NavTree> tabs = getTabList();
            if (!tabs.isEmpty())
                tabId = tabs.get(0).getId();
        }
        return tabId;
    }
    
    
    /** Subclasses may override this method */
    public ModelAndView getTabView(String tabId) throws Exception
    {
        return getBody();
    }

    public static class TabInfo extends NavTree
    {
        public TabInfo(String name, String id, URLHelper url)
        {
            super(name, url.clone().replaceParameter(TAB_PARAM, id).getLocalURIString());
            setId(id);
        }
    }
}