/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.FolderType;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.DeveloperRole;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Search;
import org.labkey.api.util.HString;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.search.SearchService;
import org.labkey.wiki.model.CollaborationFolderType;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.permissions.IncludeScriptPermission;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:07:21 PM
 */
public class WikiModule extends DefaultModule
{
    public static final String WEB_PART_NAME = "Wiki";

    private static final Logger _log = Logger.getLogger(WikiModule.class);

    public String getName()
    {
        return "Wiki";
    }

    public double getVersion()
    {
        return 9.20;
    }

    protected void init()
    {
        addController("wiki", WikiController.class, "attachments");

        ServiceRegistry.get().registerService(WikiService.class, new ServiceImpl());
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(new WikiWebPartFactory(),
                new WikiTOCFactory(),
                new WikiWebPartFactory(WEB_PART_NAME, "right"),
                new MenuWikiWebPartFactory());
    }

    public boolean hasScripts()
    {
        return false;
    }

    public void startup(ModuleContext moduleContext)
    {
        RoleManager.registerPermission(new IncludeScriptPermission(), false);
        RoleManager.getRole(DeveloperRole.class).addPermission(IncludeScriptPermission.class);
        
        ContainerManager.addContainerListener(new WikiContainerListener());
        Search.register(new WikiSearchable());
        ModuleLoader.getInstance().registerFolderType(new CollaborationFolderType());
        WebdavService.addProvider(new WikiWebdavProvider());

        // Ideally, this would be in afterUpdate(), but announcements runs the wiki sql scripts and is dependent on
        // wiki module, so no dice.
        if (moduleContext.isNewInstall())
            bootstrap(moduleContext);

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
            ss.addSearchCategory(WikiManager.searchCategory);
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        Container supportContainer = ContainerManager.getDefaultSupportContainer();
        Container homeContainer = ContainerManager.getHomeContainer();

        FolderType collaborationType = ModuleLoader.getInstance().getFolderType(CollaborationFolderType.TYPE_NAME);
        try
        {
            supportContainer.setFolderType(collaborationType);
            homeContainer.setFolderType(collaborationType);
        }
        catch (SQLException e)
        {
            _log.error("Error setting folder type", e);
        }

        String defaultPageName = "default";

        loadWikiContent(homeContainer, moduleContext.getUpgradeUser(), defaultPageName, "Welcome to LabKey Server", "/org/labkey/wiki/welcomeWiki.txt", WikiRendererType.HTML);
        loadWikiContent(supportContainer,  moduleContext.getUpgradeUser(), defaultPageName, "Welcome to LabKey support", "/org/labkey/wiki/supportWiki.txt", WikiRendererType.RADEOX);

        try
        {
            Map<String, String> homeProps = new HashMap<String, String>();
            homeProps.put("webPartContainer", homeContainer.getId());
            homeProps.put("name", defaultPageName);
            addWebPart(WEB_PART_NAME, homeContainer, HttpView.BODY, homeProps);

            Map<String, String> supportProps = new HashMap<String, String>();
            supportProps.put("webPartContainer", supportContainer.getId());
            supportProps.put("name", defaultPageName);
            addWebPart(WEB_PART_NAME, supportContainer, HttpView.BODY, supportProps);
        }
        catch (SQLException e)
        {
            _log.error("Unable to set up support folder", e);
        }
    }

    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<String>();
        try
        {
            long count = WikiManager.getWikiCount(c);
            if (count > 0)
                list.add("" + count + " Wiki Page" + (count > 1 ? "s" : ""));
        }
        catch (SQLException x)
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
        String body = PageFlowUtil.getStreamContentsAsString(is);
        wikiversion.setBody(body);

        if (renderAs == null)
            renderAs = ServiceImpl.DEFAULT_WIKI_RENDERER_TYPE;

        wikiversion.setRendererType(renderAs.name());

        try
        {
            WikiManager.insertWiki(user, c, wiki, wikiversion, null);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            _log.error(e);
        }
        catch (IOException e)
        {
            _log.error(e);
        }
    }


    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            WikiManager.TestCase.class));
    }


    @Override
    public void enumerateDocuments(final SearchService.IndexTask task, final Container c, final Date modifiedSince)
    {
        Runnable r = new Runnable()
            {
                public void run()
                {
                    WikiManager.indexWikis(task, c, modifiedSince);
                }
            };
        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }
}
