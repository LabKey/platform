/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.search;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.search.SearchService;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.util.Path;
import org.labkey.search.model.LuceneSearchServiceImpl;
import org.labkey.search.model.AbstractSearchService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class SearchModule extends DefaultModule
{
    public String getName()
    {
        return "Search";
    }

    public double getVersion()
    {
        return 0.02;
    }

    public boolean hasScripts()
    {
        return false;
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    protected void init()
    {
        addController("search", SearchController.class);
        LuceneSearchServiceImpl ss = new LuceneSearchServiceImpl();
        ss.addResourceResolver("action", new AbstractSearchService.ResourceResolver()
        {
            public Resource resolve(@NotNull String str)
            {
                return new ActionResource(str);
            }
        });
        ss.addResourceResolver("dav", new AbstractSearchService.ResourceResolver()
        {
            public Resource resolve(@NotNull String path)
            {
                return WebdavService.get().lookup(path);
            }
        });
        ServiceRegistry.get().registerService(SearchService.class, ss);
    }


    public void startup(ModuleContext moduleContext)
    {
        ((LuceneSearchServiceImpl)ServiceRegistry.get().getService(SearchService.class)).start();

        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SearchContainerListener());
    }


    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }
}