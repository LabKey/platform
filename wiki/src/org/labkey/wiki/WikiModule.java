/*
 * Copyright (c) 2005-2015 Fred Hutchinson Cancer Research Center
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
package org.labkey.wiki;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.DeveloperRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.wiki.export.WikiImporterFactory;
import org.labkey.wiki.export.WikiWriterFactory;
import org.labkey.wiki.model.CollaborationFolderType;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.permissions.IncludeScriptPermission;
import org.labkey.wiki.query.WikiSchema;
import org.labkey.wiki.renderer.RadeoxRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:07:21 PM
 */
public class WikiModule extends DefaultModule implements SearchService.DocumentProvider
{
    public static final String WEB_PART_NAME = "Wiki";

    private static final Logger _log = Logger.getLogger(WikiModule.class);

    public String getName()
    {
        return "Wiki";
    }

    public double getVersion()
    {
        return 15.20;
    }

    protected void init()
    {
        addController("wiki", WikiController.class, "attachments");

        ServiceRegistry.get().registerService(WikiService.class, WikiManager.get());

    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(new WikiWebPartFactory(),
                new WikiTOCFactory(),
                new MenuWikiWebPartFactory()));
    }

    public boolean hasScripts()
    {
        return false;
    }

    public void doStartup(ModuleContext moduleContext)
    {
        RoleManager.registerPermission(new IncludeScriptPermission(), false);
        RoleManager.getRole(DeveloperRole.class).addPermission(IncludeScriptPermission.class);
        
        ContainerManager.addContainerListener(new WikiContainerListener());
        FolderTypeManager.get().registerFolderType(this, new CollaborationFolderType());
        WebdavService.get().addProvider(new WikiWebdavProvider());

        // Ideally, this would be in afterUpdate(), but announcements runs the wiki sql scripts and is dependent on
        // wiki module, so no dice.
        if (moduleContext.isNewInstall())
            bootstrap(moduleContext);

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            ss.addSearchCategory(WikiManager.searchCategory);
            ss.addDocumentProvider(this);
        }

        ServiceRegistry.get().getService(FolderSerializationRegistry.class).addFactories(new WikiWriterFactory(), new WikiImporterFactory());

        WikiSchema.register(this);
        WikiController.registerAdminConsoleLinks();
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        Container supportContainer = ContainerManager.createDefaultSupportContainer();
        Container homeContainer = ContainerManager.getHomeContainer();

        FolderType collaborationType = FolderTypeManager.get().getFolderType(CollaborationFolderType.TYPE_NAME);

        supportContainer.setFolderType(collaborationType, moduleContext.getUpgradeUser());
        homeContainer.setFolderType(collaborationType, moduleContext.getUpgradeUser());

        String defaultPageName = "default";

        loadWikiContent(homeContainer, moduleContext.getUpgradeUser(), defaultPageName, "Welcome to LabKey Server", "/org/labkey/wiki/welcomeWiki.txt", WikiRendererType.HTML);
        loadWikiContent(supportContainer,  moduleContext.getUpgradeUser(), defaultPageName, "Welcome to LabKey support", "/org/labkey/wiki/supportWiki.txt", WikiRendererType.RADEOX);

        Map<String, String> homeProps = new HashMap<>();
        homeProps.put("webPartContainer", homeContainer.getId());
        homeProps.put("name", defaultPageName);
        addWebPart(WEB_PART_NAME, homeContainer, HttpView.BODY, homeProps);

        addWebPart("Projects", homeContainer, HttpView.BODY, 1);

        Map<String, String> supportProps = new HashMap<>();
        supportProps.put("webPartContainer", supportContainer.getId());
        supportProps.put("name", defaultPageName);
        addWebPart(WEB_PART_NAME, supportContainer, HttpView.BODY, supportProps);
    }

    @NotNull
    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<>();
        try
        {
            int count = WikiSelectManager.getPageCount(c);
            if (count > 0)
                list.add("" + count + " Wiki Page" + (count > 1 ? "s" : ""));
        }
        catch (Exception x)
        {
            list.add(x.toString());
        }
        return list;
    }

    private void loadWikiContent(Container c, User user, String name, String title, String resource, WikiRendererType renderAs)
    {
        Wiki wiki = new Wiki(c, new HString(name));
        WikiVersion wikiversion = new WikiVersion();
        wikiversion.setTitle(new HString(title));

        InputStream is = getClass().getResourceAsStream(resource);
        String body = PageFlowUtil.getStreamContentsAsString(is, StandardCharsets.UTF_8);
        wikiversion.setBody(body);

        if (renderAs == null)
            renderAs = WikiManager.DEFAULT_WIKI_RENDERER_TYPE;

        wikiversion.setRendererTypeEnum(renderAs);

        try
        {
            getWikiManager().insertWiki(user, c, wiki, wikiversion, null);
        }
        catch (SQLException | IOException e)
        {
            _log.error(e);
        }
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<Class>(Arrays.asList(
                WikiManager.TestCase.class,
                RadeoxRenderer.RadeoxRenderTest.class));
    }


    public void enumerateDocuments(final SearchService.IndexTask task, final Container c, final Date modifiedSince)
    {
        Runnable r = new Runnable()
            {
                public void run()
                {
                    getWikiManager().indexWikis(task, c, modifiedSince);
                }
            };
        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }


    public void indexDeleted()
    {
        new SqlExecutor(CommSchema.getInstance().getSchema()).execute("UPDATE comm.pages SET lastIndexed=NULL");
    }


    private WikiManager getWikiManager()
    {
        return WikiManager.get();
    }
}
