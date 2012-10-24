/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.collections;

import java.util.List;
import java.util.ListIterator;

/**
 * User: adam
 * Date: 10/21/12
 * Time: 6:06 AM
 */

// A list that exposes an indexing system other than the standard 0 to (n - 1). Implementations can, for example,
// create a one-based list, use only even indexes, or use a specific range of indexes [-24, 24]. For efficiency and
// convenience, the alternate indexes are mapped and values stored in a standard zero-based list; that list can be
// retrieved, making this class especially convenient when the same array is used with both alternate and standard
// indexing.
public abstract class IndexMappingList<L extends IndexMappingList<L, E>, E> extends ListWrapper<E>
{
    public IndexMappingList(List<E> list)
    {
        super(list);
    }

    abstract protected int mapIndexIn(int index);
    abstract protected int mapIndexOut(int index);
    abstract protected L wrapList(List<E> list);

    @Override
    public E get(int index)
    {
        return super.get(mapIndexIn(index));
    }

    @Override
    public E set(int index, E element)
    {
        return super.set(mapIndexIn(index), element);
    }

    @Override
    public void add(int index, E element)
    {
        super.add(mapIndexIn(index), element);
    }

    @Override
    public E remove(int index)
    {
        return super.remove(mapIndexIn(index));
    }

    @Override
    public int indexOf(Object o)
    {
        return mapIndexOut(super.indexOf(o));
    }

    @Override
    public int lastIndexOf(Object o)
    {
        return mapIndexOut(super.lastIndexOf(o));
    }

    @Override
    public L subList(int fromIndex, int toIndex)
    {
        return wrapList(super.subList(mapIndexIn(fromIndex), mapIndexIn(toIndex)));
    }

    // TODO: Implement a ListIteratorWrapper and an IndexMappingListIterator that applies the index transformation
    // to nextIndex() and previousIndex()

    @Override
    public ListIterator<E> listIterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<E> listIterator(int index)
    {
        throw new UnsupportedOperationException();
    }
}
