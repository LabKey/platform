/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.view.Portal.WebPart;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * In-memory cache of the {@link WebPart}s configured for specific {@link Container}s.
 * User: adam
 * Date: 10/21/11
 */
public class WebPartCache
{
    private static final StringKeyCache<Map<String, Portal.PortalPage>> CACHE = CacheManager.getStringKeyCache(10000, CacheManager.DAY, "Webparts");

    static public Portal.PortalPage getPortalPage(@NotNull Container c, @NotNull String pageId)
    {
        Map<String, Portal.PortalPage> pages = get(c);
        return null == pages ? null : pages.get(pageId);
    }


    @NotNull
    static Map<String, Portal.PortalPage> getPages(Container c, boolean showHidden)
    {
        Map<String, Portal.PortalPage> pages = get(c);
        if (null == pages)
            pages = new CaseInsensitiveHashMap<>();
        if (showHidden)
            return pages;
        CaseInsensitiveHashMap<Portal.PortalPage> ret = new CaseInsensitiveHashMap<>();
        for (Portal.PortalPage page : pages.values())
            if (!page.isHidden())
                ret.put(page.getPageId(), page);
        return ret;
    }


    @NotNull
    static Collection<WebPart> getWebParts(@NotNull Container c, @NotNull String pageId)
    {
        Portal.PortalPage page = getPortalPage(c,pageId);
        return null == page ? new ArrayList<>() : page.getWebParts().values();
    }


    static WebPart getWebPart(@NotNull Container c, @NotNull String pageId, int index)
    {
        Portal.PortalPage page = getPortalPage(c, pageId);
        if (null == page)
            return null;
        return page.getWebParts().get(index);
    }


    static CacheLoader<String, Map<String, Portal.PortalPage>> _webpartLoader = new CacheLoader<String, Map<String, Portal.PortalPage>>()
    {
        @Override
        public Map<String, Portal.PortalPage> load(String containerId, Object o)
        {
            DbSchema schema = CoreSchema.getInstance().getSchema();
            final CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>();

            SQLFragment selectPages = new SQLFragment("SELECT * FROM " + Portal.getTableInfoPortalPages().getSelectName() + " WHERE Container = ? ORDER BY \"index\"", containerId);
            Collection<Portal.PortalPage> pagesSelect = new SqlSelector(schema, selectPages).getCollection(Portal.PortalPage.class);

            for (Portal.PortalPage p : pagesSelect)
            {
                if (null == p.getEntityId())
                {
                    GUID g = new GUID();
                    SQLFragment updateEntityId = new SQLFragment("UPDATE " + Portal.getTableInfoPortalPages().getSelectName() + " SET EntityId = ? WHERE Container = ? AND PageId = ? AND EntityId IS NULL",
                            g, containerId, p.getPageId());
                    new SqlExecutor(schema).execute(updateEntityId);
                    p.setEntityId(g);
                }
                pages.put(p.getPageId(), p);
            }

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), containerId);

            new TableSelector(Portal.getTableInfoPortalWebParts(), filter, new Sort("Index")).forEach(new Selector.ForEachBlock<WebPart>()
            {
                @Override
                public void exec(WebPart wp) throws SQLException
                {
                    Portal.PortalPage p = pages.get(wp.getPageId());
                    if (null != p)
                        p.addWebPart(wp);
                }
            }, WebPart.class);

            return Collections.unmodifiableMap(pages);
        }
    };


    private static Map<String, Portal.PortalPage> get(@NotNull final Container c)
    {
        return CACHE.get(c.getId(), c, _webpartLoader);
    }

    static void remove(Container c)
    {
        CACHE.remove(c.getId());
    }
}
