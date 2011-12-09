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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;
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
        protected PortalPage(String pageId, String caption)
        {
            super(pageId, caption);
        }

        public ActionURL getURL(ViewContext context)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(context.getContainer(), getName());
        }

        public boolean isSelectedPage(ViewContext viewContext)
        {
            ActionURL currentURL = viewContext.getActionURL();
            ActionURL tabURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ContainerManager.getHomeContainer(), getName());
            if (currentURL.getPageFlow().equalsIgnoreCase(tabURL.getPageFlow()) &&
                currentURL.getAction().equalsIgnoreCase(tabURL.getAction()))
            {
                String pageName = currentURL.getParameter("pageId");
                return getName().equalsIgnoreCase(pageName);
            }
            return false;
        }

        public abstract List<Portal.WebPart> createWebParts();

        @Override
        public void initializeContent(Container container)
        {
            // Initialize the portal pages for each of the tabs
            Collection<Portal.WebPart> webParts = Portal.getParts(container, getName());

            if (webParts.size() == 0)
            {
                List<Portal.WebPart> parts = createWebParts();

                if (parts.size() > 0)
                {
                    Portal.saveParts(container, getName(), parts);
                }
            }
        }
    }

    /** Do any configuration needed on the container, such as setting up the default pages on a portal */
    public void initializeContent(Container container)
    {

    }

    public abstract boolean isSelectedPage(ViewContext viewContext);

    public abstract ActionURL getURL(ViewContext viewContext);

    public boolean isVisible(ViewContext context)
    {
        return true;
    }

    public String getName()
    {
        return _name;
    }

    public String getCaption(ViewContext viewContext)
    {
        return _caption;
    }
}
