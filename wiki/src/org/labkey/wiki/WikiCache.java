/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.util.HString;
import org.labkey.api.view.NavTree;
import org.labkey.api.wiki.WikiRenderer.WikiLinkable;
import org.labkey.wiki.WikiManager.WikiAndVersion;
import org.labkey.wiki.model.Wiki;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Aug 7, 2007
 * Time: 11:37:23 AM
 */
public class WikiCache
{
    private static final String ORDERED_PAGE_LIST = "~~toc~~";
    private static final String PAGES_NAME = "~~pages~~";
    private static final String VERSIONS_NAME = "~~versions~~";
    private static final String TOC_NAME = "~~nvtoc~~";
    private static final boolean useCache = "true".equals(System.getProperty("wiki.cache", "true"));
    private static final StringKeyCache<Object> WIKI_CACHE = CacheManager.getStringKeyCache(10000, CacheManager.DAY, "Wikis, Versions, and TOC");
    private static final BlockingCache<String, Object> BLOCKING_CACHE = new BlockingCache<String, Object>(WIKI_CACHE);

    // Always passing in Container as "argument" eliminates need to create loader instances when caching lists (but doesn't help with wikis)
    public abstract static class WikiCacheLoader<V> implements CacheLoader<String, V>
    {
        abstract V load(String key, Container c);

        @Override
        public V load(String key, Object argument)
        {
            return load(key, (Container)argument);
        }
    }

    public static WikiAndVersion getWikiAndVersion(Container c, String name, WikiCacheLoader<WikiAndVersion> loader)
    {
        return get(c, name, loader);
    }

    static Map<HString, Wiki> getPageMap(Container c, WikiCacheLoader<Map<HString, Wiki>> loader) throws SQLException
    {
        return get(c, PAGES_NAME, loader);
    }

    static List<Wiki> getOrderedPageList(Container c, WikiCacheLoader<List<Wiki>> loader)
    {
        return get(c, ORDERED_PAGE_LIST, loader);
    }

    static Map<HString, WikiLinkable> getVersionMap(Container c, WikiCacheLoader<Map<HString, WikiLinkable>> loader)
    {
        return get(c, VERSIONS_NAME, loader);
    }

    static NavTree[] getNavTree(Container c, WikiCacheLoader<NavTree[]> loader)
    {
        return get(c, TOC_NAME, loader);
    }

    public static void uncache(Container c, Wiki wiki, boolean uncacheContainerContent)
    {
        String name = wiki.getName().getSource();
        WIKI_CACHE.remove(_cachedName(c, name));

        if (uncacheContainerContent)
        {
            WikiContentCache.uncache(c);
            uncacheLists(c);
        }
        else
        {
            WikiContentCache.uncache(c, name);
        }
    }

    // This is drastic and rarely necessary
    public static void uncache(Container c)
    {
        WIKI_CACHE.removeUsingPrefix(_cachedName(c, ""));
        WikiContentCache.uncache(c);
        uncacheLists(c);
    }

    // Private methods below

    private static String _cachedName(Container c, String name)
    {
        return "Pages/" + c.getId() + "/" + c.getPath() + "/" + name;
    }

    private static <V> V get(Container c, String name, WikiCacheLoader<V> loader)
    {
        if (c == null || name == null)
            return null;

        if (useCache)
            //noinspection unchecked
            return (V)BLOCKING_CACHE.get(_cachedName(c, name), c, (WikiCacheLoader<Object>)loader);
        else
            return loader.load(_cachedName(c, name), c);
    }

    private static void uncacheLists(Container c)
    {
        WIKI_CACHE.remove(_cachedName(c, ORDERED_PAGE_LIST));
        WIKI_CACHE.remove(_cachedName(c, PAGES_NAME));
        WIKI_CACHE.remove(_cachedName(c, VERSIONS_NAME));
        WIKI_CACHE.remove(_cachedName(c, TOC_NAME));
    }
}
