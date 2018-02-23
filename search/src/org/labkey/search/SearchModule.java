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

package org.labkey.search;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderManagement;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.audit.SearchAuditProvider;
import org.labkey.search.model.AbstractSearchService;
import org.labkey.search.model.DavCrawler;
import org.labkey.search.model.DocumentConversionServiceImpl;
import org.labkey.search.model.LuceneSearchServiceImpl;
import org.labkey.search.umls.UmlsController;
import org.labkey.search.view.SearchWebPartFactory;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


public class SearchModule extends DefaultModule
{
    public String getName()
    {
        return "Search";
    }

    public double getVersion()
    {
        return 18.10;
    }

    public boolean hasScripts()
    {
        return true;
    }


    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set("search", "umls");
    }


    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        // Should test the "search" schema, but it differs between SQL Server & PostgreSQL
        return Collections.emptySet();
    }


    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Collections.singletonList(new SearchWebPartFactory()));
    }

    
    protected void init()
    {
        addController("search", SearchController.class);
        addController("umls", UmlsController.class);
        LuceneSearchServiceImpl ss = new LuceneSearchServiceImpl();
        SearchService.setInstance(ss);
        ss.addResourceResolver("dav", new AbstractSearchService.ResourceResolver()
        {
            public WebdavResource resolve(@NotNull String path)
            {
                return WebdavService.get().lookup(path);
            }

            @Override
            public HttpView getCustomSearchResult(User user, @NotNull String resourceIdentifier)
            {
                return null;
            }
        });
        ServiceRegistry.get().registerService(DocumentConversionService.class, new DocumentConversionServiceImpl());
    }


    public void doStartup(ModuleContext moduleContext)
    {
        final SearchService ss = SearchService.get();

        if (null != ss)
        {
            ss.addSearchCategory(UmlsController.umlsCategory);
            AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "full-text search", new ActionURL(SearchController.AdminAction.class, null));

            // Update the version number below to force an upgrade of the index to the latest format. This is used when we upgrade Lucene (the indexing library) to a new version.
            final boolean upgradeIndex = (!moduleContext.isNewInstall() && moduleContext.getOriginalVersion() < 17.21);

            // Update the version number below to force a clear and rebuild of the index. This is used when we change our indexing content or methodology.
            final boolean clearIndex = (!moduleContext.isNewInstall() && moduleContext.getOriginalVersion() < 17.21);

            // don't start the crawler or clear the index until all the modules are done starting up
            ContextListener.addStartupListener(new StartupListener()
            {
                @Override
                public String getName()
                {
                    return "Search Service";
                }

                public void moduleStartupComplete(ServletContext servletContext)
                {
                    if (upgradeIndex)
                    {
                        // Must call upgradeIndex() before we start the SearchService
                        ss.upgradeIndex();
                    }

                    if (clearIndex)
                    {
                        // Legal to call clear() before we start the SearchService
                        ss.clear();
                    }
                }
            });

            CacheManager.addListener(() -> {
                Logger.getLogger(SearchService.class).info("Purging SearchService queues");
                ss.purgeQueues();
            });
        }

        AuditLogService.get().registerAuditType(new SearchAuditProvider());

        // add a container listener so we'll know when containers are deleted
        ContainerManager.addContainerListener(new SearchContainerListener());

        FolderManagement.addTab("Search", "fullTextSearch", FolderManagement.NOT_ROOT, SearchController.SearchSettingsAction.class);
    }


    @Override
    public void startBackgroundThreads()
    {
        SearchService ss = SearchService.get();

        if (null != (ss))
        {
            ss.start();
            DavCrawler.getInstance().start();
        }
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        // we want to clear the last indexed time on all documents so that failed attempts can be tried again
        final StartupListener l = new StartupListener()
        {
            @Override
            public String getName()
            {
                return "Search Service: clear indexes";
            }

            public void moduleStartupComplete(ServletContext servletContext)
            {
                SearchService ss = SearchService.get();

                if (null != ss)
                    ss.clearLastIndexed();
            }
        };
        ContextListener.addStartupListener(l);
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return PageFlowUtil.set
        (
            LuceneSearchServiceImpl.TikaTestCase.class,
            LuceneSearchServiceImpl.TestCase.class
        );
    }

    // Special loading for search resources: gradle build pushes Tika JARs into deploy directory only, so need to
    // include that if in dev mode. #30598
    @Override
    public @NotNull List<File> getResourceDirectories()
    {
        List<File> dirs = super.getResourceDirectories();

        if (AppProps.getInstance().isDevMode())
        {
            File exploded = getExplodedPath();

            if (exploded != null && exploded.isDirectory())
            {
                // super might have already added deploy location (e.g., module with null sourcePath), so de-dupe with set. #30614
                Set<File> dirSet = new HashSet<>(dirs);
                dirSet.addAll(getResourceDirectory(exploded));
                dirs = new LinkedList<>(dirSet);
            }
        }

        return dirs;
    }
}
