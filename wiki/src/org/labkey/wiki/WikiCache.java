/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.wiki.model.Wiki;

/**
 * User: adam
 * Date: Aug 7, 2007
 * Time: 11:37:23 AM
 */
public class WikiCache
{
    private static final String WIKI_COLLECTIONS_KEY = "~~wiki_collections~~";
    private static final boolean useCache = "true".equals(System.getProperty("wiki.cache", "true"));
    private static final BlockingStringKeyCache<Object> BLOCKING_CACHE = CacheManager.getBlockingStringKeyCache(50000, CacheManager.DAY, "Wikis and Wiki Collections", null);

    // Passing in Container as "argument" eliminates need to create loader instances when caching collections (but doesn't help with individual wikis)
    public abstract static class WikiCacheLoader<V> implements CacheLoader<String, V>
    {
        abstract V load(String key, Container c);

        @Override
        public V load(String key, Object argument)
        {
            return load(key, (Container)argument);
        }
    }

    public static Wiki getWiki(Container c, String name, WikiCacheLoader<Wiki> loader)
    {
        return get(c, name, loader);
    }

    static WikiCollections getWikiCollections(Container c, WikiCacheLoader<WikiCollections> loader)
    {
        return get(c, WIKI_COLLECTIONS_KEY, loader);
    }

    public static void uncache(Container c, Wiki wiki, boolean uncacheContainerContent)
    {
        String name = wiki.getName();
        BLOCKING_CACHE.remove(getCacheKey(c, name));

        if (uncacheContainerContent)
        {
            WikiContentCache.uncache(c);
            uncacheCollections(c);
        }
        else
        {
            WikiContentCache.uncache(c, name);
        }
    }

    // This is drastic and rarely necessary
    public static void uncache(Container c)
    {
        BLOCKING_CACHE.removeUsingPrefix(getCacheKey(c, ""));
        WikiContentCache.uncache(c);
        uncacheCollections(c);
    }

    // Private methods below

    private static String getCacheKey(Container c, String name)
    {
        // forcing cache key lookup to lower case to properly handle case-insensitive URLs, see #11625
        return "Pages/" + c.getId() + "/" + c.getPath() + "/" + name.toLowerCase();
    }

    private static <V> V get(Container c, String name, WikiCacheLoader<V> loader)
    {
        if (c == null || name == null)
            return null;

        if (useCache)
            //noinspection unchecked
            return (V)BLOCKING_CACHE.get(getCacheKey(c, name), c, (WikiCacheLoader<Object>)loader);
        else
            return loader.load(getCacheKey(c, name), c);
    }

    private static void uncacheCollections(Container c)
    {
        BLOCKING_CACHE.remove(getCacheKey(c, WIKI_COLLECTIONS_KEY));
    }
}
