/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.springframework.web.servlet.mvc.Controller;

import java.util.List;

/**
* User: brittp
* Date: Sep 15, 2011
* Time: 4:15:32 PM
*/
public abstract class FolderTab
{
    public static final String FOLDER_TAB_PAGE_ID = "folderTab";
    public static final String LOCATION = "tab";

    private final String _name;
    private final String _caption;

    protected FolderTab(String name)
    {
        this(name, name);
    }

    /**
     * @param name the stable, consistent name for this tab, regardless of any content in this folder
     * @param caption the title to be shown on the tab itself in the UI, which may vary based on configuration
     */
    protected FolderTab(String name, String caption)
    {
        _name = name;
        _caption = caption;
    }

    /** A tab backed by a portal page */
    public static abstract class PortalPage extends FolderTab
    {
        private final Class<? extends Controller> _actionClass;

        protected PortalPage(String pageId, String caption, Class<? extends Controller> actionClass)
        {
            super(pageId, caption);
            _actionClass = actionClass;
        }

        public ActionURL getURL(ViewContext context)
        {
            ActionURL actionUrl = new ActionURL(_actionClass, context.getContainer());
            actionUrl.addParameter("pageName", getName());
            return actionUrl;
        }

        public boolean isSelectedPage(ActionURL currentURL)
        {
            if (currentURL.getPageFlow().equals("study-redesign"))
            {
                String pageName = currentURL.getParameter("pageName");
                return getName().equalsIgnoreCase(pageName);
            }
            return false;
        }

        public abstract List<Portal.WebPart> createWebParts();

        @Override
        public void initializeContent(Container container)
        {
            // Initialize the portal pages for each of the tabs
            Portal.WebPart[] webParts = Portal.getParts(container, getName());
            if (webParts.length == 0)
            {
                List<Portal.WebPart> parts = createWebParts();
                if (parts.size() > 0)
                {
                    Portal.saveParts(container, getName(), parts.toArray(new Portal.WebPart[parts.size()]));
                }
            }
        }
    }

    /** Do any configuration needed on the container, such as setting up the default pages on a portal */
    public void initializeContent(Container container)
    {

    }

    public abstract boolean isSelectedPage(ActionURL currentURL);

    public abstract ActionURL getURL(ViewContext container);

    public boolean isVisible(ViewContext context)
    {
        return true;
    }

    public String getName()
    {
        return _name;
    }

    public String getCaption()
    {
        return _caption;
    }
}
