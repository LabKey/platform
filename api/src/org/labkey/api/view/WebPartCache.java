/*
 * Copyright (c) 2011-2018 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;
import org.labkey.api.view.Portal.WebPart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory cache of the {@link WebPart}s configured for specific {@link Container}s.
 * User: adam
 * Date: 10/21/11
 */
public class WebPartCache
{
    private static final Logger LOG = LogManager.getLogger(WebPartCache.class);
    private static final Cache<String, Map<String, Portal.PortalPage>> CACHE = CacheManager.getStringKeyCache(10000, CacheManager.DAY, "Webparts");

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


    static CacheLoader<String, Map<String, Portal.PortalPage>> _webpartLoader = (containerId, o) ->
    {
        {
            DbSchema schema = CoreSchema.getInstance().getSchema();
            final CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>();
            Container container = ContainerManager.getForId(containerId);

            SQLFragment selectPages = new SQLFragment("SELECT * FROM " + Portal.getTableInfoPortalPages().getSelectName() + " WHERE Container = ? ORDER BY \"index\"", containerId);
            Collection<Portal.PortalPage> pagesSelect = new SqlSelector(schema, selectPages).getCollection(Portal.PortalPage.class);

            Map<Integer, Portal.PortalPage> pagesByRowId = new HashMap<>();       // For webparts to lookup
            for (Portal.PortalPage p : pagesSelect)
            {
                if (null == p.getEntityId())
                {
                    GUID g = new GUID();
                    SQLFragment updateEntityId = new SQLFragment("UPDATE " + Portal.getTableInfoPortalPages().getSelectName() + " SET EntityId = ? WHERE Container = ? AND RowId = ? AND EntityId IS NULL",
                            g, containerId, p.getRowId());
                    new SqlExecutor(schema).execute(updateEntityId);
                    p.setEntityId(g);
                }
                if (pages.containsKey(p.getPageId()) && null != container)
                    LOG.warn("Page '" + p.getPageId() + "' in container '" + container.getPath() +
                            "' is duplicated, meaning some expected web parts may be missing. Recommended to remove the page (tab), which should remove one of them, and set web parts as desired.");
                pages.put(p.getPageId(), p);
                pagesByRowId.put(p.getRowId(), p);
            }

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), containerId);

            new TableSelector(Portal.getTableInfoPortalWebParts(), filter, new Sort("Index")).forEach(WebPart.class, wp -> {
                Portal.PortalPage p = pagesByRowId.get(wp.getPortalPageId());
                if (null != p)
                {
                    p.addWebPart(wp);
                    wp.setPageId(p.getPageId());
                }
            });

            // create immutable PortalPage objects for caching
            CaseInsensitiveHashMap<Portal.PortalPage> ret = new CaseInsensitiveHashMap<>();
            for (var entry : pages.entrySet())
                ret.put(entry.getKey(), entry.getValue().create());
            return Collections.unmodifiableMap(ret);
        }
    };


    private static Map<String, Portal.PortalPage> get(@NotNull final Container c)
    {
        return CACHE.get(c.getId(), c, _webpartLoader);
    }

    public static void remove(Container c)
    {
        CoreSchema.getInstance().getSchema().getScope().addCommitTask(
                () -> CACHE.remove(c.getId()),
                DbScope.CommitTaskOption.IMMEDIATE,
                DbScope.CommitTaskOption.POSTCOMMIT);
    }
}
