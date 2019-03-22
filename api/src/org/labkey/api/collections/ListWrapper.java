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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * User: adam
 * Date: 10/20/12
 * Time: 9:09 PM
 */
public class ListWrapper<E> implements List<E>
{
    private final List<E> _list;

    public ListWrapper(List<E> list)
    {
        _list = list;
    }

    public List<E> getUnderlyingList()
    {
        return _list;
    }

    @Override
    public int size()
    {
        return _list.size();
    }

    @Override
    public boolean isEmpty()
    {
        return _list.isEmpty();
    }

    @Override
    public boolean contains(Object o)
    {
        return _list.contains(o);
    }

    @Override
    public Iterator<E> iterator()
    {
        return _list.iterator();
    }

    @Override
    public Object[] toArray()
    {
        return _list.toArray();
    }

    public <T> T[] toArray(T[] a)
    {
        return _list.toArray(a);
    }

    @Override
    public boolean add(E e)
    {
        return _list.add(e);
    }

    @Override
    public boolean remove(Object o)
    {
        return _list.remove(o);
    }

    public boolean containsAll(Collection<?> c)
    {
        return _list.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c)
    {
        return _list.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c)
    {
        return _list.addAll(index, c);
    }

    public boolean removeAll(Collection<?> c)
    {
        return _list.removeAll(c);
    }

    public boolean retainAll(Collection<?> c)
    {
        return _list.retainAll(c);
    }

    @Override
    public void clear()
    {
        _list.clear();
    }

    @Override
    public boolean equals(Object o)
    {
        return _list.equals(o);
    }

    @Override
    public int hashCode()
    {
        return _list.hashCode();
    }

    @Override
    public E get(int index)
    {
        return _list.get(index);
    }

    @Override
    public E set(int index, E element)
    {
        return _list.set(index, element);
    }

    @Override
    public void add(int index, E element)
    {
        _list.add(index, element);
    }

    @Override
    public E remove(int index)
    {
        return _list.remove(index);
    }

    @Override
    public int indexOf(Object o)
    {
        return _list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o)
    {
        return _list.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator()
    {
        return _list.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index)
    {
        return _list.listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex)
    {
        return _list.subList(fromIndex, toIndex);
    }
}
