/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.util.HString;
import org.labkey.api.view.NavTree;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;
import org.labkey.api.announcements.CommSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/*
* User: adam
* Date: Dec 3, 2010
* Time: 10:54:25 PM
*/

// Handles all the select queries that are cached by WikiCache; keeps the query and data structure details out of the
// cache code while making it clear which queries are cached and which are not.
public class WikiSelectManager
{
    // Page map -- map of name -> Wiki

    static Map<HString, Wiki> getPageMap(Container c) throws SQLException
    {
        return WikiCache.getPageMap(c, PAGE_MAP_CACHE_LOADER);
    }

    private static final WikiCache.WikiCacheLoader<Map<HString, Wiki>> PAGE_MAP_CACHE_LOADER = new WikiCache.WikiCacheLoader<Map<HString, Wiki>>() {
            @Override
            Map<HString, Wiki> load(String key, Container c)
            {
                try
                {
                    return generatePageMap(c);
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
        };

    private static Map<HString, Wiki> generatePageMap(Container c) throws SQLException
    {
        Map<HString, Wiki> tree = new TreeMap<HString, Wiki>();
        List<Wiki> l = getPageList(c);
        for (Wiki wiki : l)
        {
            tree.put(wiki.getName(), wiki);
        }
        return tree;
    }


    // Version map -- map of name -> WikiLinkable

    static Map<HString, WikiRenderer.WikiLinkable> getVersionMap(Container c) throws SQLException
    {
        return WikiCache.getVersionMap(c, VERSIONS_MAP_CACHE_LOADER);
    }

    private static final WikiCache.WikiCacheLoader<Map<HString, WikiRenderer.WikiLinkable>> VERSIONS_MAP_CACHE_LOADER = new WikiCache.WikiCacheLoader<Map<HString, WikiRenderer.WikiLinkable>>() {
            @Override
            Map<HString, WikiRenderer.WikiLinkable> load(String key, Container c)
            {
                Map<HString, WikiRenderer.WikiLinkable> tree = new TreeMap<HString, WikiRenderer.WikiLinkable>();
                List<Wiki> list = getPageList(c);

                for (Wiki wiki : list)
                    tree.put(wiki.getName(), WikiManager.getLatestVersion(wiki, false));

                return tree;
            }
        };


    // Page list -- list of all wiki objects in this folder in depth-first order

    public static List<Wiki> getPageList(Container c)
    {
        return WikiCache.getOrderedPageList(c, PAGE_LIST_CACHE_LOADER);
    }

    private static final WikiCache.WikiCacheLoader<List<Wiki>> PAGE_LIST_CACHE_LOADER = new WikiCache.WikiCacheLoader<List<Wiki>>() {
                @Override
                public List<Wiki> load(String key, Container c)
                {
                    List<Wiki> pageList = new ArrayList<Wiki>();
                    List<Wiki> rootTopics = WikiManager.getWikisByParentId(c.getId(), -1);

                    for (Wiki rootTopic : rootTopics)
                        WikiManager.addAllChildren(pageList, rootTopic);

                    return pageList;
                }
            };


    // NavTree -- array of NavTrees

    static NavTree[] getNavTree(Container c)
    {
        NavTree[] toc = WikiCache.getNavTree(c, NAV_TREE_CACHE_LOADER);

        //need to make a deep copy of the NavTree so that
        //the caller can apply per-session expand state
        //and per-request selection state
        NavTree[] copy = new NavTree[toc.length];

        for (int idx = 0; idx < toc.length; ++idx)
        {
            copy[idx] = new NavTree(toc[idx]);
        }

        return copy;
    }

    private static final WikiCache.WikiCacheLoader<NavTree[]> NAV_TREE_CACHE_LOADER = new WikiCache.WikiCacheLoader<NavTree[]>() {
        @Override
        NavTree[] load(String key, Container c)
        {
            return createNavTree(WikiManager.getWikisByParentId(c.getId(), -1), "Wiki-TOC-" + c.getId());
        }
    };

    private static NavTree[] createNavTree(List<Wiki> pageList, String rootId)
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

    // UNDONE: consider exposing this method, or exposing wiki.getLatestVersion() (??)
    static WikiManager.WikiAndVersion getLatestWikiAndVersion(Container c, final HString name, boolean forceRefresh)
    {
        // TODO: This ignores forceRefresh entirely... remove forceRefresh?

        return WikiCache.getWikiAndVersion(c, name.getSource(), new WikiCache.WikiCacheLoader<WikiManager.WikiAndVersion>()
        {
            @Override
            public WikiManager.WikiAndVersion load(String key, Container c)
            {
                Wiki wiki = getWikiByName(c, name);

                if (wiki == null)
                {
                    return null;
                }

                WikiManager.WikiAndVersion wikipair;

                try
                {
                    WikiVersion wikiversion = Table.selectObject(CommSchema.getInstance().getTableInfoPageVersions(),
                                Table.ALL_COLUMNS,
                                new SimpleFilter("RowId", wiki.getPageVersionId()),
                                null,
                                WikiVersion.class);

                    if (wikiversion == null)
                        throw new IllegalStateException("Cannot retrieve a valid version for page " + wiki.getName());

                    // always cache wiki and version -- we defer formatting until WikiVersion.getHtml() is called
                    wikipair = new WikiManager.WikiAndVersion(wiki, wikiversion);
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }

                return wikipair;
            }
        });
    }


    // Available to WikiManager only to support tests
    static Wiki getWikiByName(Container c, HString name)
    {
        try
        {
            if (name == null)
                return null;

            Wiki[] wikis = Table.select(CommSchema.getInstance().getTableInfoPages(),
                    Table.ALL_COLUMNS,
                    new SimpleFilter("container", c.getId()).addCondition("name", name),
                    null, Wiki.class);

            if (0 == wikis.length)
            {
                //Didn't find it with case-sensitive lookup, try case-sensitive (in case the
                //underlying database is case sensitive)
                //Bug 2225
                wikis = Table.select(CommSchema.getInstance().getTableInfoPages(),
                        Table.ALL_COLUMNS,
                        new SimpleFilter("container", c.getId()).addWhereClause("LOWER(name) = LOWER(?)", new Object[] { name }),
                        null, Wiki.class);

                if (0 == wikis.length)
                    return null;
            }

            return wikis[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
}
