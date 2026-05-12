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

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.Pair;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;


public class ArrayListMap<K, V> extends AbstractMap<K, V> implements Iterable<V>, Serializable
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
        private final Map<K,Integer> _map;
        private int _max = -1;

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
            if (null == integer)
            {
                throw new IllegalArgumentException("Indices must be non-null");
            }
            assert !containsValue(integer) : "Duplicate index " + integer + ". Current values: " + _map;
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
        public void putAll(@NotNull Map<? extends K, ? extends Integer> map)
        {
            _map.putAll(map);
        }

        @Override
        public void clear()
        {
            _map.clear();
        }

        @Override
        public @NotNull Set<K> keySet()
        {
            return _map.keySet();
        }

        @Override
        public @NotNull Collection<Integer> values()
        {
            return _map.values();
        }

        @Override
        public @NotNull Set<Entry<K, Integer>> entrySet()
        {
            return _map.entrySet();
        }
    }


    private final FindMap<K> _findMap;
    private final List<Object> _row;

    public ArrayListMap()
    {
        this(new FindMap<>(new HashMap<>()), new ArrayList<>());
    }


    public ArrayListMap(int columnCount)
    {
        this(new FindMap<>(new HashMap<>(columnCount * 2)), new ArrayList<>(columnCount));
    }


    public ArrayListMap(ArrayListMap<K, V> m, List<V> row)
    {
        this(m.getFindMap(), row);
    }


    public ArrayListMap(FindMap<K> findMap)
    {
        this(findMap, new ArrayList<>(findMap.size()));
    }


    public ArrayListMap(FindMap<K> findMap, List<V> row)
    {
        _findMap = findMap;
        _row = (List<Object>)row;
    }


    @Override
    public V get(Object key)
    {
        Integer I = _findMap.get(key);
        if (I == null)
            return null;
        int i = I.intValue();
        Object v = i>=_row.size() ? null : _row.get(i);
        return convertToV(v);
    }


    @Override
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
            _row.add(DOES_NOT_CONTAINKEY);
        return convertToV(_row.set(i,value));
    }


    @Override
    public void clear()
    {
        if (_readonly)
            throw new IllegalStateException();
        for (int i=0 ; i<_row.size() ; i++)
            _row.set(i, DOES_NOT_CONTAINKEY);
    }


    @Override
    public boolean containsKey(Object key)
    {
        Integer I = _findMap.get(key);
        if (I == null)
            return false;
        int i = I.intValue();
        Object v = i>=_row.size() ? null : _row.get(i);
    return v != DOES_NOT_CONTAINKEY;
    }


    @Override
    public boolean containsValue(Object value)
    {
        return _row.contains(value);
    }


    /* ArrayList order please */
    @Override
    public @NotNull Set<Entry<K, V>> entrySet()
    {
        // This is not particularly fast, but that's probably OK
        // CONSIDER: use a LinkedHashMap to implement FindMap and skip this alignment step
        ArrayList<Entry<K, V>> entryList = new ArrayList<>(_findMap.size());
        for (var findEntry : _findMap.entrySet())
        {
            K key = findEntry.getKey();
            int i = findEntry.getValue();
            Object v = i>=_row.size() ? DOES_NOT_CONTAINKEY : _row.get(i);
            if (v != DOES_NOT_CONTAINKEY)
            {
                while (entryList.size() <= i)
                    entryList.add(null);
                entryList.set(i, new Pair<>(key, (V) v));
            }
        }
        var ret = new LinkedHashSet<Entry<K, V>>();
        entryList.stream().filter(Objects::nonNull).forEach(ret::add);
        return ret;
    }


    /** use getFindMap().remove(key) */
    @Override
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


    @Override
    public int size()
    {
        return _findMap.size();
    }


    @Override
    public @NotNull Collection<V> values()
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


    @NotNull
    @Override
    public Iterator<V> iterator()
    {
        return new Iterator<>()
        {
            private int _i = 0;

            {
                while (_i < _row.size() && _row.get(_i) == DOES_NOT_CONTAINKEY)
                    _i++;
            }

            @Override
            public boolean hasNext()
            {
                return _i < _row.size();
            }

            @Override
            public V next()
            {
                if (_i >= _row.size())
                    throw new NoSuchElementException();
                var ret = _row.get(_i++);
                assert ret != DOES_NOT_CONTAINKEY;
                while (_i < _row.size() && _row.get(_i) == DOES_NOT_CONTAINKEY)
                    _i++;
                return (V)ret;
            }
        };
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
    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        buf.append("{");

        Iterator<Map.Entry<K, Integer>> i = _findMap.entrySet().iterator();
        String separator = "";
        while (i.hasNext())
        {
            Map.Entry<K, Integer> e = i.next();
            Object key = e.getKey();
            int index = e.getValue().intValue();
            Object value = index >= _row.size() ? DOES_NOT_CONTAINKEY : _row.get(index);

            if (value != DOES_NOT_CONTAINKEY)
            {
                buf.append(separator);
                if (key == this)
                    buf.append("(this Map)");
                else
                    buf.append(key);
                buf.append("=");
                if (value == this)
                    buf.append("(this Map)");
                else
                    buf.append(value);
                separator = ", ";
            }
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
        private ArrayListMap<String, String> createAndPopulate()
        {
            ArrayListMap<String, String> a = new ArrayListMap<>(4);
            a.put("Z", "one");
            a.put("B", "two");
            a.put("C", "three");
            a.put("D", "four");
            return a;
        }

        @Test
        public void testToString()
        {
            ArrayListMap<String, String> a = createAndPopulate();
            assertEquals("{Z=one, B=two, C=three, D=four}", a.toString());

            ArrayListMap<String,String> b = new ArrayListMap<>(a, new ArrayList<>());
            b.put("G", "test");
            assertEquals("{G=test}", b.toString());
            b.put("Z", "test2");
            assertEquals("{Z=test2, G=test}", b.toString());

            assertEquals("{Z=one, B=two, C=three, D=four}", a.toString());
        }
        @Test
        public void test()
        {
            ArrayListMap<String, String> a = createAndPopulate();

            {
                assertEquals("one", a.get("Z"));
                assertEquals("one", a.get(0));
                assertEquals("two", a.get("B"));
                assertEquals("two", a.get(1));
                assertEquals("three", a.get("C"));
                assertEquals("three", a.get(2));
                assertEquals("four", a.get("D"));
                assertEquals("four", a.get(3));
            }

            {
                var it = a.iterator();
                assertTrue(it.hasNext());
                assertEquals("one", it.next());
                assertTrue(it.hasNext());
                assertEquals("two", it.next());
                assertTrue(it.hasNext());
                assertEquals("three", it.next());
                assertTrue(it.hasNext());
                assertEquals("four", it.next());
                assertFalse(it.hasNext());
            }

            {
                var it = a.entrySet().iterator();
                assertTrue(it.hasNext());
                assertEquals("Z", it.next().getKey());
                assertEquals("B", it.next().getKey());
                assertEquals("C", it.next().getKey());
                assertEquals("D", it.next().getKey());
                assertFalse(it.hasNext());
            }

            {
                var it = a.keySet().iterator();
                assertTrue(it.hasNext());
                assertEquals("Z", it.next());
                assertEquals("B", it.next());
                assertEquals("C", it.next());
                assertEquals("D", it.next());
                assertFalse(it.hasNext());
            }

            {
                var it = a.values().iterator();
                assertTrue(it.hasNext());
                assertEquals("one", it.next());
                assertEquals("two", it.next());
                assertEquals("three", it.next());
                assertEquals("four", it.next());
                assertFalse(it.hasNext());
            }

            var it = a.entrySet().iterator();

            ArrayListMap<String,String> b = new ArrayListMap<>(a, new ArrayList<>());
            b.put("Z", "ONE");
            b.put("E", "FIVE");
            a.put("F", "six");
            b.put("G", "SEVEN");

            assertEquals("one", a.get("Z"));
            assertEquals("ONE", b.get("Z"));

            assertFalse(a.containsKey("E"));
            assertNull(a.get("E"));
            assertTrue(b.containsKey("E"));
            assertEquals("FIVE", b.get("E"));
            assertNull(a.put("E", "five"));
            assertEquals("five", a.get("E"));
            assertEquals("FIVE", b.get("E"));

            assertFalse(b.containsKey("F"));
            assertTrue(a.containsKey("F"));
            assertNull(b.get("F"));
            assertEquals("six", a.get("F"));
            
            assertEquals("SEVEN", b.get("G"));
            assertTrue(b.containsKey("G"));
            assertEquals(3, b.values().size());
            assertEquals("SEVEN", b.remove("G"));
            assertNull(b.get("G"));
            assertFalse(b.containsKey("G"));
            assertEquals(2, b.values().size());
            assertNull(b.put("G","SEVENTY"));
            assertEquals("SEVENTY", b.get("G"));
            assertTrue(b.containsKey("G"));

            assertSame(a.getFindMap(), b.getFindMap());
        }
    }
}
