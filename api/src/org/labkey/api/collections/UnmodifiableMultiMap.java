package org.labkey.api.collections;

import org.apache.commons.collections15.IteratorUtils;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.Unmodifiable;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: 2/9/2015
 * Time: 6:18 PM
 */
public class UnmodifiableMultiMap<K, V> implements MultiMap<K, V>, Unmodifiable
{
    private final MultiMap<K, V> _map;

    public UnmodifiableMultiMap(MultiMap<K, V> map)
    {
        _map = map;
    }

    @Override
    public int size(Object o)
    {
        return _map.size(o);
    }

    @Override
    public int size()
    {
        return _map.size();
    }

    @Override
    public Collection<V> get(Object o)
    {
        return _map.get(o);
    }

    @Override
    public boolean containsValue(Object o)
    {
        return _map.containsValue(o);
    }

    @Override
    public boolean containsValue(Object o, Object o1)
    {
        return _map.containsValue(o, o1);
    }

    @Override
    public Collection<V> values()
    {
        return _map.values();
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
    public Iterator<V> iterator(Object o)
    {
        return _map.iterator(o);
    }

    @Override
    public Set<K> keySet()
    {
        return _map.keySet();
    }

    @Override
    public Set<Map.Entry<K, Collection<V>>> entrySet()
    {
        return _map.entrySet();
    }

    @Override
    public Map<K, Collection<V>> map()
    {
        return _map.map();
    }

    // ========== Methods below here are not allowed ==========

    @Override
    public V put(K k, V v)
    {
        throw new UnsupportedOperationException("This MultiMap is unmodifiable");
    }

    @Override
    public V remove(Object o, Object o1)
    {
        throw new UnsupportedOperationException("This MultiMap is unmodifiable");
    }

    @Override
    public Collection<V> remove(Object o)
    {
        throw new UnsupportedOperationException("This MultiMap is unmodifiable");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map)
    {
        throw new UnsupportedOperationException("This MultiMap is unmodifiable");
    }

    @Override
    public void putAll(MultiMap<? extends K, ? extends V> multiMap)
    {
        throw new UnsupportedOperationException("This MultiMap is unmodifiable");
    }

    @Override
    public boolean putAll(K k, Collection<? extends V> collection)
    {
        throw new UnsupportedOperationException("This MultiMap is unmodifiable");
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException("This MultiMap is unmodifiable");
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            MultiMap<String, String> mmap = new MultiHashMap<>();
            mmap.put("abc", "def");
            mmap.put("abc", "ghi");
            mmap.put("abc", "jkl");
            mmap.put("xyz", "mno");
            mmap.put("xyz", "pqr");

            MultiMap<String, String> unmodifiable = new UnmodifiableMultiMap<>(mmap);

            assertEquals(unmodifiable.size(), 2);
            assertEquals(unmodifiable.size("abc"), 3);
            assertEquals(unmodifiable.size("xyz"), 2);
            assertEquals(unmodifiable.get("abc").size(), 3);
            assertTrue(unmodifiable.containsValue("pqr"));
            assertTrue(unmodifiable.containsValue("def"));
            assertTrue(unmodifiable.containsValue("abc", "def"));
            assertFalse(unmodifiable.containsValue("abc", "mno"));
            assertEquals(unmodifiable.values().size(), 5);
            assertFalse(unmodifiable.isEmpty());
            assertTrue(unmodifiable.containsKey("abc"));
            assertFalse(unmodifiable.containsKey("def"));
            assertEquals(IteratorUtils.toList(unmodifiable.iterator("abc")).size(), 3);
            assertEquals(unmodifiable.keySet().size(), 2);
            assertEquals(unmodifiable.entrySet().size(), 2);
            assertEquals(unmodifiable.map().size(), 2);

            try
            {
                unmodifiable.put("abc", "stu");
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException ignored)
            {
            }

            try
            {
                unmodifiable.remove("abc", "def");
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException ignored)
            {
            }

            try
            {
                unmodifiable.remove("abc");
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException ignored)
            {
            }

            try
            {
                unmodifiable.putAll(Collections.<String, String>emptyMap());
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException ignored)
            {
            }

            try
            {
                unmodifiable.putAll(new MultiHashMap<String, String>());
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException ignored)
            {
            }

            try
            {
                unmodifiable.putAll("stu", PageFlowUtil.set("abc", "def"));
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException ignored)
            {
            }

            try
            {
                unmodifiable.clear();
                fail("Expected UnsupportedOperationException");
            }
            catch (UnsupportedOperationException ignored)
            {
            }
        }
    }
}
