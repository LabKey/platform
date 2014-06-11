/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.util.HString;
import org.labkey.api.view.NavTree;
import org.labkey.wiki.model.WikiTree;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final Map<Integer, WikiTree> _treesByRowId = new LinkedHashMap<>();
    private final Map<HString, WikiTree> _treesByName = new LinkedHashMap<>();
    private final List<HString> _names = new ArrayList<>();
    private final int _pageCount;
    private final Map<HString, HString> _nameTitleMap = new LinkedHashMap<>();
    private final List<NavTree> _navTree;


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
        _treesByRowId.put(_root.getRowId(), _root);

        final MultiMap<Integer, Integer> childMap = new MultiHashMap<>();

        new SqlSelector(CommSchema.getInstance().getSchema(), new SQLFragment(SQL, c)).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                int rowId = rs.getInt(1);
                HString name = new HString(rs.getString(2));
                int parentId = rs.getInt(3);
                HString title = new HString(rs.getString(4));

                assert !name.isEmpty();
                assert !title.isEmpty();

                WikiTree tree = new WikiTree(rowId, name, title);
                _treesByRowId.put(rowId, tree);
                _treesByName.put(name, tree);
                childMap.put(parentId, rowId);
            }
        });

        // Now that we have all the children, populate them into the WikiTree
        populateWikiTree(_root, childMap);

        // List of names in depth-first order
        populateNames(_root);
        _pageCount = _names.size();

        // Now create the name->title map
        for (HString name : _names)
            _nameTitleMap.put(name, _treesByName.get(name).getTitle());

        _navTree = createNavTree(c, true);
    }


    private void populateWikiTree(WikiTree parent, MultiMap<Integer, Integer> childMap)
    {
        Collection<WikiTree> children = parent.getChildren();
        Collection<Integer> childrenIds = childMap.get(parent.getRowId());

        if (null != childrenIds)
        {
            for (Integer childId : childrenIds)
            {
                WikiTree child = _treesByRowId.get(childId);
                child.setParent(parent);
                children.add(child);
                populateWikiTree(child, childMap);
            }
        }
    }


    // Create name list in depth-first order
    private void populateNames(WikiTree root)
    {
        Collection<WikiTree> children = root.getChildren();

        for (WikiTree tree : children)
        {
            _names.add(tree.getName());
            populateNames(tree);
        }
    }

    protected List<NavTree> createNavTree(Container c, boolean showHidden)
    {
        String rootId = createRootId(c);
        return createNavTree(c, rootId, _root, showHidden);
    }

    private String createRootId(Container c)
    {
        return "Wiki-TOC-" + c.getId();
    }

    private List<NavTree> createNavTree(Container c, String rootId, WikiTree tree, boolean showHidden)
    {
        ArrayList<NavTree> elements = new ArrayList<>();

        //add all pages to the nav tree
        for (WikiTree child : tree.getChildren())
        {
            if (!child.getName().startsWith("_") || showHidden)
            {
                NavTree node = new NavTree(child.getTitle().getSource(), WikiController.getPageURL(c, child.getName()), true);
                node.addChildren(createNavTree(c, rootId, child, showHidden));
                node.setId(rootId);
                elements.add(node);
            }
        }

        return Collections.unmodifiableList(elements);
    }


    int getPageCount()
    {
        return _pageCount;
    }


    WikiTree getWikiTree()
    {
        return _root;
    }


    // TODO: Return unmodifiable collections

    @NotNull List<HString> getNames()
    {
        return _names;
    }

    /** this gets the unfiltered version of the tree. */
    List<NavTree>  getNavTree()
    {
        return _navTree;
    }

    // Returns null for non-existent wiki
    @Nullable WikiTree getWikiTree(@Nullable HString name)
    {
        return _treesByName.get(name);
    }

    // Returns null for non-existent wiki
    @Nullable WikiTree getWikiTree(int rowId)
    {
        return _treesByRowId.get(rowId);
    }

    // Returns null for non-existent wiki, empty collection for existing but no children
    @Nullable Collection<WikiTree> getChildren(@Nullable HString parentName)
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
    Map<HString, HString> getNameTitleMap()
    {
        return _nameTitleMap;
    }

    HString getName(int rowId)
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
        return populateWikiTrees(root, new LinkedHashSet<WikiTree>());
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
