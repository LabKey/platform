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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.util.HString;
import org.labkey.api.view.NavTree;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.wiki.model.Wiki;

import java.util.ArrayList;
import java.util.Collection;
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
        _cache(c, wikipair.getWiki().getName().getSource(), wikipair);
    }

    private static void cache(Container c, NavTree[] wikiToc)
    {
        _cache(c, TOC_NAME, wikiToc);
    }

    static void _cache(Container c, String name, Object o)
    {
        if (useCache)
            WIKI_CACHE.put(_cachedName(c, name), o, CacheManager.HOUR);
    }

    static void uncache(Container c)
    {
        uncacheUsingPrefix(c, "");
    }

    static void cachePageMap(Container c, Map<HString, Wiki> tree)
    {
        WikiCache.cache(c, WikiCache.PAGES_NAME, tree);
    }

    static Map<HString, Wiki> getCachedPageMap(Container c)
    {
        //noinspection unchecked
        return (Map<HString, Wiki>) getCached(c, WikiCache.PAGES_NAME);
    }

    static void cacheOrderedPageList(Container c, List<Wiki> list)
    {
        WikiCache.cache(c, WikiCache.ORDERED_PAGE_LIST, list);
    }

    static List<Wiki> getCachedOrderedPageList(Container c)
    {
        //noinspection unchecked
        return (List<Wiki>) getCached(c, WikiCache.ORDERED_PAGE_LIST);
    }

    static void cacheVersionMap(Container c, Map<HString, WikiRenderer.WikiLinkable> map)
    {
        WikiCache.cache(c, WikiCache.VERSIONS_NAME, map);
    }

    static Map<HString, WikiRenderer.WikiLinkable> getCachedVersionMap(Container c)
    {
        //noinspection unchecked
        return (Map<HString, WikiRenderer.WikiLinkable>) WikiCache.getCached(c, WikiCache.VERSIONS_NAME);
    }

    /**
     * Returns an array of NavTree nodes representing the Wiki table of contents. This method will
     * create the nodes if necessary, cache them, and return them.
     * @param c The Container
     * @return An array of NavTree nodes.
     */
    @NotNull
    static synchronized NavTree[] getNavTree(Container c)
    {
        //need to make a deep copy of the NavTree so that
        //the caller can apply per-session expand state
        //and per-request selection state
        NavTree[] toc = (NavTree[])getCached(c, TOC_NAME);
        if (null == toc)
        {
            toc = createNavTree(WikiManager.getWikisByParentId(c.getId(), -1), "Wiki-TOC-" + c.getId());
            cache(c, toc);
        }

        NavTree[] copy = new NavTree[toc.length];
        for (int idx = 0; idx < toc.length; ++idx)
        {
            copy[idx] = new NavTree(toc[idx]);
        }
        return copy;
    }

    static private NavTree[] createNavTree(List<Wiki> pageList, String rootId)
    {
        ArrayList<NavTree> elements = new ArrayList<NavTree>();
        //add all pages to the nav tree
        for (Wiki page : pageList)
        {
            NavTree node = new NavTree(page.latestVersion().getTitle().getSource(), page.getPageLink(), true);
            node.addChildren(createNavTree(page.getChildren(), rootId));
            node.setId(rootId);
            elements.add(node);
        }
        return elements.toArray(new NavTree[elements.size()]);
    }


    public static void uncache(Container c, String name)
    {
        WIKI_CACHE.remove(_cachedName(c, name));
        uncacheLists(c);
    }

    private static void uncacheLists(Container c)
    {
        WIKI_CACHE.remove(_cachedName(c, ORDERED_PAGE_LIST));
        WIKI_CACHE.remove(_cachedName(c, PAGES_NAME));
        WIKI_CACHE.remove(_cachedName(c, VERSIONS_NAME));
    }

    private static void uncacheUsingPrefix(Container c, String prefix)
    {
        WIKI_CACHE.removeUsingPrefix(_cachedName(c, prefix));
        uncacheLists(c);
    }

    static Object getCached(Container c, String name)
    {
        if (c == null || name == null)
            return null;

        Object cached = null;
        if (useCache)
            cached = WIKI_CACHE.get(_cachedName(c, name));
        return cached;
    }
}
