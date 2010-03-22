/*
 * Copyright (c) 2005-2009 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


/**
 * This is basically like SequencedHashMap
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

        public V getValue()
        {
            return _value;
        }

        public V setValue(V value)
        {
            V oldValue = getValue();
            _value = value;
            return oldValue;
        }

        private K _key;
        protected V _value;
        public int hash;
        public Entry<K, V> overflow;
        public Entry<K, V> next;
        public Entry<K, V> prev;
    }


    protected Entry<K, V> newEntry(int hash, K key)
    {
        return new Entry<K, V>(hash, key);
    }


    protected int bucketIndex(int hash)
    {
        return (hash & 0xeffffff) % buckets.length;
    }


    protected Entry<K, V> findOrAddEntry(K key)
    {
        int h = hash(key);
        Entry<K, V> e = findEntry(h, key);
        if (null == e)
            e = addEntry(newEntry(h, key));
        return e;
    }
    

    protected Entry<K, V> findEntry(Object key)
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


    protected Entry<K, V> findEntry(int hash, Object key)
    {
        Entry<K, V> e = buckets[bucketIndex(hash)];
        while (null != e)
        {
            if (hash == e.hash && eq(key, e.getKey()))
                break;
            e = e.overflow;
        }
        if (null != e && lru)
            moveToTail(e);
        return e;
    }


    protected Entry<K, V> removeEntry(K key)
    {
        return removeEntry(hash(key), key);
    }


    protected Entry removeEntry(Entry remove)
    {
        int hash = remove.hash;

        Entry prev = null;
        int h = bucketIndex(hash);
        Entry<K, V> e = buckets[h];
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


    protected Entry<K, V> removeEntry(int hash, K key)
    {
        Entry prev = null;
        int h = bucketIndex(hash);
        Entry<K, V> e = buckets[h];
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


    protected Entry<K, V> addEntry(Entry<K, V> e)
    {
        int h = bucketIndex(e.hash);
        e.overflow = buckets[h];
        buckets[h] = e;
        moveToTail(e);
        size++;
        return e;
    }


    protected void unlink(Entry e)
    {
        e.prev.next = e.next;
        e.next.prev = e.prev;
    }


    protected void moveToTail(Entry<K, V> e)
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


    protected Entry<K, V> firstEntry()
    {
        return head.next != head ? head.next : null;
    }


    private static final List<CacheMap> KNOWN_CACHEMAPS = new LinkedList<CacheMap>();

    private final String _debugName;

    protected Entry<K, V>[] buckets;
    protected Entry<K, V> head;
    protected int size = 0;
    protected boolean lru = false;

    protected Stats stats = new Stats();
    public Stats transactionStats = new Stats();

    protected static class Stats
    {
        private AtomicLong gets = new AtomicLong(0);
        private AtomicLong misses = new AtomicLong(0);
        private AtomicLong puts = new AtomicLong(0);
        private AtomicLong expirations = new AtomicLong(0);
        private AtomicLong removes = new AtomicLong(0);
        private AtomicLong clears = new AtomicLong(0);
        private AtomicLong max_size = new AtomicLong(0);
    }

    //
    // Map implementation
    //

    /**
     * size is max expected entries
     */

    public CacheMap(int initialSize, String debugName)
    {
        buckets = new Entry[(int) (initialSize * 1.5)];
        head = newEntry(0, null);
        assert debugName.length() > 0;
        _debugName = debugName;
        addToKnownCacheMaps();
    }


    protected void addToKnownCacheMaps()
    {
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


    public static List<CacheMap> getKnownCacheMaps()
    {
        List<CacheMap> copy = new ArrayList<CacheMap>();

        synchronized (KNOWN_CACHEMAPS)
        {
            for (CacheMap cachemap : KNOWN_CACHEMAPS)
            {
                copy.add(cachemap);
            }
        }

        return copy;
    }


    @Override
    public void clear()
    {
        Arrays.fill(buckets, null);
        size = 0;
        head.prev = head;
        head.next = head;
        trackClear();
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
        trackPut(value);
        return prev;
    }


    @Override
    public V get(Object key)
    {
        Entry<K, V> e = findEntry(key);
        if (null == e)
            return trackGet(null);
        return trackGet(e.getValue());
    }


    protected V trackGet(V value)
    {
        stats.gets.incrementAndGet();

        if (value == null)
            stats.misses.incrementAndGet();

        return value;
    }


    protected void trackPut(V value)
    {
        assert null != value : "Attempt to cache null into " + getDebugName() + "; must use marker for null instead.";
        stats.puts.incrementAndGet();

        long maxSize = stats.max_size.get();
        long currentSize = size();
        if (currentSize > maxSize)
            stats.max_size.compareAndSet(maxSize, currentSize);
    }


    protected void trackExpiration()
    {
        stats.expirations.incrementAndGet();
    }


    protected void trackRemove()
    {
        stats.removes.incrementAndGet();
    }


    protected void trackClear()
    {
        stats.clears.incrementAndGet();
    }


    public CacheStats getCacheStats()
    {
        return getCacheStats(stats, size);
    }


    public CacheStats getTransactionCacheStats()
    {
        return getCacheStats(transactionStats, 0);
    }


    private CacheStats getCacheStats(Stats stats, int size)
    {
        return new CacheStats(getDebugName(), stats.gets.get(), stats.misses.get(), stats.puts.get(), stats.expirations.get(), stats.removes.get(), stats.clears.get(), size, stats.max_size.get(), getLimit());
    }


    @Override
    public V remove(Object key)
    {
        Entry<K, V> e = removeEntry((K)key);
        if (null != e)
        {
            trackRemove();
            return e.getValue();
        }
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
        trackExpiration();
        return true;
    }


    protected boolean removeOldestEntry(Map.Entry<K, V> entry)
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
     * not as optimized as Map interfaces, since this is just the entrySet()
     */
    protected class CacheMapSet extends AbstractSet<Map.Entry<K, V>>
    {
        @Override
        public boolean add(Map.Entry<K, V> e)
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
            return new Iterator<Map.Entry<K, V>>()
            {
                Entry<K, V> current = head;
                Entry<K, V> next = head != null ? head.next : null; // to make remove easier


                public boolean hasNext()
                {
                    return head != null && head != next;
                }


                public Map.Entry<K, V> next()
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

    public String getDebugName()
    {
        return _debugName;
    }


    protected @Nullable Long getLimit()
    {
        return null;   // Unlimited... subclasses may implement a limit
    }


    @Override
    public String toString()
    {
        return (_debugName + " " + super.toString()).trim();
    }
}
