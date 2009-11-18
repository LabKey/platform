/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.commons.lang.time.DateUtils;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;

/**
 * User: matthewb
 * Date: Dec 11, 2006
 * Time: 3:49:11 PM
 */
public class TTLCacheMap<K, V> extends CacheMap<K, V>
{
    public static final long SECOND = DateUtils.MILLIS_PER_SECOND;
    public static final long MINUTE = DateUtils.MILLIS_PER_MINUTE;
    public static final long HOUR = DateUtils.MILLIS_PER_HOUR;
    public static final long DAY = DateUtils.MILLIS_PER_DAY;

    private final int maxSize;
    private final long defaultExpires;

    /*
     * NOTE: this is distinctly unlike the WeakHashMap.  The refererant here is
     * the value, not the key.  This allows GC to reclaim large cached objects.
     *
     * NOTE: the usual way to do this is to have Entry extend SoftReference
     * However, I just want to reuse base class as conveniently as possible.
     */
    private ReferenceQueue<V> _q = new ReferenceQueue<V>();

    // Static class -- different K & V from outer class
    private static class SoftEntry<K2, V2> extends SoftReference<V2>
    {
        private CacheMap.Entry<K2, V2> _entry;

        SoftEntry(CacheMap.Entry<K2, V2> e, V2 value, ReferenceQueue<V2> q)
        {
            super(value, q);
            _entry = e;
        }
    }


    private class TTLCacheEntry extends CacheMap.Entry<K, V>
    {
        private long _expires = -1;
        private SoftEntry<K, V> _ref = null;

        private TTLCacheEntry(int hash, K key, long expires)
        {
            super(hash, key);
            _expires = expires;
        }

        public V getValue()
        {
            return null == _ref ? null : _ref.get();
        }

        public V setValue(V value)
        {
            V old = getValue();
            _ref = new SoftEntry<K, V>(this, value, _q);
            return old;
        }

        private boolean expired()
        {
            return getValue() == null || _expires != -1 && System.currentTimeMillis() > _expires;
        }
    }


    @Override
    protected Entry<K, V> newEntry(int hash, K key)
    {
        return new TTLCacheEntry(hash, key, -1);
    }


    public TTLCacheMap(int maxSize, long defaultExpires, String debugName)
    {
        super(Math.min(10000, maxSize), debugName);
        this.lru = true;
        this.maxSize = maxSize;
        this.defaultExpires = defaultExpires;
    }
    

    public TTLCacheMap(int maxSize, String debugName)
    {
        this(maxSize, -1, debugName);
    }


    public int getMaxSize()
    {
        return maxSize;
    }

    public long getDefaultExpires()
    {
        return defaultExpires;
    }

    @Override
    public V put(K key, V value)
    {
        return put(key, value, defaultExpires);
    }


    public V put(K key, V value, long timeToLive)
    {
        assert timeToLive == -1 || timeToLive < 7 * DAY;

        Entry<K, V> e = findOrAddEntry(key);
        V prev = e.setValue(value);
        ((TTLCacheEntry) e)._expires = timeToLive == -1 ? -1 : System.currentTimeMillis() + timeToLive;
        testOldestEntry();
        return prev;
    }


    @Override
    public V get(Object key)
    {
        TTLCacheEntry e = (TTLCacheEntry)findEntry(key);
        if (null == e)
            return null;
        if (e.expired())
        {
            removeEntry(e);
            testOldestEntry();
            return null;
        }
        return e.getValue();
    }


    @Override
    public void clear()
    {
        for (Entry<K, V> entry = head.next; entry != head; entry = entry.next)
        {
            SoftEntry<K, V> r = ((TTLCacheEntry)entry)._ref;
            if (null != r)
                r.clear();
        }
        super.clear();
    }


    protected boolean removeOldestEntry(Map.Entry<K, V> mapEntry)
    {
        purge();
        return size > maxSize || ((TTLCacheEntry) mapEntry).expired();
    }


    // CONSIDER: generalize to removeAll(Filter)
    public void removeUsingPrefix(String prefix)
    {
        // since we're touching all the Entrys anyway, might as well test expired()
        for (Entry<K, V> entry = head.next; entry != head; entry = entry.next)
        {
            if (removeOldestEntry(entry))
                removeEntry(entry);
            else if (entry.getKey() instanceof String && ((String)entry.getKey()).startsWith(prefix))
                removeEntry(entry);
        }
    }

    void purge()
    {
        SoftEntry<K, V> e;
        while (null != (e = (SoftEntry<K, V>)_q.poll()))
        {
            removeEntry(e._entry);
        }
    }
}