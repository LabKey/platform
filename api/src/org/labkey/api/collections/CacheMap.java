/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import java.util.*;


/**
 * This is bascially like SequencedHashMap
 * <p/>
 * It is designed to be efficient like LinkedHashMap, but subclassable like
 * SequencedHashMap.
 * <p/>
 * It is intended to be used as a base class for customized caching functionality
 */

public class CacheMap<K, V> extends AbstractMap<K, V>
{
    //
    // Internal implementation of hash table of Map.Entry
    //

    protected static class Entry<K, V> implements Map.Entry<K, V>
    {
        protected Entry()
        {
            next = this;
            prev = this;
        }

        protected Entry(K key)
        {
            this(key.hashCode(), key);
        }

        protected Entry(int h, K key)
        {
            next = this;
            prev = this;
            hash = h;
            _key = key;
        }

        public K getKey()
        {
            return _key;
        }

        public void setKey(K key)
        {
            throw new UnsupportedOperationException("can't set key");
        }

        public V getValue()
        {
            return (V)_value;
        }

        public V setValue(V value)
        {
            V oldValue = getValue();
            _value = value;
            return oldValue;
        }

        private K _key;
        protected Object _value;
        public int hash;
        public Entry overflow;
        public Entry next;
        public Entry prev;
    }


    protected Entry<K,V> newEntry(int hash, K key)
    {
        return new Entry<K,V>(hash, key);
    }


    protected int bucketIndex(int hash)
    {
        return (hash & 0xeffffff) % buckets.length;
    }


    protected Entry<K, V> findOrAddEntry(K key)
    {
        int h = hash(key);
        Entry<K,V> e = findEntry(h, key);
        if (null == e)
            e = addEntry(newEntry(h, key));
        return e;
    }
    

    protected Entry<K,V> findEntry(Object key)
    {
        return findEntry(hash(key), key);
    }


    protected int hash(Object key)
    {
        return key == null ? 0 : key.hashCode();
    }


    protected boolean eq(Object a, Object b)
    {
        return a == null ? b == null : a.equals(b);
    }


    protected Entry<K,V> findEntry(int hash, Object key)
    {
        Entry e = buckets[bucketIndex(hash)];
        while (null != e)
        {
            if (hash == e.hash && eq(key, e.getKey()))
                break;
            e = e.overflow;
        }
        if (null != e && lru)
            moveToTail(e);
        return (Entry<K,V>)e;
    }


    protected Entry removeEntry(K key)
    {
        return removeEntry(hash(key), key);
    }


    protected Entry removeEntry(Entry remove)
    {
        int hash = remove.hash;

        Entry prev = null;
        int h = bucketIndex(hash);
        Entry e = buckets[h];
        while (null != e)
        {
            if (e == remove)
                break;
            prev = e;
            e = e.overflow;
        }
        if (null != e)
        {
            if (null == prev)
                buckets[h] = e.overflow;
            else
                prev.overflow = e.overflow;
            unlink(e);
            size--;
        }
        return e;
    }


    protected Entry removeEntry(int hash, K key)
    {
        Entry prev = null;
        int h = bucketIndex(hash);
        Entry e = buckets[h];
        while (null != e)
        {
            if (hash == e.hash && eq(key,e.getKey()))
                break;
            prev = e;
            e = e.overflow;
        }
        if (null != e)
        {
            if (null == prev)
                buckets[h] = e.overflow;
            else
                prev.overflow = e.overflow;
            unlink(e);
            size--;
        }
        return e;
    }


    protected Entry<K,V> addEntry(Entry e)
    {
        int h = bucketIndex(e.hash);
        e.overflow = buckets[h];
        buckets[h] = e;
        moveToTail(e);
        size++;
        return (Entry<K,V>)e;
    }


    protected void unlink(Entry e)
    {
        e.prev.next = e.next;
        e.next.prev = e.prev;
    }


    protected void moveToTail(Entry e)
    {
        // unlink (NOOP for new entry)
        e.prev.next = e.next;
        e.next.prev = e.prev;
        // add to end of list
        e.next = head;
        e.prev = head.prev;
        head.prev.next = e;
        head.prev = e;
    }


