/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.wiki.export.WikiImporterFactory;
import org.labkey.wiki.export.WikiWriterFactory;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiType;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.query.WikiSchema;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides a simple wiki with multiple rendering engine options.
 * User: migra
 * Date: Jul 18, 2005
 */
public class WikiModule extends CodeOnlyModule implements SearchService.DocumentProvider
{
    public static final String WEB_PART_NAME = "Wiki";

    // package logger for use with logger-manage.view
    private static final Logger _logPackage = LogManager.getLogger(WikiModule.class.getPackage().getName());

    private static final Logger _log = LogManager.getLogger(WikiModule.class);

    @Override
    public String getName()
    {
        return "Wiki";
    }

    @Override
    protected void init()
    {
        addController("wiki", WikiController.class, "attachments");

        WikiService.setInstance(WikiManager.get());

        AttachmentService.get().registerAttachmentType(WikiType.get());
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new MenuWikiWebPartFactory(),
            new WikiTOCFactory(),
            new WikiWebPartFactory()
        );
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(new WikiContainerListener());
//        WebdavService.get().addProvider(new WikiWebdavProvider());

        // Ideally, this would be in afterUpdate(), but announcements runs the wiki sql scripts and is dependent on
        // wiki module, so no dice.
        populateHomeProjectWebpartsWithStartupProps();
        if (ModuleLoader.getInstance().isNewInstall())
            bootstrap(moduleContext);

        SearchService ss = SearchService.get();
        if (null != ss)
        {
            ss.addSearchCategory(WikiManager.searchCategory);
            ss.addDocumentProvider(this);
        }

        FolderSerializationRegistry.get().addFactories(new WikiWriterFactory(), new WikiImporterFactory());

        WikiSchema.register(this);
        WikiController.registerAdminConsoleLinks();
    }

    // TODO should this move to CoreModule.populateLookAndFeelWithStartupProps()?
    private void populateHomeProjectWebpartsWithStartupProps()
    {
        String propName = "homeProjectInitWebparts";
        if (ModuleLoader.getInstance().isNewInstall())
        {
            Collection<ConfigProperty> startupProps = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_LOOK_AND_FEEL_SETTINGS);
            startupProps.forEach(prop ->
            {
                if (propName.equalsIgnoreCase(prop.getName()) && prop.getModifier() == ConfigProperty.modifier.bootstrap)
                {
                    for (String webpartName : StringUtils.split(prop.getValue(), ';'))
                    {
                        WebPartFactory webPartFactory = Portal.getPortalPart(webpartName);
                        if (webPartFactory != null)
                            Portal.registerHomeProjectInitWebpart(webPartFactory);
                    }
                }
            });
        }
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        Container supportContainer = ContainerManager.getDefaultSupportContainer();
        Container homeContainer = ContainerManager.getHomeContainer();
        Container sharedContainer = ContainerManager.getSharedContainer();
        String defaultPageName = "default";

        if (moduleContext.isNewInstall())
        {
            loadWikiContent(homeContainer, moduleContext.getUpgradeUser(), defaultPageName, "Welcome to LabKey Server", "/org/labkey/wiki/welcomeWiki.txt");
            loadWikiContent(supportContainer, moduleContext.getUpgradeUser(), defaultPageName, "Welcome to LabKey Support", "/org/labkey/wiki/supportWiki.txt");
            loadWikiContent(sharedContainer, moduleContext.getUpgradeUser(), defaultPageName, "Shared Resources", "/org/labkey/wiki/sharedWiki.txt");
        }

        addWebPart(supportContainer, defaultPageName);
        addWebPart(sharedContainer, defaultPageName);

        // if any modules have registered webparts to show on the home page, use those
        // otherwise, just add the default wiki webpart
        if (!Portal.getHomeProjectInitWebparts().isEmpty())
        {
            // Clear existing webpart(s) first -- Core module added the Projects webpart
            Portal.saveParts(homeContainer, Collections.emptyList());
            for (WebPartFactory webPartFactory : Portal.getHomeProjectInitWebparts())
                addWebPart(webPartFactory.getName(), homeContainer, HttpView.BODY);
        }
        else
        {
            // Note: Core module already added the Projects webpart. Now add a wiki webpart with the default content.
            addWebPart(homeContainer, defaultPageName);
        }
    }

    private void addWebPart(@Nullable Container c, String wikiName)
    {
        if (c != null)
        {
            Map<String, String> wikiProps = new HashMap<>();
            wikiProps.put("webPartContainer", c.getId());
            wikiProps.put("name", wikiName);
            addWebPart(WEB_PART_NAME, c, HttpView.BODY, 0, wikiProps);
        }
    }

    @Override
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

    private void loadWikiContent(@Nullable Container c, User user, String name, String title, String resource)
    {
        if (c != null)
        {
            Wiki wiki = new Wiki(c, name);
            WikiVersion wikiversion = new WikiVersion();
            wikiversion.setTitle(title);

            InputStream is = getClass().getResourceAsStream(resource);
            String body = PageFlowUtil.getStreamContentsAsString(is);
            wikiversion.setBody(body);

            wikiversion.setRendererTypeEnum(WikiRendererType.HTML);

            try
            {
                getWikiManager().insertWiki(user, c, wiki, wikiversion, null, false, null);
            }
            catch (IOException e)
            {
                _log.error("Failed to insert wiki in " + c.getPath() + " from " + resource, e);
            }
        }
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Set.of(
            WikiManager.TestCase.class
        );
    }


    @Override
    public void enumerateDocuments(final SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable r = () -> getWikiManager().indexWikis(task, c, modifiedSince, null);
        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }


    @Override
    public void indexDeleted()
    {
        new SqlExecutor(CommSchema.getInstance().getSchema()).execute("UPDATE comm.pages SET lastIndexed=NULL");
    }


    private WikiManager getWikiManager()
    {
        return WikiManager.get();
    }
}
