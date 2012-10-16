/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.util.GUID;
import org.labkey.api.view.Portal.WebPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * User: adam
 * Date: 10/21/11
 * Time: 10:45 PM
 */
public class WebPartCache
{
    private static final BlockingStringKeyCache<LinkedHashMap<String,Portal.PortalPage>> CACHE = CacheManager.getBlockingStringKeyCache(10000, CacheManager.DAY, "Webparts", null);

    static public Portal.PortalPage getPortalPage(@NotNull Container c, @NotNull String pageId)
    {
        LinkedHashMap<String,Portal.PortalPage> pages = get(c);
        return null == pages ? null : pages.get(pageId);
    }


    static LinkedHashMap<String,Portal.PortalPage> getPages(Container c, boolean showHidden)
    {
        LinkedHashMap<String,Portal.PortalPage> pages = get(c);
        if (null == pages)
            new LinkedHashMap<String, Portal.PortalPage>();
        if (showHidden)
            return pages;
        LinkedHashMap<String, Portal.PortalPage> ret = new LinkedHashMap<String, Portal.PortalPage>();
        for (Portal.PortalPage page : pages.values())
            if (!page.isHidden())
                ret.put(page.getPageId(), page);
        return ret;
    }


    @NotNull
    static Collection<WebPart> getWebParts(@NotNull Container c, @NotNull String pageId)
    {
        Portal.PortalPage page = getPortalPage(c,pageId);
        return null == page ? new ArrayList<WebPart>() : page.getWebParts().values();
    }


    static WebPart getWebPart(@NotNull Container c, @NotNull String pageId, int index)
    {
        Portal.PortalPage page = getPortalPage(c, pageId);
        if (null == page)
            return null;
        return page.getWebParts().get(index);
    }


    static CacheLoader _webpartLoader = new CacheLoader<String, LinkedHashMap<String,Portal.PortalPage>>()
    {
        @Override
        public LinkedHashMap<String,Portal.PortalPage> load(String containerId, Object o)
        {
            DbSchema schema = CoreSchema.getInstance().getSchema();
            LinkedHashMap<String,Portal.PortalPage> pages = new LinkedHashMap<String, Portal.PortalPage>();

            SQLFragment selectPages = new SQLFragment("SELECT * FROM " + Portal.getTableInfoPortalPages().getSelectName() + " WHERE Container = ? ORDER BY \"index\"", containerId);
            Collection<Portal.PortalPage> pagesSelect = new SqlSelector(schema, selectPages).getCollection(Portal.PortalPage.class);
            for (Portal.PortalPage p : pagesSelect)
            {
                if (null == p.getEntityId())
                {
                    GUID g = new GUID();
                    SQLFragment updateEntityId = new SQLFragment("UPDATE " + Portal.getTableInfoPortalPages().getSelectName() + " SET EntityId = ? WHERE Container = ? AND PageId = ? AND EntityId IS NULL",
                            g, containerId, p.getPageId());
                    new SqlExecutor(schema,updateEntityId).execute();
                    p.setEntityId(g);
                }
                pages.put(p.getPageId(), p);
            }

            SimpleFilter filter = new SimpleFilter("Container", containerId);
            ArrayList<WebPart> list = new ArrayList<WebPart>(new TableSelector(Portal.getTableInfoPortalWebParts(), filter, new Sort("Index")).getCollection(WebPart.class));

            for (WebPart wp : list)
            {
                Portal.PortalPage p = pages.get(wp.getPageId());
                if (null != p)
                    p.addWebPart(wp);
            }

            return pages;
        }
    };


    private static LinkedHashMap<String,Portal.PortalPage> get(@NotNull final Container c)
    {
        return CACHE.get(c.getId(), c, _webpartLoader);
    }


    static void remove(Container c, String pageId)
    {
        CACHE.remove(c.getId());
    }


    static void remove(Container c)
    {
        CACHE.remove(c.getId());
    }
}
