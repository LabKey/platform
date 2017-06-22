/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.rstudio.RStudioService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The Developer menu that appears in the header when the user is in the Developers group, but is not an admin.
 * User: dave
 * Date: Sep 10, 2009
 */
public class PopupDeveloperView extends PopupMenuView
{
    public PopupDeveloperView(ViewContext context)
    {
        NavTree navTree = new NavTree("Developer");

        if (context.getUser().isDeveloper())
            navTree.addChildren(getNavTree(context));

        navTree.setId("devMenu");
        setNavTree(navTree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }

    public static List<NavTree> getNavTree(ViewContext context)
    {
        Container container = context.getContainer();
        ArrayList<NavTree> items = new ArrayList<>();
        if (!container.isRoot())
            items.add(new NavTree("Schema Browser", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(container)));
        String consoleURL = PageFlowUtil.urlProvider(AdminUrls.class).getSessionLoggingURL().getLocalURIString(false);
        NavTree consoleNavTree = new NavTree("Server JavaScript Console");
        consoleNavTree.setScript("window.open('" + consoleURL + "','javascriptconsole','width=400,height=400,location=0,menubar=0,resizable=1,status=0,alwaysRaised=yes')");
        items.add(consoleNavTree);
        String memTrackerURL = PageFlowUtil.urlProvider(AdminUrls.class).getTrackedAllocationsViewerURL().getLocalURIString(false);
        NavTree memTrackerNavTree = new NavTree("Memory Allocations");
        memTrackerNavTree.setScript("window.open('" + memTrackerURL + "','memoryallocations','width=500,height=400,location=0,menubar=0,resizable=1,status=0,alwaysRaised=yes')");
        items.add(memTrackerNavTree);
        items.add(new NavTree("JavaScript API Reference", "https://www.labkey.org/download/clientapi_docs/javascript-api/"));
        items.add(new NavTree("XML Schema Reference", "https://www.labkey.org/download/schema-docs/xml-schemas"));
        RStudioService rstudio = ServiceRegistry.get(RStudioService.class);
        if (null != rstudio)
        {
            ActionURL url = rstudio.getRStudioLink(context.getUser());
            if (null != url)
            {
                NavTree rss = new NavTree("RStudio Server",url+"&method=LAUNCH");
                rss.setTarget("labkey_rstudio");
                items.add(rss);
            }
        }
        return items;
    }
}
