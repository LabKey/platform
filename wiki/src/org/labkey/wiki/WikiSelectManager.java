/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.NavTree;
import org.labkey.wiki.WikiCache.WikiCacheLoader;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiTree;
import org.labkey.wiki.model.WikiVersion;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: adam
* Date: Dec 3, 2010
* Time: 10:54:25 PM
*/

// Handles all the select queries that are cached by WikiCache; keeps the query and data structure details out of the
// cache code while making it clear which queries are cached and which are not.
public class WikiSelectManager
{
    // List of page names in this folder, in depth-first tree order
    public static List<String> getPageNames(Container c)
    {
        return getWikiCollections(c).getNames();
    }


    // Number of wiki pages in this folder
    public static int getPageCount(Container c)
    {
        return getWikiCollections(c).getPageCount();
    }


    // Does this folder have any wiki pages?
    public static boolean hasPages(Container c)
    {
        return getPageCount(c) > 0;
    }


    // Wiki name -> current title map, in depth-first tree order
    public static Map<String, String> getNameTitleMap(Container c)
    {
        return getWikiCollections(c).getNameTitleMap();
    }


    // Get a single wiki by rowId
    public static Wiki getWiki(Container c, int rowId)
    {
        String name = getWikiCollections(c).getName(rowId);

        if (null == name)
            return null;

        return getWiki(c, name);
    }


    // Get a wiki version
    public static WikiVersion getVersion(Container c, int rowId)
    {
        return WikiVersionCache.getVersion(c, rowId);
    }


    // Get a single wiki by name
    public static Wiki getWiki(Container c, final String name)
    {
        if (name == null)
        {
            return null;
        }
        return WikiCache.getWiki(c, name, new WikiCacheLoader<Wiki>()
        {
            @Override
            public Wiki load(String key, Container c)
            {
                return getWikiFromDatabase(c, name);
            }
        });
    }


    private static WikiCollections getWikiCollections(Container c)
    {
        return WikiCache.getWikiCollections(c, new WikiCacheLoader<WikiCollections>(){
            @Override
            WikiCollections load(String key, Container c)
            {
                return new WikiCollections(c);
            }
        });
    }


    // Available outside this class only to support junit tests
    static Wiki getWikiFromDatabase(Container c, String name)
    {
        if (name == null)
            return null;

        Wiki wiki = new TableSelector(CommSchema.getInstance().getTableInfoPages(),
                SimpleFilter.createContainerFilter(c).addCondition(FieldKey.fromParts("name"), name),
                null).getObject(Wiki.class);

        if (null == wiki)
        {
            //Didn't find it with case-sensitive lookup, try case-sensitive (in case the
            //underlying database is case sensitive)
            //Bug 2225
            wiki = new TableSelector(CommSchema.getInstance().getTableInfoPages(),
                    SimpleFilter.createContainerFilter(c).addWhereClause("LOWER(name) = LOWER(?)", new Object[] { name }),
                    null).getObject(Wiki.class);
        }

        return wiki;
    }


    // TODO: Does every caller need a deep copy?
    static @NotNull List<NavTree> getNavTree(Container c, User u)
    {
        List<NavTree> toc;

        // if admin get unfiltered, else filter navtree
        if (c.hasPermission(u, AdminPermission.class))
            toc = getWikiCollections(c).getAdminNavTree();
        else
            toc = getWikiCollections(c).getNonAdminNavTree();

        //need to make a deep copy of the NavTree so that
        //the caller can apply per-session expand state
        //and per-request selection state
        List<NavTree> copy = new ArrayList<>();

        for (NavTree tree : toc)
            copy.add(new NavTree(tree));

        return copy;
    }


    // Return a collection of WikiTrees representing all wikis that are possible parents for this wiki. The set omits
    // the wiki itself plus all its descendants
    public static Set<WikiTree> getPossibleParents(Container c, @Nullable Wiki wiki)
    {
        WikiCollections wc = getWikiCollections(c);
        Set<WikiTree> possibleParents = wc.getWikiTrees();

        if (null != wiki)
        {
            WikiTree currentTree = wc.getWikiTree(wiki.getRowId());
            possibleParents.removeAll(wc.getWikiTrees(currentTree));
        }

        return possibleParents;
    }


    public static @NotNull Collection<WikiTree> getChildren(Container c, int rowId)
    {
        WikiTree tree = getWikiCollections(c).getWikiTree(rowId);

        assert null != tree;

        return tree.getChildren();
    }


    // Only use if a full wiki is needed (otherwise, use WikiTree)
    public static @NotNull List<Wiki> getChildWikis(Container c, int rowId)
    {
        List<Wiki> wikis = new LinkedList<>();

        for (WikiTree tree : getChildren(c, rowId))
            wikis.add(getWiki(c, tree.getName()));

        return wikis;
    }


    // All WikiTrees in this folder
    public static Set<WikiTree> getWikiTrees(Container c)
    {
        return getWikiCollections(c).getWikiTrees();
    }


    // WikiTrees for a subtree
    public static Set<WikiTree> getWikiTrees(Container c, Wiki wiki)
    {
        WikiCollections wc = getWikiCollections(c);
        WikiTree tree = wc.getWikiTree(wiki.getRowId());
        return wc.getWikiTrees(tree);
    }

    // Single WikiTree
    public static WikiTree getWikiTree(Container c, String name)
    {
        return getWikiCollections(c).getWikiTree(name);
    }


    public static boolean hasChildren(Container c, int rowId)
    {
        WikiTree tree = getWikiCollections(c).getWikiTree(rowId);

        // Null check wiki since it might have been deleted, #13559
        return null != tree && !tree.getChildren().isEmpty();
    }

    // ====== Everything below here is deprecated ======

    // TODO: Use VersionCache for other versions (not just latest) and for list of versions

    // TODO: Use cache!!  Switch to an array of versions, and use the cache to retrieve each one
    public static WikiVersion getVersion(Wiki wiki, int version) throws SQLException
    {
        if (null == wiki.getEntityId())
            return null;

        WikiVersion[] versions = getAllVersions(wiki);
        WikiVersion wikiversion = null;

        for (WikiVersion v : versions)
        {
            if (v.getVersion() == version)
            {
                wikiversion = v;
                break;
            }
        }

        if (null == wikiversion)
            return null;

        return wikiversion;
    }

    public static WikiVersion[] getAllVersions(Wiki wiki)
    {
        //fail if wiki has no entityid
        if (null == wiki.getEntityId())
            throw new IllegalStateException("Cannot retrieve version for non-existent wiki page.");

        return new TableSelector(CommSchema.getInstance().getTableInfoPageVersions(),
                new SimpleFilter(FieldKey.fromParts("pageentityid"), wiki.getEntityId()),
                new Sort("pageentityid,version")).getArray(WikiVersion.class);
    }

    public static int getVersionCount(Wiki wiki)
    {
        return getAllVersions(wiki).length;
    }

    public static int getNextVersionNumber(Wiki wiki)
    {
        WikiVersion[] versions = getAllVersions(wiki);
        //get last wiki version inserted
        //note: this will break if an existing version between 0 and n is deleted
        WikiVersion wikiversion = versions[versions.length - 1];
        return wikiversion.getVersion() + 1;
    }
}