    protected Entry<K,V> firstEntry()
    {
        return (Entry<K,V>)head.next != head ? head.next : null;
    }


    protected String debugName = "";
    protected Entry[] buckets;
    protected Entry head;
    protected int size = 0;
    protected boolean lru = false;
    private static final List<CacheMap> KNOWN_CACHEMAPS = new ArrayList<CacheMap>();

    //
    // Map implementation
    //

    /**
     * size is max expected entries
     */
    public CacheMap(int initialSize)
    {
        buckets = new Entry[(int) (initialSize * 1.5)];
        head = newEntry(0,null);
        synchronized (KNOWN_CACHEMAPS)
        {
            KNOWN_CACHEMAPS.add(this);
        }
    }


    public static void purgeAllCaches()
    {
        synchronized (KNOWN_CACHEMAPS)
        {
            for (CacheMap cmap : KNOWN_CACHEMAPS)
            {
                cmap.clear();
            }
        }
    }


    @Override
    public void clear()
    {
        Arrays.fill(buckets, null);
        size = 0;
        head.prev = head;
        head.next = head;
    }


    @Override
    public boolean containsKey(Object key)
    {
        Entry e = findEntry(key);
        return null != e;
    }


    @Override
    public V put(K key, V value)
    {
        Entry<K, V> e = findOrAddEntry(key);
        V prev = e.setValue(value);
        testOldestEntry();
        return prev;
    }


    @Override
    public V get(Object key)
    {
        Entry<K, V> e = findEntry(key);
        if (null == e)
            return null;
        return e.getValue();
    }


    @Override
    public V remove(Object key)
    {
        Entry<K, V> e = removeEntry((K)key);
        if (null != e)
            return e.getValue();
        return null;
    }


    protected boolean testOldestEntry()
    {
        Entry<K, V> eldest = firstEntry();
        if (null == eldest)
            return false;
        if (!removeOldestEntry(eldest))
            return false;
        removeEntry(eldest);
        return true;
    }


    protected boolean removeOldestEntry(Map.Entry entry)
    {
        return false;
    }


    @Override
    public int size()
    {
        return size;
    }

    //
    // entrySet()
    //

    public Set<Map.Entry<K, V>> entrySet()
    {
        return new CacheMapSet();
    }


    /**
     * not as optimized as Map interfaces,  since this is just the entrySet()
     */
    protected class CacheMapSet extends AbstractSet<Map.Entry<K, V>>
    {
        public boolean add(Entry<K, V> e)
        {
            throw new UnsupportedOperationException("can't add to entrySet");
        }


        @Override
        public void clear()
        {
            CacheMap.this.clear();
        }


        @Override
        public boolean contains(Object o)
        {
            Entry e = (Entry) o;
            Entry find = CacheMap.this.findEntry(e.getKey());
            return o.equals(find);
        }


        public Iterator<Map.Entry<K, V>> iterator()
        {
            // UNDONE concurrent modification checking
            return new Iterator()
            {
                Entry<K, V> current = head;
                Entry<K, V> next = head != null ? head.next : null; // to make remove easier


                public boolean hasNext()
                {
                    return head != null && head != next;
                }


                public Object next()
                {
                    if (head == next)
                        throw new NoSuchElementException();
                    current = next;
                    next = current.next;
                    return current;
                }


                public void remove()
                {
                    if (null == current)
                        throw new IllegalStateException();
                    if (head == current)
                        throw new NoSuchElementException();
                    removeEntry(current.hash, current.getKey());
                    current = null;
                }
            };
        }


        @Override
        public boolean remove(Object o)
        {
            return CacheMap.this.removeEntry((Entry<K, V>) o) != null;
        }


        public int size()
        {
            return CacheMap.this.size();
        }
    }

    public void setDebugName(String name)
    {
        debugName = name;
    }


    @Override
    public String toString()
    {
        return (debugName + " " + super.toString()).trim();
    }
}
