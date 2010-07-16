/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.cache.implementation;

import org.apache.log4j.Logger;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Filter;
import org.labkey.api.util.HeartBeat;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;

/**
 * User: matthewb
 * Date: Dec 11, 2006
 * Time: 3:49:11 PM
 */

// Unsynchronized TTL cache implementation -- only for internal use by other cache implementations.
public class TTLCacheMap<K, V> extends CacheMap<K, V>
{
    private static final Logger LOG = Logger.getLogger(TTLCacheMap.class);

    private final int _limit;
    private final long _defaultExpires;

    /*
     * NOTE: this is distinctly unlike the WeakHashMap.  The referent here is
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
            return getValue() == null || _expires != -1 && HeartBeat.currentTimeMillis() > _expires;
        }
    }


    @Override
    protected Entry<K, V> newEntry(int hash, K key)
    {
        return new TTLCacheEntry(hash, key, -1);
    }

    TTLCacheMap(int limit, long defaultExpires, String debugName)
    {
        // Limit the initial size of the underlying map (it will grow if necessary)
        super(Math.min(10000, limit), debugName);
        this.lru = true;
        _limit = limit;
        _defaultExpires = defaultExpires;
    }
    

    public TTLCacheMap(int limit, String debugName)
    {
        this(limit, -1, debugName);
    }


    public long getDefaultExpires()
    {
        return _defaultExpires;
    }

    @Override
    public V put(K key, V value)
    {
        return put(key, value, _defaultExpires);
    }


    public V put(K key, V value, long timeToLive)
    {
        assert timeToLive == -1 || timeToLive < 7 * CacheManager.DAY;

        Entry<K, V> e = findOrAddEntry(key);
        V prev = e.setValue(value);
        ((TTLCacheEntry) e)._expires = timeToLive == -1 ? -1 : HeartBeat.currentTimeMillis() + timeToLive;
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


    // If we see more than 1 million elements then we must have a loop
    private static final int TRAVERSE_LIMIT = 1000000;

    @Override
    public void clear()
    {
        int corruptionDetector = 0;

        for (Entry<K, V> entry = head.next; entry != head; entry = entry.next)
        {
            SoftEntry<K, V> r = ((TTLCacheEntry)entry)._ref;
            if (null != r)
                r.clear();

            // Bail out after visiting 1 million entries... must be a corrupt map
            if (corruptionDetector++ > TRAVERSE_LIMIT)
            {
                reportCorruption(entry);
                break;
            }
        }

        super.clear();
    }

    private void reportCorruption(Entry<K, V> entry)
    {
        StringBuilder message = new StringBuilder("Corrupt TTLCacheMap detected: \"" + toString() + "\". Listing 100 entries that may be part of a loop:\n");

        for (int i = 0; i < 100 && null != entry; i++)
        {
            message.append("  ");
            message.append(entry.getKey());
            message.append("\n");

            entry = entry.next;
        }

        LOG.error(message);

        if (AppProps.getInstance().isDevMode())
            throw new IllegalStateException("Corrupt TTLCacheMap detected: \"" + toString() + "\"");
    }


    protected boolean removeOldestEntry(Map.Entry<K, V> mapEntry)
    {
        purge();
        return size > _limit || ((TTLCacheEntry) mapEntry).expired();
    }


    public int removeUsingFilter(Filter<K> filter)
    {
        int removes = 0;
        int corruptionDetector = 0;

        // since we're touching all the Entrys anyway, might as well test expired()
        for (Entry<K, V> entry = head.next; entry != head; entry = entry.next)
        {
            if (removeOldestEntry(entry))
            {
                removeEntry(entry);
            }
            else if (filter.accept(entry.getKey()))
            {
                removes++;
                removeEntry(entry);
            }

            // Bail out after visiting 1 million entries... must be a corrupt map
            if (corruptionDetector++ > TRAVERSE_LIMIT)
            {
                reportCorruption(entry);
                break;
            }
        }

        return removes;
    }


    void purge()
    {
        SoftEntry<K, V> e;
        while (null != (e = (SoftEntry<K, V>)_q.poll()))
        {
            removeEntry(e._entry);
        }
    }

    @Override
    protected int getLimit()
    {
        return _limit;
    }
}