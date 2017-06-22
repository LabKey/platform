/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import java.util.Set;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:50:02 AM
 */

// Convenience class that wraps a Cache<String, V> to provide a StringKeyCache<V>.
public class StringKeyCacheWrapper<V> implements StringKeyCache<V>
{
    private final Cache<String, V> _cache;

    public StringKeyCacheWrapper(@NotNull Cache<String, V> cache)
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
    public Set<String> getKeys()
    {
        return _cache.getKeys();
    }

    @Override
    public void clear()
    {
        _cache.clear();
    }

    @Override
    public void close()
    {
        _cache.close();
    }

    @Override
    public TrackingCache getTrackingCache()
    {
        return _cache.getTrackingCache();
    }
}
