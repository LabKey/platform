/*
 * Copyright (c) 2009-2020 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.labkey.api.settings.AdminConsole;
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
import org.labkey.search.view.SearchWebPartFactory;

import javax.servlet.ServletContext;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class SearchModule extends DefaultModule
{
    // package logger for use with logger-manage.view
    static final Logger _logPackage = LogManager.getLogger(SearchModule.class.getPackage().getName());

    @Override
    public String getName()
    {
        return "Search";
    }

    @Override
    public Double getSchemaVersion()
    {
        return 22.000;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }


    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set("search");
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
        return List.of(new SearchWebPartFactory());
    }

    
    @Override
    protected void init()
    {
        addController("search", SearchController.class);
        LuceneSearchServiceImpl ss = new LuceneSearchServiceImpl();
        SearchService.setInstance(ss);
        ss.addResourceResolver("dav", new AbstractSearchService.ResourceResolver()
        {
            @Override
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
        DocumentConversionService.setInstance(new DocumentConversionServiceImpl());
    }


    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        final SearchService ss = SearchService.get();

        if (null != ss)
        {
            AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "full-text search", new ActionURL(SearchController.AdminAction.class, null));

            CacheManager.addListener(() -> {
                LogManager.getLogger(SearchService.class).info("Purging SearchService queues");
                ss.purgeQueues();
            });
        }

        AuditLogService.get().registerAuditType(new SearchAuditProvider());

        // add a container listener so we'll know when containers are deleted
        ContainerManager.addContainerListener(new SearchContainerListener());

        FolderManagement.addTab(FolderManagement.TYPE.FolderManagement, "Search", "fullTextSearch", FolderManagement.NOT_ROOT, SearchController.SearchSettingsAction.class);
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
        // After every search module upgrade, delete the index and clear the last indexed time on all documents
        // to rebuild the entire index, #35674 & #42617
        if (!moduleContext.isNewInstall() && moduleContext.needsUpgrade(getSchemaVersion()))
        {
            ContextListener.addStartupListener(new StartupListener()
            {
                @Override
                public String getName()
                {
                    return "Search Service: delete index";
                }

                @Override
                public void moduleStartupComplete(ServletContext servletContext)
                {
                    SearchService ss = SearchService.get();

                    if (null != ss)
                        ss.deleteIndex();
                }
            });
        }
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return Set.of
        (
            LuceneSearchServiceImpl.TestCase.class,
            LuceneSearchServiceImpl.TikaTestCase.class
        );
    }
}
