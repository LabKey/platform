/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.cache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheType;
import org.labkey.api.cache.SimpleCache;
import org.labkey.api.util.Filter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
* User: adam
* Date: 12/25/11
* Time: 8:17 PM
*/
class EhSimpleCache<K, V> implements SimpleCache<K, V>
{
    private static final Logger LOG = Logger.getLogger(EhSimpleCache.class);

    private final Cache _cache;

    EhSimpleCache(Cache cache)
    {
        _cache = cache;
    }

    @Override
    public void put(K key, V value)
    {
        Element element = new Element(key, value);
        _cache.put(element);
    }

    @Override
    public void put(K key, V value, long timeToLive)
    {
        Element element = new Element(key, value);
        element.setTimeToLive((int)timeToLive / 1000);
        _cache.put(element);
    }

    @Override
    public @Nullable V get(K key)
    {
        Element e = _cache.get(key);
        return null == e ? null : (V)e.getObjectValue();
    }

    @Override
    public void remove(K key)
    {
        _cache.remove(key);
    }

    @Override
    public int removeUsingFilter(Filter<K> filter)
    {
        int removes = 0;
        List<K> keys = _cache.getKeys();

        for (K key : keys)
        {
            if (filter.accept(key))
            {
                remove(key);
                removes++;
            }
        }

        return removes;
    }

    @Override
    public Set<K> getKeys()
    {
        // EhCache provides keys as a "set-like" list; make it a real Set
        return new HashSet<K>(_cache.getKeys());
    }

    @Override
    public void clear()
    {
        _cache.removeAll();
    }

    @Override
    public int getLimit()
    {
        return _cache.getCacheConfiguration().getMaxElementsInMemory();
    }

    @Override
    public int size()
    {
        return (int)_cache.getStatistics().getObjectCount();
    }

    @Override
    public boolean isEmpty()
    {
        return 0 == size();
    }

    @Override
    public long getDefaultExpires()
    {
        return _cache.getCacheConfiguration().getTimeToLiveSeconds() * 1000;
    }

    @Override
    public CacheType getCacheType()
    {
        return CacheType.NonDeterministicLRU;
    }

    @Override
    public void close()
    {
        EhCacheProvider.getInstance().closeCache(_cache);
    }

    @Override
    public void log()
    {
        StringBuilder sb = new StringBuilder();

        for (K key : (List<K>)_cache.getKeys())
        {
            sb.append(key).append(" -> ").append(get(key)).append("\n");
        }

        LOG.info(sb);
    }
}
