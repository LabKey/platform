/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.view.NavTree;
import org.labkey.wiki.model.WikiTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: adam
* Date: Dec 9, 2010
* Time: 9:32:04 PM
*/

// Generates and holds various collections representing all the wikis in a container.
public class WikiCollections
{
    private final WikiTree _root = WikiTree.createRootWikiTree();
    private final Map<Integer, WikiTree> _treesByRowId;
    private final Map<String, WikiTree> _treesByName;
    private final Map<String, String> _nameTitleMap;
    private final List<String> _names;

    private final List<NavTree> _adminNavTree;
    private final List<NavTree> _nonAdminNavTree;

    private static final StringBuilder SQL = new StringBuilder();

    static
    {
        SQL.append("SELECT pages.RowId, Name, Parent, Title FROM ");
        SQL.append(CommSchema.getInstance().getTableInfoPages());
        SQL.append(" pages LEFT OUTER JOIN ");
        SQL.append(CommSchema.getInstance().getTableInfoPageVersions());
        SQL.append(" versions ON pages.PageVersionId = versions.RowId WHERE Container = ? ORDER BY pages.DisplayOrder, pages.Rowid");
    }

    // For each wiki:
    // Name
    // Title
    // RowId?
    // DisplayOrder (implied)
    //
    // Maintains parent->children tree
    public WikiCollections(Container c)
    {
        final Map<Integer, WikiTree> treesByRowId = new LinkedHashMap<>();
        final Map<String, WikiTree> treesByName = new LinkedHashMap<>();
        final Map<String, String> nameTitleMap = new LinkedHashMap<>();
        final List<String> names = new LinkedList<>();
        final MultiValuedMap<Integer, Integer> childMap = new ArrayListValuedHashMap<>();

        treesByRowId.put(_root.getRowId(), _root);

        new SqlSelector(CommSchema.getInstance().getSchema(), new SQLFragment(SQL, c)).forEach(rs -> {
            int rowId = rs.getInt(1);
            String name = rs.getString(2);
            int parentId = rs.getInt(3);
            String title = rs.getString(4);

            assert !name.isEmpty();
            assert !title.isEmpty();

            WikiTree tree = new WikiTree(rowId, name, title);
            treesByRowId.put(rowId, tree);
            treesByName.put(name, tree);
            childMap.put(parentId, rowId);
        });

        // Now that we have all the children, populate them into the WikiTree
        populateWikiTree(_root, childMap, treesByRowId);

        // List of names in depth-first order
        populateNames(_root, names);

        // Now create the name->title map
        for (String name : names)
            nameTitleMap.put(name, treesByName.get(name).getTitle());

        _treesByRowId = Collections.unmodifiableMap(treesByRowId);
        _treesByName = Collections.unmodifiableMap(treesByName);
        _nameTitleMap = Collections.unmodifiableMap(nameTitleMap);
        _names = Collections.unmodifiableList(names);

        _adminNavTree = createNavTree(c, true);
        _nonAdminNavTree = createNavTree(c, false);
    }


    private void populateWikiTree(WikiTree parent, MultiValuedMap<Integer, Integer> childMap, Map<Integer, WikiTree> treesByRowId)
    {
        Collection<WikiTree> children = parent.getChildren();
        Collection<Integer> childrenIds = childMap.get(parent.getRowId());

        if (null != childrenIds)
        {
            for (Integer childId : childrenIds)
            {
                WikiTree child = treesByRowId.get(childId);
                child.setParent(parent);
                children.add(child);
                populateWikiTree(child, childMap, treesByRowId);
            }
        }
    }


    // Create name list in depth-first order
    private void populateNames(WikiTree root, List<String> names)
    {
        Collection<WikiTree> children = root.getChildren();

        for (WikiTree tree : children)
        {
            names.add(tree.getName());
            populateNames(tree, names);
        }
    }

    private List<NavTree> createNavTree(Container c, boolean showHidden)
    {
        String rootId = createRootId(c);
        return Collections.unmodifiableList(createNavTree(c, rootId, _root, showHidden));
    }

    private String createRootId(Container c)
    {
        return "Wiki-TOC-" + c.getId();
    }

    private List<NavTree> createNavTree(Container c, String rootId, WikiTree tree, boolean showHidden)
    {
        ArrayList<NavTree> elements = new ArrayList<>();

        //add all pages to the nav tree
        tree.getChildren()
            .stream()
            .filter(child -> showHidden || !child.getName().startsWith("_"))
            .forEach(child -> {
                NavTree node = new NavTree(child.getTitle(), WikiController.getPageURL(c, child.getName()), true);
                node.addChildren(createNavTree(c, rootId, child, showHidden));
                node.setId(rootId);
                elements.add(node);
            });

        return elements;
    }


    int getPageCount()
    {
        return getNames().size();
    }


    WikiTree getWikiTree()
    {
        return _root;
    }


    @NotNull List<String> getNames()
    {
        return _names;
    }

    /** this gets the unfiltered version of the tree. */
    List<NavTree> getAdminNavTree()
    {
        return _adminNavTree;
    }

    List<NavTree> getNonAdminNavTree()
    {
        return _nonAdminNavTree;
    }

    // Returns null for non-existent wiki
    @Nullable WikiTree getWikiTree(@Nullable String name)
    {
        return _treesByName.get(name);
    }

    // Returns null for non-existent wiki
    @Nullable WikiTree getWikiTree(int rowId)
    {
        return _treesByRowId.get(rowId);
    }

    // Returns null for non-existent wiki, empty collection for existing but no children
    @Nullable Collection<WikiTree> getChildren(@Nullable String parentName)
    {
        WikiTree parent = getWikiTree(parentName);

        if (null == parent)
            return null;

        return parent.getChildren();
    }

    // Returns null for non-existent wiki, empty collection for existing but no children
    @Nullable Collection<WikiTree> getChildren(int rowId)
    {
        return _treesByRowId.get(rowId).getChildren();
    }

    // TODO: Change to return the root WikiTree?
    Map<String, String> getNameTitleMap()
    {
        return _nameTitleMap;
    }

    String getName(int rowId)
    {
        WikiTree tree = getWikiTree(rowId);

        return null == tree ? null : tree.getName();
    }

    // Return a new, modifiable collection of WikiTrees representing all wikis in this container
    Set<WikiTree> getWikiTrees()
    {
        Set<WikiTree> set = getWikiTrees(_root);
        set.remove(_root);
        return set;
    }

    // Return new, modifiable collection of WikiTrees representing all wikis in this subtree, including the root
    Set<WikiTree> getWikiTrees(WikiTree root)
    {
        return populateWikiTrees(root, new LinkedHashSet<>());
    }

    // Recursively traverse this tree, adding all nodes to the collection.  Return collection as a convenience.
    private Set<WikiTree> populateWikiTrees(WikiTree root, Set<WikiTree> trees)
    {
        trees.add(root);

        if (root != null)
        {
            for (WikiTree child : root.getChildren())
                populateWikiTrees(child, trees);
        }

        return trees;
    }
}
