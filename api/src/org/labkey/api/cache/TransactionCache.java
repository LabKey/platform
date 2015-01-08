/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

import org.labkey.api.util.Filter;

/**
 * A read-through, transaction-specific cache.  Reads through to the shared cache until any write occurs, at which point it
 * switches to using a private cache for the remainder of the transaction.
 * User: adam
 * Date: Nov 9, 2009
 */
public class TransactionCache<K, V> implements Cache<K, V>
{
    private final Cache<K, V> _sharedCache;
    private final Cache<K, V> _privateCache;

    private boolean _hasWritten = false;

    public TransactionCache(Cache<K, V> sharedCache, Cache<K, V> privateCache)
    {
        _privateCache = privateCache;
        _sharedCache = sharedCache;
    }

    @Override
    public V get(K key)
    {
        V v;

        if (_hasWritten)
            v = _privateCache.get(key);
        else
            v = _sharedCache.get(key);

        return v;
    }


    @Override
    public V get(K key, Object arg, CacheLoader<K, V> loader)
    {
        V v;

        if (_hasWritten)
            v = _privateCache.get(key);
        else
            v = _sharedCache.get(key);

        if (null == v)
        {
            v = loader.load(key, arg);
            CacheManager.validate(loader, v);
            _hasWritten = true;
            _privateCache.put(key, v);
        }

        return v;
    }

    @Override
    public void put(K key, V value)
    {
        _hasWritten = true;
        _privateCache.put(key, value);
    }

    @Override
    public void put(K key, V value, long timeToLive)
    {
        _hasWritten = true;
        _privateCache.put(key, value, timeToLive);
    }

    @Override
    public void remove(K key)
    {
        _hasWritten = true;
        _privateCache.remove(key);
    }

    @Override
    public int removeUsingFilter(Filter<K> filter)
    {
        _hasWritten = true;
        return _privateCache.removeUsingFilter(filter);
    }

    @Override
    public void clear()
    {
        _hasWritten = true;
        _privateCache.clear();
    }

    @Override
    public void close()
    {
        _privateCache.close();
    }

    @Override
    public TrackingCache getTrackingCache()
    {
        return _privateCache.getTrackingCache();
    }
}
