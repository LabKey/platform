/*
 * Copyright (c) 2003-2016 Fred Hutchinson Cancer Research Center
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

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.Pair;

import java.io.Serializable;
import java.util.*;


public class ArrayListMap<K, V> extends AbstractMap<K, V> implements Serializable
{
    boolean _readonly = false;

    private static final Object DOES_NOT_CONTAINKEY = new Object()
    {
        @Override
        public String toString()
        {
            return "ArrayListMap.DOES_NOT_CONTAINKEY";
        }
    };

    private V convertToV(Object o)
    {
        return o == DOES_NOT_CONTAINKEY ? null : (V)o;
    }

    public static class FindMap<K> implements Map<K,Integer>
    {
        final Map<K,Integer> _map;
        int _max = -1;

        public FindMap(Map<K,Integer> wrap)
        {
            _map = wrap;
        }

        @Override
        public int size()
        {
            return _map.size();
        }

        @Override
        public boolean isEmpty()
        {
            return _map.isEmpty();
        }

        @Override
        public boolean containsKey(Object o)
        {
            return _map.containsKey(o);
        }

        @Override
        public boolean containsValue(Object o)
        {
            return _map.containsValue(o);
        }

        @Override
        public Integer get(Object o)
        {
            return _map.get(o);
        }

        @Override
        public Integer put(K k, Integer integer)
        {
            assert null != integer;
            assert !containsValue(integer);
            if (integer > _max)
                _max = integer;
            return _map.put(k,integer);
        }

        @Override
        public Integer remove(Object o)
        {
            return _map.remove(o);
        }

        @Override
        public void putAll(Map<? extends K, ? extends Integer> map)
        {
            _map.putAll(map);
        }

        @Override
        public void clear()
        {
            _map.clear();
        }

        @Override
        public Set<K> keySet()
        {
            return _map.keySet();
        }

        @Override
        public Collection<Integer> values()
        {
            return _map.values();
        }

        @Override
        public Set<Entry<K, Integer>> entrySet()
        {
            return _map.entrySet();
        }
    }


    private final FindMap<K> _findMap;
    private final List<Object> _row;

    public ArrayListMap()
    {
        this(new FindMap<>(new HashMap<K, Integer>()), new ArrayList<V>());
    }


    public ArrayListMap(int columnCount)
    {
        this(new FindMap<>(new HashMap<K, Integer>(columnCount * 2)), new ArrayList<V>(columnCount));
    }


    public ArrayListMap(ArrayListMap<K, V> m, List<V> row)
    {
        this(m.getFindMap(), row);
    }


    public ArrayListMap(FindMap<K> findMap)
    {
        this(findMap, new ArrayList<V>(findMap.size()));
    }


    public ArrayListMap(FindMap<K> findMap, List<V> row)
    {
        _findMap = findMap;
        _row = (List<Object>)row;
    }


    public V get(Object key)
    {
        Integer I = _findMap.get(key);
        if (I == null)
            return null;
        int i = I.intValue();
        Object v = i>=_row.size() ? null : _row.get(i);
        return convertToV(v);
    }


    public V put(K key, V value)
    {
        if (_readonly)
            throw new IllegalStateException();
        Integer I = _findMap.get(key);
        int i;

        if (null == I)
        {
            i = _findMap._max+1;
            _findMap.put(key, i);
        }
        else
        {
            i = I.intValue();
        }
        
        while (i >= _row.size())
            _row.add(null);
        return convertToV(_row.set(i,value));
    }


    public void clear()
    {
        if (_readonly)
            throw new IllegalStateException();
        for (int i=0 ; i<_row.size() ; i++)
            _row.set(i, DOES_NOT_CONTAINKEY);
    }


    public boolean containsKey(Object key)
    {
        Integer I = _findMap.get(key);
        if (I == null)
            return false;
        int i = I.intValue();
        Object v = i>=_row.size() ? null : _row.get(i);
    return v != DOES_NOT_CONTAINKEY;
    }


    public boolean containsValue(Object value)
    {
        return _row.contains(value);
    }


    public Set<Entry<K, V>> entrySet()
    {
        Set<Entry<K, V>> r = new HashSet<>(_row.size() * 2);
        for (Entry<K, Integer> e : _findMap.entrySet())
        {
            int i = e.getValue();
            if (i < _row.size())
            {
                if (_row.get(i) != DOES_NOT_CONTAINKEY)
                    r.add(new Pair<>(e.getKey(), (V)_row.get(i)));
            }
        }
        return r;
    }


    public Set<K> keySet()
    {
        Set<K> ret = _findMap.keySet();
        assert null != (ret = Collections.unmodifiableSet(ret));
        return ret;

    }


    /** use getFindMap().remove(key) */
    public V remove(Object key)
    {
        if (_readonly)
            throw new IllegalStateException();
        Integer I = _findMap.get(key);
        if (null == I)
            return null;
        int i = I.intValue();
        while (i >= _row.size())
            _row.add(null);
        return convertToV(_row.set(i, DOES_NOT_CONTAINKEY));
    }


    public int size()
    {
        return _findMap.size();
    }


    public Collection<V> values()
    {
        ArrayList<V> a = new ArrayList<>(size());
        for (Object o : _row)
        {
            if (o != DOES_NOT_CONTAINKEY)
                a.add((V)o);
        }
        Collection<V> ret = a;
        assert null != (ret = Collections.unmodifiableCollection(ret));
        return ret;
    }


    /* ArrayListMap extensions (not part of Map) */

    public V get(int i)
    {
        return convertToV(i<_row.size() ? _row.get(i) : null);
    }

    /**
     * NOTE: we're not validating that you haven't removed the key!
     */
    public V set(int i, V value)
    {
        return convertToV(_row.set(i, value));
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


    public FindMap<K> getFindMap()
    {
        return _findMap;
    }

    protected List<Object> getRow()
    {
        return _row;
    }


    public void setReadOnly(boolean b)
    {
        _readonly = b;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            ArrayListMap<String,String> a = new ArrayListMap<>(4);
            a.put("A", "one");
            a.put("B", "two");
            a.put("C", "three");
            a.put("D", "four");

            ArrayListMap<String,String> b = new ArrayListMap<>(a, new ArrayList<String>());
            b.put("A", "ONE");
            b.put("E", "FIVE");
            a.put("F", "six");
            b.put("G", "SEVEN");

            assertEquals(a.get("A"), "one");
            assertEquals(b.get("A"), "ONE");

            assertTrue(a.containsKey("E"));
            assertNull(a.get("E"));
            assertEquals("FIVE", b.get("E"));
            assertNull(a.put("E", "five"));
            assertEquals("five", a.get("E"));
            assertEquals("FIVE", b.get("E"));

            assertTrue(b.containsKey("F"));
            assertNull(b.get("F"));
            assertEquals("six", a.get("F"));
            
            assertEquals("SEVEN", b.get("G"));
            assertTrue(b.containsKey("G"));
            assertEquals(7, b.values().size());
            assertEquals("SEVEN", b.remove("G"));
            assertNull(b.get("G"));
            assertFalse(b.containsKey("G"));
            assertEquals(6, b.values().size());
            assertNull(b.put("G","SEVENTY"));
            assertEquals("SEVENTY", b.get("G"));
            assertTrue(b.containsKey("G"));

            assertSame(a.getFindMap(), b.getFindMap());
        }
    }
}
