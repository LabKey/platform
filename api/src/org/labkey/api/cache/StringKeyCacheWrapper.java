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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Filter;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:50:02 AM
 */

// Convenience class that wraps a Cache to provide a StringKeyCache.
class StringKeyCacheWrapper<V> implements StringKeyCache<V>, Tracking
{
    private final TrackingCache<String, V> _cache;

    StringKeyCacheWrapper(@NotNull TrackingCache<String, V> cache)
    {
        _cache = cache;
    }

    public int removeUsingPrefix(final String prefix)
    {
        return removeUsingFilter(new Filter<String>(){
            @Override
            public boolean accept(String s)
            {
                return s.startsWith(prefix);
            }
        });
    }

    public void put(String key, V value)
    {
        _cache.put(key, value);
    }

    public void put(String key, V value, long timeToLive)
    {
        _cache.put(key, value, timeToLive);
    }

    @Override
    public V get(String key)
    {
        return _cache.get(key);
    }

    public V get(String key, @Nullable Object arg, CacheLoader<String, V> stringVCacheLoader)
    {
        return _cache.get(key, arg, stringVCacheLoader);
    }

    @Override
    public void remove(String key)
    {
        _cache.remove(key);
    }

    @Override
    public int removeUsingFilter(Filter<String> stringFilter)
    {
        return _cache.removeUsingFilter(stringFilter);
    }

    @Override
    public void clear()
    {
        _cache.clear();
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
        return _cache.getDebugName();
    }

    @Override
    public StackTraceElement[] getCreationStackTrace()
    {
        return _cache.getCreationStackTrace();
    }

    @Override
    public Stats getStats()
    {
        return _cache.getStats();
    }

    @Override
    public Stats getTransactionStats()
    {
        return _cache.getTransactionStats();
    }
}
