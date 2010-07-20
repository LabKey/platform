/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.cache;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 9:47:03 AM
 */

// TODO: Track expirations?
// Wraps a generic BasicCache to provide a full Cache implementation.  Adds statistics gathering and debug name handling.
public class CacheWrapper<K, V> implements Cache<K, V>
{
    private final BasicCache<K, V> _cache;
    private final String _debugName;
    private final Stats _stats;
    private final Stats _transactionStats;

    public CacheWrapper(@NotNull BasicCache<K, V> cache, @NotNull String debugName, @Nullable Stats stats)
    {
        _cache = cache;
        assert StringUtils.isNotBlank(debugName);
        _debugName = debugName;
        _stats = (null != stats ? stats : new Stats());
        _transactionStats = new Stats();
    }

    @Override
    public void put(K key, V value)
    {
        _cache.put(key, value);
        trackPut(value);
    }

    @Override
    public void put(K key, V value, long timeToLive)
    {
        _cache.put(key, value, timeToLive);
        trackPut(value);
    }

    @Override
    public V get(K key)
    {
        return trackGet(_cache.get(key));
    }

    @Override
    public void remove(K key)
    {
        _cache.remove(key);
        trackRemove();
    }

    @Override
    public int removeUsingFilter(Filter<K> kFilter)
    {
        return trackRemoves(_cache.removeUsingFilter(kFilter));
    }

    @Override
    public void clear()
    {
        _cache.clear();
        trackClear();
    }

    @Override
    public int getLimit()
    {
        return _cache.getLimit();
    }

    @Override
    public int size()
    {
        return _cache.size();
    }

    @Override
    public long getDefaultExpires()
    {
        return _cache.getDefaultExpires();
    }

    @Override
    public CacheType getCacheType()
    {
        return _cache.getCacheType();
    }

    @Override
    public void close()
    {
        _cache.close();
    }

    @Override
    public String getDebugName()
    {
        return _debugName;
    }

    @Override
    public Stats getStats()
    {
        return _stats;
    }

    @Override
    public Stats getTransactionStats()
    {
        return _transactionStats;
    }

    @Override
    public String toString()
    {
        return getDebugName();
    }

    private V trackGet(V value)
    {
        _stats.gets.incrementAndGet();

        if (value == null)
            _stats.misses.incrementAndGet();

        return value;
    }

    private void trackPut(V value)
    {
        assert null != value : "Attempt to cache null into " + getDebugName() + "; must use marker for null instead.";
        _stats.puts.incrementAndGet();

        long maxSize = _stats.max_size.get();
        long currentSize = size();
        if (currentSize > maxSize)
            _stats.max_size.compareAndSet(maxSize, currentSize);
    }

    private void trackExpiration()
    {
        _stats.expirations.incrementAndGet();
    }

    private void trackRemove()
    {
        _stats.removes.incrementAndGet();
    }

    private int trackRemoves(int removes)
    {
        _stats.removes.addAndGet(removes);
        return removes;
    }

    private void trackClear()
    {
        _stats.clears.incrementAndGet();
    }
}
