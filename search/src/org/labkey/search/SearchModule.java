/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.AdminConsole;
import org.labkey.search.model.AbstractSearchService;
import org.labkey.search.model.LuceneSearchServiceImpl;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.io.FilenameFilter;
import java.io.File;


public class SearchModule extends DefaultModule
{
    public final static String searchRunningState = "runningState";
    
    
    public String getName()
    {
        return "Search";
    }

    public double getVersion()
    {
        return 0.04;
    }

    public boolean hasScripts()
    {
        return true;
    }


    @Override
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("search");
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
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            Map<String,String> m = PropertyManager.getProperties(SearchModule.class.getName(), true);
            boolean running = !AppProps.getInstance().isDevMode();
            if (m.containsKey(searchRunningState))
                running = "true".equals(m.get(searchRunningState));

            // UNDONE: start the service AFTER all the other modules have had a chance to register DocumentProviders
            if (running)
                ss.start();

            AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "indexer", new ActionURL(SearchController.AdminAction.class, null));
        }


        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SearchContainerListener());
    }


    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }


    @Override
    // Custom filter to avoid flagging Bouncy Castle jar
    protected FilenameFilter getJarFilenameFilter()
    {
        return new FilenameFilter() {
            public boolean accept(File dir, String name)
            {
                return name.endsWith(".jar") && !name.startsWith("bcmail-jdk15");
            }
        };
    }
}