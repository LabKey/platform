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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.mbean.LabKeyManagement;
import org.labkey.api.mbean.SearchMXBean;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderManagement;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.audit.SearchAuditProvider;
import org.labkey.search.model.AbstractSearchService;
import org.labkey.search.model.DavCrawler;
import org.labkey.search.model.LuceneSearchServiceImpl;
import org.labkey.search.model.PlainTextDocumentParser;
import org.labkey.search.model.SearchStartupProperties;
import org.labkey.search.view.SearchWebPartFactory;

import javax.management.StandardMBean;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;


public class SearchModule extends DefaultModule
{
    private static final Logger LOG = LogHelper.getLogger(SearchModule.class, "Search module startup issues");

    @Override
    public String getName()
    {
        return "Search";
    }

    @Override
    public Double getSchemaVersion()
    {
        return 24.001;
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

        LabKeyManagement.register(new StandardMBean(ss, SearchMXBean.class, true), "Search");

        ss.addResourceResolver("dav", new AbstractSearchService.ResourceResolver()
        {
            @Override
            public WebdavResource resolve(@NotNull String path)
            {
                return WebdavService.get().lookup(path);
            }

            @Override
            public HttpView<?> getCustomSearchResult(User user, @NotNull String resourceIdentifier)
            {
                return null;
            }
        });
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        ModuleLoader.getInstance().handleStartupProperties(
            new StandardStartupPropertyHandler<>("SearchSettings", SearchStartupProperties.class)
            {
                @Override
                public void handle(Map<SearchStartupProperties, StartupPropertyEntry> properties)
                {
                    SearchService ss = SearchService.get();
                    if (null == ss)
                        LOG.error("Search service is not present");
                    else
                        properties.forEach((ssp, sp) -> {
                            try
                            {
                                ssp.setProperty(ss, _searchIndexStartupHandler, sp.getValue());
                            }
                            catch (Exception e)
                            {
                                LOG.error("Exception while attempting to set startup property", e);
                            }
                        });
                }
            }
        );

        final SearchService ss = SearchService.get();

        if (null != ss)
        {
            AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "full-text search", new ActionURL(SearchController.AdminAction.class, null));

            CacheManager.addListener(() -> {
                SearchService._log.info("Purging SearchService queues");
                ss.purgeQueues();
            });

            ss.addDocumentParser(new PlainTextDocumentParser());
        }

        AuditLogService.get().registerAuditType(new SearchAuditProvider());

        // add a container listener, so we'll know when containers are deleted
        ContainerManager.addContainerListener(new SearchContainerListener());

        FolderManagement.addTab(FolderManagement.TYPE.FolderManagement, "Search", "fullTextSearch", FolderManagement.NOT_ROOT, SearchController.SearchSettingsAction.class);

        UsageMetricsService.get().registerUsageMetrics(getName(), () ->
        {
            // Report the total number of search entries in the audit log
            User user = new LimitedUser(User.getSearchUser(), CanSeeAuditLogRole.class);
            UserSchema auditSchema = AuditLogService.get().createSchema(user, ContainerManager.getRoot());
            TableInfo auditTable = auditSchema.getTableOrThrow(SearchAuditProvider.EVENT_TYPE, ContainerFilter.EVERYTHING);

            long count = new TableSelector(auditTable).getRowCount();
            return Collections.singletonMap("fullTextSearches", count);
        });
    }

    @Override
    public void startBackgroundThreads()
    {
        SearchService ss = SearchService.get();

        if (null != ss)
        {
            // Execute any reindexing operations in the background to not block startup, Issue #48960
            JobRunner.getDefault().execute(() -> {
                _searchIndexStartupHandler.reindexIfNeeded(ss);
                ss.start();
                DavCrawler.getInstance().start();
            });
        }
    }

    private final SearchIndexStartupHandler _searchIndexStartupHandler = new SearchIndexStartupHandler();

    public static class SearchIndexStartupHandler
    {
        private volatile boolean _deleteIndex = false;
        private volatile boolean _indexFull = false;

        private final Queue<String> _deleteIndexReasons = new ConcurrentLinkedQueue<>();
        private final Queue<String> _indexFullReasons = new ConcurrentLinkedQueue<>();

        public void setDeleteIndex(String reason)
        {
            _deleteIndex = true;
            _deleteIndexReasons.add(reason);
        }

        public void setIndexFull(String reason)
        {
            _indexFull = true;
            _indexFullReasons.add(reason);
        }

        public void reindexIfNeeded(@NotNull SearchService ss)
        {
            if (_deleteIndex)
            {
                LOG.info("Deleting full-text search index and clearing last indexed because: " + _deleteIndexReasons);
                ss.deleteIndex();
            }
            if (_indexFull)
            {
                LOG.info("Initiating an aggressive full-text search reindex because: " + _indexFullReasons);
                ss.indexFull(true);
            }
        }
    };

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        // After every search module upgrade, delete the index and clear the last indexed time on all documents
        // to rebuild the entire index, Issue #35674 & Issue #42617
        if (!moduleContext.isNewInstall() && moduleContext.needsUpgrade(getSchemaVersion()))
        {
            _searchIndexStartupHandler.setDeleteIndex("Search schema upgrade");
            _searchIndexStartupHandler.setIndexFull("Search schema upgrade");
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
