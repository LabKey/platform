/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
 * A read-through, transaction-specific cache. Reads through to the shared cache for each entry until a write operation
 * (put or remove) occurs on that entry, at which point only the private cache is consulted for this entry for the
 * remainder of the transaction. This should provide good performance while avoiding pollution of the shared cache
 * during the transaction and in the case of a rollback.
 * User: adam
 * Date: Nov 9, 2009
 */
public class TransactionCache<K, V> implements Cache<K, V>
{
    /** Need our own markers so we can distinguish missing vs. cached miss and missing vs. removed */
    @SuppressWarnings("unchecked")
    private final V NULL_MARKER = (V)new Object();
    @SuppressWarnings("unchecked")
    private final V REMOVED_MARKER = (V)new Object();

    /** Cache shared by other threads */
    private final Cache<K, V> _sharedCache;

    /** Our own private, transaction-specific cache, which may contain database changes that have not yet been committed */
    private final Cache<K, V> _privateCache;

    /** Whether the cache has been cleared. Once clear() is invoked, the shared cache is ignored. */
    private boolean _hasBeenCleared = false;

    public TransactionCache(Cache<K, V> sharedCache, Cache<K, V> privateCache)
    {
        _privateCache = privateCache;
        _sharedCache = sharedCache;
    }

    @Override
    public V get(@NotNull K key)
    {
        return get(key, null, null);
    }

    @Override
    public V get(@NotNull K key, Object arg, @Nullable CacheLoader<K, V> loader)
    {
        V v = _privateCache.get(key);

        if (v == REMOVED_MARKER)
        {
            v = null; // Entry has been removed from private cache, so treat as missing
        }
        else if (null == v && !_hasBeenCleared)
        {
            v = _sharedCache.get(key); // Entry has never been modified, read-through to shared cache
        }

        // If removed/cleared from private cache or missing from both caches, attempt to load and put into private cache
        if (null == v && null != loader)
        {
            v = loader.load(key, arg);
            put(key, v);
        }

        return v == NULL_MARKER ? null : v;
    }

    @Override
    public void put(@NotNull K key, V value)
    {
        if (null == value)
            value = NULL_MARKER;
        _privateCache.put(key, value);
    }

    @Override
    public void put(@NotNull K key, V value, long timeToLive)
    {
        if (null == value)
            value = NULL_MARKER;
        _privateCache.put(key, value, timeToLive);
    }

    @Override
    public void remove(@NotNull K key)
    {
        _privateCache.put(key, REMOVED_MARKER);
    }

    @Override
    public int removeUsingFilter(Filter<K> filter)
    {
        return (int)(
            _privateCache.getKeys().stream().filter(filter::accept).peek(this::remove).count() +
            _sharedCache.getKeys().stream().filter(filter::accept).peek(this::remove).count()
        );
    }

    @Override
    public Set<K> getKeys()
    {
        throw new UnsupportedOperationException("getKeys() is not implemented");
    }

    @Override
    public void clear()
    {
        _hasBeenCleared = true;
        _privateCache.clear();
    }

    @Override
    public void close()
    {
        _privateCache.close();
    }

    @Override
    public TrackingCache<K, V> getTrackingCache()
    {
        return _privateCache.getTrackingCache();
    }

    @Override
    public Cache<K, V> createTemporaryCache()
    {
        throw new UnsupportedOperationException();
    }
}
