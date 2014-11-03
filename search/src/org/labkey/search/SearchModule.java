/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.cache.CacheListener;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StartupListener;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.ActionResource;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
        return 14.30;
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
        return new ArrayList<WebPartFactory>(Arrays.asList(new SearchWebPartFactory("Search", null),
            new SearchWebPartFactory("Search", WebPartFactory.LOCATION_RIGHT)));
    }

    
    protected void init()
    {
        addController("search", SearchController.class);
        addController("umls", UmlsController.class);
        LuceneSearchServiceImpl ss = new LuceneSearchServiceImpl();
        ss.addResourceResolver("action", new AbstractSearchService.ResourceResolver()
        {
            public WebdavResource resolve(@NotNull String str)
            {
                return new ActionResource(str);
            }
        });
        ss.addResourceResolver("dav", new AbstractSearchService.ResourceResolver()
        {
            public WebdavResource resolve(@NotNull String path)
            {
                return WebdavService.get().lookup(path);
            }
        });
        ServiceRegistry.get().registerService(SearchService.class, ss);
        ServiceRegistry.get().registerService(DocumentConversionService.class, new DocumentConversionServiceImpl());
    }


    public void doStartup(ModuleContext moduleContext)
    {
        final SearchService ss = ServiceRegistry.get().getService(SearchService.class);

        if (null != ss)
        {
            ss.addSearchCategory(UmlsController.umlsCategory);
            AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "full-text search", new ActionURL(SearchController.AdminAction.class, null));

            // For future upgrades of Lucene or changes to indexing, update the version number below to rebuild the index.
            final boolean clearIndex = (!moduleContext.isNewInstall() && moduleContext.getOriginalVersion() < 14.11);

            // don't start the crawler until all the modules are done starting up
            ContextListener.addStartupListener(new StartupListener(){
                @Override
                public String getName()
                {
                    return "Search Service";
                }

                public void moduleStartupComplete(ServletContext servletContext)
                {
                    ss.start();

                    if (clearIndex)
                        ss.clear();

                    DavCrawler.getInstance().start();
                }
            });

            CacheManager.addListener(new CacheListener()
            {
                @Override
                public void clearCaches()
                {
                    Logger.getLogger(SearchService.class).info("Purging SearchService queues");
                    ss.purgeQueues();
                }
            });
        }

        AuditLogService.get().addAuditViewFactory(new SearchAuditViewFactory());
        AuditLogService.registerAuditType(new SearchAuditProvider());

        // add a container listener so we'll know when containers are deleted
        ContainerManager.addContainerListener(new SearchContainerListener());
    }


    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
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
                SearchService ss = ServiceRegistry.get(SearchService.class);
                ss.clearLastIndexed();
            }
        };
        ContextListener.addStartupListener(l);
    }
    

    public static final String EVENT_TYPE = "SearchAuditEvent";

    private static class SearchAuditViewFactory extends SimpleAuditViewFactory
    {
        public String getEventType()
        {
            return EVENT_TYPE;
        }

        @Override
        public String getName()
        {
            return "Search";
        }

        @Override
        public String getDescription()
        {
            return "Search queries";
        }

        @Override
        public void setupTable(FilteredTable table, UserSchema schema)
        {
            super.setupTable(table, schema);
            ColumnInfo col = table.getColumn("Key1");
            col.setLabel("Query");
        }

        @Override
        public List<FieldKey> getDefaultVisibleColumns()
        {
            List<FieldKey> columns = new ArrayList<>();
            columns.add(FieldKey.fromParts("Date"));
            columns.add(FieldKey.fromParts("CreatedBy"));
            columns.add(FieldKey.fromParts("ImpersonatedBy"));
            columns.add(FieldKey.fromParts("Key1"));
            columns.add(FieldKey.fromParts("Comment"));
            columns.add(FieldKey.fromParts("ContainerId"));
            return columns;
        }

        public QueryView createDefaultQueryView(ViewContext context)
        {
            AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
            view.setSort(new Sort("-Date"));

            return view;
        }
    }
}
