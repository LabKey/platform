/*
 * Copyright (c) 2003-2009 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.Pair;

import java.io.Serializable;
import java.util.*;


public class ArrayListMap<K, V> extends AbstractMap<K, V> implements Serializable
{
    private final Map<K, Integer> _findMap;
    private final List<V> _row;

    public ArrayListMap()
    {
        this(new HashMap<K, Integer>(), new ArrayList<V>());
    }


    public ArrayListMap(int columnCount)
    {
        this(new HashMap<K, Integer>(columnCount * 2), new ArrayList<V>(columnCount));
    }


    public ArrayListMap(ArrayListMap<K, V> m, List<V> row)
    {
        this(m.getFindMap(), row);
    }


    protected ArrayListMap(Map<K, Integer> findMap)
    {
        this(findMap, new ArrayList<V>(findMap.size()));
    }


    protected ArrayListMap(Map<K, Integer> findMap, int columnCount)
    {
        this(findMap, new ArrayList<V>(columnCount));
    }


    protected ArrayListMap(Map<K, Integer> findMap, List<V> row)
    {
        _findMap = findMap;
        _row = row;
    }


    public V get(Object key)
    {
        Integer I = _findMap.get(key);
        if (I == null)
            return null;
        int i = I.intValue();
        return i>=_row.size() ? null : _row.get(i);
    }


    public V put(K key, V value)
    {
        Integer I = _findMap.get(key);
        int i;

        if (null == I)
        {
            i = _findMap.size();
            _findMap.put(key, i);
        }
        else
        {
            i = I.intValue();
        }
        
        while (i >= _row.size())
            _row.add(null);
        return _row.set(i, value);
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


    // CONSIDER: throw UnsupportedOperation()
    public V remove(Object key)
    {
        return put((K)key, null);
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


    public Map<K, Integer> getFindMap()
    {
        return _findMap;
    }

    public List<V> getRow()
    {
        return _row;
    }


    public static void main(String[] args)
    {
        Map<String,String> m = new ArrayListMap<String,String>(4);
        m.put("A", "one");
        m.put("B", "two");
        m.put("C", "three");
        m.put("D", "four");
        System.out.println(m.toString());
    }
}
