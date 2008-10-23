/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Search;
import org.labkey.api.util.Search.SearchTermParser;
import org.labkey.api.util.Search.Searchable;
import org.labkey.api.util.SearchHit;
import org.labkey.api.view.*;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.webdav.WebdavService;
import org.labkey.wiki.model.CollaborationFolderType;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.wiki.model.WikiWebPart;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:07:21 PM
 */
public class WikiModule extends DefaultModule implements ContainerManager.ContainerListener, Searchable
{
    public static final String NAME = "Wiki";
    public static final String WEB_PART_NAME = "Wiki";
    public static final String SEARCH_DOMAIN = "wiki";

    private boolean _newInstall = false;
    private User _installerUser = null;

    Logger _log = Logger.getLogger(WikiModule.class);

    public WikiModule()
    {
        super(NAME, 8.20, "/org/labkey/wiki", false,
                new WikiWebPartFactory(),
                new WikiTOCFactory(),
                new WikiWebPartFactory(WEB_PART_NAME, "right"));
        addController("wiki", WikiController.class, "attachments");

        WikiService.register(new ServiceImpl());
    }

    public static class WikiWebPartFactory extends BaseWebPartFactory
    {
        public WikiWebPartFactory()
        {
            this(WEB_PART_NAME, null);
        }

        public WikiWebPartFactory(String name, String location)
        {
            super(name, location, true, false);
            addLegacyNames("Narrow Wiki");
        }

        public boolean isAvailable(Container c, String location)
        {
            return location.equals(getDefaultLocation());
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            Map<String, String> props = webPart.getPropertyMap();
            return new WikiWebPart(webPart.getPageId(), webPart.getIndex(), props);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart)
        {
            WebPartView v = new WikiController.CustomizeWikiPartView();
            v.addObject("webPart", webPart);

            return v;
        }
    }

    public static class WikiTOCFactory extends BaseWebPartFactory
    {
        public WikiTOCFactory()
        {
            super("Wiki TOC", "right", true, false);
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            WebPartView v = new WikiController.WikiTOC(portalCtx);
            //TODO: Should just use setters
            populateProperties(v, webPart.getPropertyMap());
            return v;
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart)
        {
            WebPartView v = new WikiController.CustomizeWikiTOCPartView();
            v.addObject("webPart", webPart);
            return v;
        }
    }

    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        ContainerManager.addContainerListener(this);
        Search.register(this);
        ModuleLoader.getInstance().registerFolderType(new CollaborationFolderType(this));
        WebdavService.addProvider(new WikiWebdavProvider());

        if (_newInstall)
        {
            Container supportContainer = ContainerManager.getDefaultSupportContainer();
            Container homeContainer = ContainerManager.getHomeContainer();

            String defaultPageName = "default";

            loadWikiContent(homeContainer, _installerUser, defaultPageName, "Welcome to LabKey Server", "/org/labkey/wiki/welcomeWiki.txt", WikiRendererType.HTML);
            loadWikiContent(supportContainer, _installerUser, defaultPageName, "Welcome to LabKey support", "/org/labkey/wiki/supportWiki.txt", WikiRendererType.RADEOX);

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

            _newInstall = false;
            _installerUser = null;
        }
    }

    public void containerCreated(Container c)
    {
    }

    // Note: Attachments are purged by AttachmentServiceImpl.containerDeleted()
    public void containerDeleted(Container c, User user)
    {
        try
        {
            WikiManager.purgeContainer(c);
        }
        catch (Throwable t)
        {
            _log.error(t);
        }
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
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

    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        if (moduleContext.getInstalledVersion() == 0.0)
        {
            _newInstall = true;
            _installerUser = viewContext.getUser();
        }

        if (moduleContext.getInstalledVersion() == 1.0)
        {
            Container root = ContainerManager.getRoot();
            if (root != null)
            {
                try
                {
                    for (Container child : ContainerManager.getAllChildren(root))
                        WikiManager.updateWikiParenting(child);
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void loadWikiContent(Container c, User user, String name, String title, String resource, WikiRendererType renderAs)
    {
        Wiki wiki = new Wiki(c, name);
        WikiVersion wikiversion = new WikiVersion();
        wikiversion.setTitle(title);

        InputStream is = getClass().getResourceAsStream(resource);
        String body = PageFlowUtil.getStreamContentsAsString(is);
        wikiversion.setBody(body);

        if (renderAs == null)
            renderAs = WikiService.get().getDefaultWikiRendererType();

        wikiversion.setRendererType(renderAs.name());

        try
        {
            WikiManager.insertWiki(user, c, wiki, wikiversion, null);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }
        catch (IOException e)
        {
            _log.error(e);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            _log.error(e);
        }
    }


    public void search(SearchTermParser parser, Set<Container> containers, List<SearchHit> hits, User user)
    {
        WikiManager.search(parser, containers, hits);
    }


    public String getSearchResultNamePlural()
    {
        return "Wiki Pages";
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }


    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            WikiManager.TestCase.class));
    }
}
