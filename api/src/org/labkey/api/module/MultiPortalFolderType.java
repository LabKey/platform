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
package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Nov 9, 2011
 */
public abstract class MultiPortalFolderType extends DefaultFolderType
{
    public MultiPortalFolderType(String name, String description, @Nullable List<Portal.WebPart> requiredParts, @Nullable List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        super(name, description, requiredParts, preferredParts, activeModules, defaultModule);
    }

    @Override
    public boolean hasConfigurableTabs()
    {
        return true;
    }

    @Override @NotNull
    public AppBar getAppBar(ViewContext ctx, PageConfig pageConfig)
    {
        Collection<Portal.WebPart> tabs = Portal.getParts(ctx.getContainer(), FolderTab.FOLDER_TAB_PAGE_ID);

        if (tabs == null || tabs.isEmpty())
            tabs = resetDefaultTabs(ctx.getContainer());

        List<NavTree> buttons = new ArrayList<NavTree>();

        for (Portal.WebPart tab : tabs)
        {
            FolderTab folderTab = findTab(tab.getName());
            if (folderTab != null && folderTab.isVisible(ctx))
            {
                String label = folderTab.getCaption(ctx);
                NavTree nav = new NavTree(label, folderTab.getURL(ctx));
                buttons.add(nav);
                if (folderTab.isSelectedPage(ctx))
                    nav.setSelected(true);
            }
        }

        return new AppBar(getFolderTitle(ctx), ctx.getContainer().getStartURL(ctx.getUser()), buttons);
    }

    protected abstract String getFolderTitle(ViewContext context);
}
