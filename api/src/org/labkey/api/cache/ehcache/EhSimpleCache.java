/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheType;
import org.labkey.api.cache.SimpleCache;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.IntegerUtils;

import java.util.List;
import java.util.stream.Stream;

class EhSimpleCache<K, V> implements SimpleCache<K, V>
{
    private static final Logger LOG = LogManager.getLogger(EhSimpleCache.class);

    private final Cache _cache;
    private final String _debugName;

    EhSimpleCache(Cache cache, String debugName)
    {
        _cache = cache;
        _debugName = debugName;
    }

    private void validateKey(K key)
    {
        // Ehcache keeps the first key object stored for an entry, so a Container or User key pins a stale copy
        String keyType = key instanceof Container ? "Container" : key instanceof User ? "User" : null;
        if (null != keyType)
            throw new IllegalArgumentException(keyType + " used as a key in cache \"" + _debugName + "\". Pass " + keyType + ".class (keyClass) to the Cache factory method to allow this.");
    }

    @Override
    public void put(@NotNull K key, V value)
    {
        validateKey(key);
        Element element = new Element(key, value);
        _cache.put(element);
    }

    @Override
    public void put(@NotNull K key, V value, long timeToLive)
    {
        validateKey(key);
        Element element = new Element(key, value);
        element.setTimeToLive(IntegerUtils.asInteger(timeToLive / 1000)); // Convert from ms to sec
        _cache.put(element);
    }

    @Override
    public @Nullable V get(@NotNull K key)
    {
        Element e = _cache.get(key);
        return null == e ? null : (V)e.getObjectValue();
    }

    @Override
    public boolean remove(@NotNull K key)
    {
        return _cache.remove(key);
    }

    @Override
    public Stream<K> getKeys()
    {
        // Stream EhCache's "set-like" list of keys
        return (Stream<K>)_cache.getKeys().stream();
    }

    @Override
    public void clear()
    {
        _cache.removeAll();
    }

    @Override
    public int getLimit()
    {
        return (int)_cache.getCacheConfiguration().getMaxEntriesLocalHeap();
    }

    @Override
    public int size()
    {
        return (int)_cache.getStatistics().getSize();
    }

    @Override
    public int getExpirations()
    {
        return (int)_cache.getStatistics().cacheExpiredCount();
    }

    @Override
    public int getEvictions()
    {
        return (int)_cache.getStatistics().cacheEvictedCount();
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
