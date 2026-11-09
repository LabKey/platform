/*
 * Copyright (c) 2009-2026 LabKey Corporation
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
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static methods that handle the registration of Developer menu providers. PopupAdminView renders the menus.
 */
public class DeveloperMenu
{
    private static final List<MenuProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private DeveloperMenu()
    {
    }

    public static List<NavTree> getNavTree(ViewContext context)
    {
        DeveloperMenuNavTrees trees = new DeveloperMenuNavTrees();

        PROVIDERS.forEach(p -> p.addMenuItems(context.getContainer(), context.getUser(), trees));

        return trees.toNavTrees();
    }

    public static void registerMenuProvider(MenuProvider provider)
    {
        PROVIDERS.add(provider);
    }

    static
    {
        registerMenuProvider((c, user, items) -> {
            if (c.hasPermission(user, ReadPermission.class))
            {
                items.add(DeveloperMenuNavTrees.Section.query, new NavTree("Schema Browser", PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(c)));
            }

            if (user.isPlatformDeveloper())
            {
                String memTrackerURL = PageFlowUtil.urlProvider(AdminUrls.class).getTrackedAllocationsViewerURL().getLocalURIString(false);
                NavTree memTrackerNavTree = new NavTree("Memory Allocations");
                memTrackerNavTree.setScript("window.open('" + memTrackerURL + "','memoryallocations','width=500,height=400,location=0,menubar=0,resizable=1,status=0,alwaysRaised=yes')");
                items.add(DeveloperMenuNavTrees.Section.monitoring, memTrackerNavTree);

                String consoleURL = PageFlowUtil.urlProvider(AdminUrls.class).getSessionLoggingURL().getLocalURIString(false);
                NavTree consoleNavTree = new NavTree("Server JavaScript Console");
                consoleNavTree.setScript("window.open('" + consoleURL + "','javascriptconsole','width=400,height=400,location=0,menubar=0,resizable=1,status=0,alwaysRaised=yes')");
                items.add(DeveloperMenuNavTrees.Section.monitoring, consoleNavTree);
            }

            items.add(DeveloperMenuNavTrees.Section.referenceDocs, new NavTree("JavaScript API Reference", "https://www.labkey.org/download/clientapi_docs/javascript-api/"));
            items.add(DeveloperMenuNavTrees.Section.referenceDocs, new NavTree("XML Schema Reference", "https://www.labkey.org/download/schema-docs/xml-schemas"));
            items.add(DeveloperMenuNavTrees.Section.referenceDocs, new NavTree("SQL Reference", new HelpTopic("labkeySql").getHelpTopicHref(HelpTopic.Referrer.devMenu)));
        });
    }
}
