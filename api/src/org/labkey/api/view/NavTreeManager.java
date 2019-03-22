/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.security.User;
import org.labkey.api.util.SessionHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Remembers expand/collapse state for {@link NavTree}s by sticking them into the HTTP session.
 * User: Mark Igra
 * Date: Jun 16, 2006
 */
public class NavTreeManager
{
    private static final Logger _log = Logger.getLogger(NavTreeManager.class);
    private static final String EXPAND_CONTAINERS_KEY = NavTreeManager.class.getName() + "/expandedContainers";
    private static final String CACHE_PREFIX = NavTreeManager.class.getName() + "/";
    private static final String NULL_MARKER = "__null marker representing the root__";   // ConcurrentHashMap does not support null keys

    private static final StringKeyCache<Collapsible> NAV_TREE_CACHE = CacheManager.getSharedCache();
    
    public static void expandCollapsePath(ViewContext viewContext, String navTreeId, @Nullable String path, boolean collapse)
    {
        if (null == navTreeId)
            return;
        
        _log.debug("Expand/Collapse path navTreeId = " + navTreeId + " path=" +  path + " collapse= " + collapse);
        saveExpandState(viewContext, navTreeId, path, collapse);
    }

    private static void saveExpandState(ViewContext viewContext, String navTreeId, @Nullable String path, boolean collapse)
    {
        path = null == path ? NULL_MARKER : path;
        Set<String> expandedPaths = getExpandedPaths(viewContext, navTreeId);

        if (collapse)
            expandedPaths.remove(path);
        else
            expandedPaths.add(path);
    }

    private static void collapseAll(Collapsible tree)
    {
        tree.setCollapsed(true);
        for (Collapsible child : tree.getChildren())
            collapseAll(child);
    }


    static final Callable allocTreeMap = () -> Collections.synchronizedMap(new HashMap<String,Set<String>>());


    // Returned set is synchronized, but all iteration requires manual synchronization on the set, see crashweb #13038
    public static Set<String> getExpandedPaths(ViewContext viewContext, String navTreeId)
    {
        //Each navtreeid has a set of expanded paths...
        Map<String, Set<String>> treeMap = (Map<String, Set<String>>)
                SessionHelper.getAttribute(viewContext.getRequest(), EXPAND_CONTAINERS_KEY, allocTreeMap);
        // treeMap should never be null, unless we can't create a session
        if (null == treeMap)
            return Collections.emptySet();

        return treeMap.computeIfAbsent(navTreeId, k -> Collections.synchronizedSet(new HashSet<>()));
    }


    // Give external callers a copy of the synchronzied set... I don't trust them to synchronize properly
    public static Set<String> getExpandedPathsCopy(ViewContext viewContext, String navTreeId)
    {
        return new HashSet<>(getExpandedPaths(viewContext, navTreeId));
    }


    private static void _expandCollapseSubtree(Collapsible tree, @NotNull String path, boolean collapse)
    {
        @SuppressWarnings({"StringEquality"})
        Collapsible subtree = tree.findSubtree(NULL_MARKER == path ? null : path);

        if (null != subtree)
            subtree.setCollapsed(collapse);
    }

    /*
     * Grab the expanded state for this tree from the session and apply it to the tree.
     */
    public static void applyExpandState(Collapsible tree, ViewContext viewContext)
    {
        Set<String> expandedPaths = getExpandedPaths(viewContext, tree.getId());

        // Synchronized set iteration must be manually synchronized
        synchronized (expandedPaths)
        {
            for (String p : expandedPaths)
                _expandCollapseSubtree(tree, p, false);
        }
    }

    /*
     * Cache a tree for the current user. User may be null
     */
    public static void cacheTree(Collapsible navTree, @Nullable User user)
    {
        collapseAll(navTree);
        NAV_TREE_CACHE.put(getCacheKey(navTree.getId(), user), navTree, CacheManager.MINUTE);
    }

    /*
     * Uncache a tree with the id given. Also uncaches any per-user cached trees.
     */
    public static void uncacheTree(String treeId)
    {
        NAV_TREE_CACHE.removeUsingPrefix(getCacheKey(treeId, null));
    }

    public static void uncacheTree(String treeId, User user)
    {
        NAV_TREE_CACHE.remove(getCacheKey(treeId, user));
    }

    public static Collapsible getFromCache(String navTreeId, ViewContext viewContext)
    {
        Collapsible cached = NAV_TREE_CACHE.get(getCacheKey(navTreeId, viewContext.getUser()));
        if (cached != null)
        {
            collapseAll(cached);
            applyExpandState(cached, viewContext);
        }
        return cached;
    }

    private static String getCacheKey(String navTreeId, @Nullable User user)
    {
        assert null != navTreeId;
        String key;
        if (null != user)
        {
            // Caching permission-related state is tricky with impersonation, so involve the impersonation context
            key = navTreeId + "/user=" + user.getUserId() + user.getImpersonationContext().getCacheKey();
        }
        else
        {
            key = navTreeId + "/";
        }
        return CACHE_PREFIX + key;
    }

    public static void uncacheAll()
    {
        NAV_TREE_CACHE.removeUsingPrefix(CACHE_PREFIX);
    }
}
