/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.commons.lang.time.DateUtils;

import java.lang.ref.SoftReference;
import java.lang.ref.ReferenceQueue;
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

    int maxSize = 1000;
    long defaultExpires = -1;

    /*
     * NOTE: this is distinctly unlike the WeakHashMap.  The refererant here is
     * the value, not the key.  This allows GC to reclaim large cached objects.
     *
     * NOTE: the usual way to do this is to have Entry extend SoftReference
     * However, I just want to reuse base class as conveniently as possible.
     */
    ReferenceQueue<V> _q = new ReferenceQueue<V>();

    static class SoftEntry<K,V> extends SoftReference<V>
    {
        CacheMap.Entry<K,V> _entry;

        SoftEntry(CacheMap.Entry<K,V> e, V value, ReferenceQueue q)
        {
            super(value, q);
            _entry = e;
        }
    }


    class TTLCacheEntry<K, V> extends CacheMap.Entry<K, V>
    {
        long expires = -1;

        TTLCacheEntry(int hash, K key, long expires)
        {
            super(hash, key);
            this.expires = expires;
        }

        public V getValue()
        {
            return null == _value ? null : ((SoftEntry<K,V>)_value).get();
        }

        public V setValue(V value)
        {
            V old = getValue();
            _value = new SoftEntry(this,value,_q);
            return old;
        }

        boolean expired()
        {
            return getValue() == null || expires != -1 && System.currentTimeMillis() > expires;
        }
    }


    @Override
    protected Entry<K, V> newEntry(int hash, K key)
    {
        return new TTLCacheEntry(hash, key, -1);
    }


    public TTLCacheMap(int maxSize, long defaultExpires)
    {
        super(maxSize);
        this.lru = true;
        this.maxSize = maxSize;
        this.defaultExpires = defaultExpires;
    }
    

    public TTLCacheMap(int maxSize)
    {
        this(maxSize, -1);
    }


    @Override
    public V put(K key, V value)
    {
        return put(key, value, defaultExpires);
    }


    public V put(K key, V value, long timeToLive)
    {
        assert timeToLive == -1 || timeToLive < 7*DAY;

        Entry<K,V> e = findOrAddEntry(key);
        V prev = e.setValue(value);
        assert e instanceof TTLCacheEntry;
        ((TTLCacheEntry) e).expires = timeToLive == -1 ? -1 : System.currentTimeMillis() + timeToLive;
        testOldestEntry();
        return prev;
    }


    @Override
    public V get(Object key)
    {
        TTLCacheEntry<K, V> e = (TTLCacheEntry<K, V>) findEntry(key);
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
            SoftEntry<K,V> r = (SoftEntry<K,V>)entry._value;
            if (null != r)
                r.clear();
        }
        super.clear();
    }


    protected boolean removeOldestEntry(Map.Entry mapEntry)
    {
        purge();
        return size > maxSize || ((TTLCacheEntry) mapEntry).expired();
    }


    // CONSIDER: generalize to removeAll(Filter)
    public void removeUsingPrefix(String prefix)
    {
        // since we're touching all the Entrys anyway, might as well test expired()
        long ms = System.currentTimeMillis();
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
        SoftEntry<K,V> e;
        while (null != (e = (SoftEntry<K,V>)_q.poll()))
        {
            removeEntry(e._entry);
        }
    }
}