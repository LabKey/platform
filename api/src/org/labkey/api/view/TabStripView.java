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

import org.labkey.api.util.PageFlowUtil;

import java.util.List;

/**
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public abstract class TabStripView extends JspView<TabStripView>
{
    public static final String TAB_PARAM = "tabId";

    public String _prefix = "";

    public TabStripView()
    {
        super("/org/labkey/api/view/tabstrip.jsp");
        setFrame(WebPartView.FrameType.NONE);
        setModelBean(this);
    }

    /** set prefix for tab id's and content div */
    public TabStripView(String prefix)
    {
        this();
        _prefix = prefix + "_";
    }

    public abstract List<NavTree> getTabList();
    public abstract HttpView getTabView(String tabId) throws Exception;

    public static class TabInfo extends NavTree
    {
        public TabInfo(String name, String id, ActionURL url)
        {
            super(name, url.clone().replaceParameter("tabId", id).getLocalURIString());
            setId(id);
        }
    }
}