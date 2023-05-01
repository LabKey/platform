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

package org.labkey.wiki.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

/*
* User: adam
* Date: Dec 15, 2010
* Time: 10:37:30 AM
*/
// Represents a tree or sub-tree of wiki objects.  Used for caching TOC, children, siblings, etc.
public class WikiTree
{
    private final Integer _rowId;
    private final String _name;
    private final String _title;
    private final Collection<WikiTree> _children;

    private int _depth = -1;
    private WikiTree _parent = null;

    public static WikiTree createRootWikiTree()
    {
        return new WikiTree(null, null, null);
    }

    public WikiTree(Integer rowId, String name, String title)
    {
        _rowId = rowId;
        _name = name;
        _title = title;
        _children = new LinkedHashSet<>();
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public String getName()
    {
        return _name;
    }

    public String getTitle()
    {
        return _title;
    }

    public int getDepth()
    {
        return _depth;
    }

    public void setParent(@NotNull WikiTree tree)
    {
        _parent = tree;
        _depth = tree.getDepth() + 1;
    }

    public @Nullable WikiTree getParent()
    {
        return _parent;
    }

    public @NotNull Collection<WikiTree> getChildren()
    {
        return _children;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WikiTree wikiTree = (WikiTree) o;

        if (Objects.equals(_rowId, wikiTree._rowId)) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_rowId);
    }
}
