/*
 * Copyright (c) 2003-2007 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.labkey.common.util.Pair;

import java.io.Serializable;
import java.util.*;


public class ArrayListMap<K, V> extends AbstractMap<K, V> implements Serializable
{
    protected Map<K, Integer> _findMap;
    protected ArrayList<V> _row;


    public ArrayListMap()
    {
        this(true);
    }


    protected ArrayListMap(boolean init)
    {
        if (init)
        {
            _findMap = new HashMap<K, Integer>();
            _row = new ArrayList<V>();
        }
    }


    public ArrayListMap(int size)
    {
        _row = new ArrayList<V>(size);
        _findMap = new HashMap<K, Integer>(size * 2);
    }


    public V get(Object key)
    {
        Integer i = _findMap.get(key);
        return null == i ? null : _row.get(i.intValue());
    }


    public V put(K key, V value)
    {
        Integer i = _findMap.get(key);
        if (null != i)
            return _row.set(i, value);
        _row.add(value);
        i = _row.size() - 1;
        _findMap.put(key, i);
        return null;
    }


    public void clear()
    {
        _findMap.clear();
        _row.clear();
    }


    public boolean containsKey(Object key)
    {
        return _findMap.containsKey(key);
    }


    public boolean containsValue(Object value)
    {
        return _row.contains(value);
    }


    /**
     * this would be slow, so don't implement unless we need to
     */
    public Set<Entry<K, V>> entrySet()
    {
        Set<Entry<K, V>> r = new HashSet<Entry<K, V>>(_row.size() * 2);
        for (Entry<K, Integer> e : _findMap.entrySet())
        {
            r.add(new Pair(e.getKey(), _row.get(e.getValue())));
        }
        return r;
    }


    public Set<K> keySet()
    {
        return _findMap.keySet();
    }


    public V remove(Object key)
    {
        Integer i = _findMap.remove(key);
        if (null == i)
            return null;
        V o = _row.get(i.intValue());
        _row.set(i.intValue(), null);
        return o;
    }


    public int size()
    {
        return _findMap.size();
    }


    public Collection<V> values()
    {
        return Collections.unmodifiableCollection(_row);
    }

    /* ArrayListMap extensions (not part of Map) */

    public V get(int i)
    {
        return _row.get(i);
    }

    /**
     * NOTE: we're not validating that you haven't removed the key!
     */
    public V set(int i, V value)
    {
        return _row.set(i, value);
    }


    // need to override toString() since we don't implement entrySet()
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append("{");

        Iterator i = _findMap.entrySet().iterator();
        boolean hasNext = i.hasNext();
        while (hasNext)
        {
            Map.Entry e = (Map.Entry) i.next();
            Object key = e.getKey();
            Object value = _row.get(((Integer) e.getValue()).intValue());
            if (key == this)
                buf.append("(this Map)");
            else
                buf.append(key);
            buf.append("=");
            if (value == this)
                buf.append("(this Map)");
            else
                buf.append(value);
            hasNext = i.hasNext();
            if (hasNext)
                buf.append(", ");
        }

        buf.append("}");
        return buf.toString();
    }


    public static void main(String[] args)
    {
        Map m = new ArrayListMap(4);
        m.put("A", "one");
        m.put("B", "two");
        m.put("C", "three");
        m.put("D", "four");
        System.out.println(m.toString());
    }
}
