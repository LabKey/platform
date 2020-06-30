/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

public class NamedObjectList implements Collection<NamedObject>
{
    public static final NamedObjectList INCOMPLETE = new NamedObjectList(false);

    private final ArrayListMap<String, NamedObject> _map = new ArrayListMap<>();
    private final boolean _complete;

    public NamedObjectList()
    {
        this(true);
    }

    public NamedObjectList(boolean complete)
    {
        _complete = complete;
    }

    public boolean isComplete()
    {
        return _complete;
    }

    public void put(NamedObject obj)
    {
        _map.put(obj.getName(), obj);
    }

    @Override
    public int size()
    {
        return _map.size();
    }

    public Object get(int i)
    {
        NamedObject n = _map.get(i);
        return null == n ? null : n.getObject();
    }

    public Object get(String s)
    {
        NamedObject n = _map.get(s);
        return null == n ? null : n.getObject();
    }

    @Override
    public <NamedObject> NamedObject[] toArray(NamedObject[] array)
    {
        return _map.values().toArray(array);
    }

    @Override
    public NamedObject[] toArray()
    {
        return _map.values().toArray(new NamedObject[size()]);
    }

    @Override
    public boolean add(NamedObject arg0)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection arg0)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object arg0)
    {
        return _map.containsValue(arg0);
    }

    @Override
    public boolean containsAll(Collection arg0)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty()
    {
        return _map.isEmpty();
    }

    @Override
    public Iterator<NamedObject> iterator()
    {
        return _map.values().iterator();
    }

    @Override
    public boolean remove(Object arg0)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection arg0)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection arg0)
    {
        throw new UnsupportedOperationException();
    }

}
