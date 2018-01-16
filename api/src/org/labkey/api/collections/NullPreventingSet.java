/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.Iterator;
import java.util.Collection;

/**
 * Wrapper around another Set instance that disallows adding nulls by throwing an {@link IllegalArgumentException}
 * if a caller tries to add one. Useful for preventing a {@link NullPointerException} from blowing up with a less
 * useful, later stack trace.
 * User: jeckels
 * Date: Feb 16, 2009
 */
public class NullPreventingSet<T> implements Set<T>
{
    private Set<T> _set;

    public NullPreventingSet(Set<T> set)
    {
        _set = set;
    }

    public int size()
    {
        return _set.size();
    }

    public boolean isEmpty()
    {
        return _set.isEmpty();
    }

    public boolean contains(Object o)
    {
        return _set.contains(o);
    }

    @NotNull
    public Iterator<T> iterator()
    {
        return _set.iterator();
    }

    @NotNull
    public Object[] toArray()
    {
        return _set.toArray();
    }

    @NotNull
    public <T> T[] toArray(T[] a)
    {
        return _set.toArray(a);
    }

    public boolean add(T t)
    {
        if (t == null)
        {
            throw new IllegalArgumentException("Cannot add null to this set");
        }
        return _set.add(t);
    }

    public boolean remove(Object o)
    {
        return _set.remove(o);
    }

    public boolean containsAll(Collection<?> c)
    {
        return _set.containsAll(c);
    }

    public boolean addAll(Collection<? extends T> c)
    {
        return _set.addAll(c);
    }

    public boolean retainAll(Collection<?> c)
    {
        return _set.retainAll(c);
    }

    public boolean removeAll(Collection<?> c)
    {
        return _set.removeAll(c);
    }

    public void clear()
    {
        _set.clear();
    }

    public boolean equals(Object o)
    {
        return _set.equals(o);
    }

    public int hashCode()
    {
        return _set.hashCode();
    }
}
