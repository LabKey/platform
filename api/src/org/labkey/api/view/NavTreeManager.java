package org.labkey.api.view;

import org.apache.log4j.Logger;
import org.labkey.api.security.User;
import org.labkey.api.util.Cache;

import javax.servlet.http.HttpSession;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jun 16, 2006
 * Time: 9:22:37 AM
 */
public class NavTreeManager
{
    private static final String EXPAND_CONTAINERS_KEY = NavTreeManager.class.getName() + "/expandedContainers";
    private static final String CACHE_PREFIX = NavTreeManager.class.getName() + "/";
    private static final Logger _log = Logger.getLogger(NavTreeManager.class);
    
    public static void expandCollapsePath(ViewContext viewContext, String navTreeId, String path, boolean collapse)
    {
        if (null == navTreeId)
            return;
        
        _log.debug("Expand/Collapse path navTreeId = " + navTreeId + " path=" +  path + " collapse= " + collapse);
        saveExpandState(viewContext, navTreeId, path, collapse);
    }

    private static void saveExpandState(ViewContext viewContext, String navTreeId, String path, boolean collapse)
    {
        Set<String> expandedPaths = getExpandedPaths(viewContext, navTreeId);
        if (collapse)
            expandedPaths.remove(path);
        else
            expandedPaths.add(path);
    }

    private static void collapseAll(Collapsible tree)
    {
        tree.setCollapsed(true);
        if (tree.getChildren() != null)
        {
            for (Collapsible child : tree.getChildren())
                collapseAll(child);
        }
    }

    private static Set<String> getExpandedPaths(ViewContext viewContext, String navTreeId)
    {
        //Each navtreeid has a set of expanded paths...
        HttpSession session = viewContext.getRequest().getSession(true);
        Map<String, Set<String>> treeMap = (Map<String, Set<String>>) session.getAttribute(EXPAND_CONTAINERS_KEY);
        if (null == treeMap)
        {
            //FIX: 5389
            //It's possible to get two requests on two different threads under
            //the same session (e.g., nav tree embedded in a wiki web part). So these
            //collections must use the syncrhronized wrappers.
            treeMap = Collections.synchronizedMap(new HashMap<String, Set<String>>());
            // Don't track. These stick around in session, so are new?
            // assert MemTracker.put(treeMap);
            session.setAttribute(EXPAND_CONTAINERS_KEY, treeMap);
        }

        Set<String> expandedPaths = treeMap.get(navTreeId);
        if (null == expandedPaths)
        {
            expandedPaths = Collections.synchronizedSet(new HashSet<String>());
            //assert MemTracker.put(expandedPaths);
            treeMap.put(navTreeId, expandedPaths);
        }

        return expandedPaths;
    }

    private static void _expandCollapseSubtree(Collapsible tree, String path, boolean collapse)
    {
        Collapsible subtree = tree.findSubtree(path);
        if (null != subtree)
            subtree.setCollapsed(collapse);
    }

    /*
     * Grab the expanded state for this tree from the session and apply it to the tree.
     */
    public static void applyExpandState(Collapsible tree, ViewContext viewContext)
    {
        Set<String> expandedPaths = getExpandedPaths(viewContext, tree.getId());

        //FIX: 5389
        //according to the javadoc for Collections, iterating over a synchronized set still
        //requires a synchronized block. See:
        //http://java.sun.com/j2se/1.5.0/docs/api/java/util/Collections.html#synchronizedSet(java.util.Set)
        synchronized(expandedPaths)
        {
            for (String p : expandedPaths)
                _expandCollapseSubtree(tree, p, false);
        }
    }

    /*
     * Cache a tree for the current user. User may be null
     */
    public static void cacheTree(Collapsible navTree, User user)
    {
        collapseAll(navTree);
        Cache.getShared().put(getCacheKey(navTree.getId(), user), navTree, Cache.MINUTE);
    }

    /*
     * Uncache a tree with the id given. Also uncaches any per-user cached trees.
     */
    public static void uncacheTree(String treeId)
    {
        Cache.getShared().removeUsingPrefix(getCacheKey(treeId, null));
    }

    public static void uncacheTree(String treeId, User user)
    {
        Cache.getShared().remove(getCacheKey(treeId, user));
    }

    public static Collapsible getFromCache(String navTreeId, ViewContext viewContext)
    {
        Collapsible cached = (Collapsible) Cache.getShared().get(getCacheKey(navTreeId, viewContext.getUser()));
        if (cached != null)
        {
            collapseAll(cached);
            applyExpandState(cached, viewContext);
        }
        return cached;
    }

    private static String getCacheKey(String navTreeId, User user)
    {
        assert null != navTreeId;
        String key;
        if (null != user)
            key = navTreeId + "/user=" + user.getUserId();
        else
            key = navTreeId + "/";
        return CACHE_PREFIX + key;
    }

    public static void uncacheAll()
    {
        Cache.getShared().removeUsingPrefix(CACHE_PREFIX);
    }
}
