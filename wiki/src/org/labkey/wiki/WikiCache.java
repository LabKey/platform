/*
 * Copyright (c) 2007 LabKey Corporation
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

import org.labkey.api.util.Cache;
import org.labkey.api.data.Container;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.wiki.model.Wiki;

import java.util.Map;
import java.util.Collection;
import java.util.List;

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
    private static boolean useCache = "true".equals(System.getProperty("wiki.cache", "true"));
    private static final Cache _pageCache = Cache.getShared();

    private static String _cachedName(Container c, String name)
    {
        return "Pages/" + c.getId() + "/" + c.getPath() + "/" + name;
    }

    private static void cache(Container c, String mapName, Map map)
    {
        _cache(c, mapName, map);
    }

    private static void cache(Container c, String collectionName, Collection collection)
    {
        _cache(c, collectionName, collection);
    }

    static void cache(Container c, WikiManager.WikiAndVersion wikipair)
    {
        _cache(c, wikipair.getWiki().getName(), wikipair);
    }

    static void _cache(Container c, String name, Object o)
    {
        if (useCache)
            _pageCache.put(_cachedName(c, name), o, Cache.HOUR);
    }

    static void uncache(Container c)
    {
        uncacheUsingPrefix(c, "");
    }

    static void cachePageMap(Container c, Map<String, Wiki> tree)
    {
        WikiCache.cache(c, WikiCache.PAGES_NAME, tree);
    }

    static Map<String, Wiki> getCachedPageMap(Container c)
    {
        return (Map<String, Wiki>) getCached(c, WikiCache.PAGES_NAME);
    }

    static void cacheOrderedPageList(Container c, List<Wiki> list)
    {
        WikiCache.cache(c, WikiCache.ORDERED_PAGE_LIST, list);
    }

    static List<Wiki> getCachedOrderedPageList(Container c)
    {
        return (List<Wiki>) getCached(c, WikiCache.ORDERED_PAGE_LIST);
    }

    static void cacheVersionMap(Container c, Map<String, WikiRenderer.WikiLinkable> map)
    {
        WikiCache.cache(c, WikiCache.VERSIONS_NAME, map);
    }

    static Map<String, WikiRenderer.WikiLinkable> getCachedVersionMap(Container c)
    {
        return (Map<String, WikiRenderer.WikiLinkable>) WikiCache.getCached(c, WikiCache.VERSIONS_NAME);
    }

    // Not currently used -- only case where this would be the correct behavior is if we update the content of a wiki
    // and verify that the title & name didn't change.  Not worth it.
    private static void uncache(Container c, String name)
    {
        _pageCache.remove(_cachedName(c, name));
        uncacheLists(c);
    }

    private static void uncacheLists(Container c)
    {
        _pageCache.remove(_cachedName(c, ORDERED_PAGE_LIST));
        _pageCache.remove(_cachedName(c, PAGES_NAME));
        _pageCache.remove(_cachedName(c, VERSIONS_NAME));
    }

    private static void uncacheUsingPrefix(Container c, String prefix)
    {
        _pageCache.removeUsingPrefix(_cachedName(c, prefix));
        uncacheLists(c);
    }

    static Object getCached(Container c, String name)
    {
        if (c == null || name == null)
            return null;

        Object cached = null;
        if (useCache)
            cached = _pageCache.get(_cachedName(c, name));
        return cached;
    }
}