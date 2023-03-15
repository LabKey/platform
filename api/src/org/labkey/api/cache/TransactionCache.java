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
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.util.Filter;

import java.util.Objects;
import java.util.Set;

/**
 * A read-through, transaction-specific cache. Reads through to the shared cache for each entry until a write operation
 * (put or remove) occurs on that entry, at which point only the private cache is consulted for this entry for the
 * remainder of the transaction. This should provide good performance while avoiding pollution of the shared cache
 * during the transaction and in the case of a rollback.
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
    private final DatabaseCache<K, V> _databaseCache;
    private final Transaction _transaction;

    /** Our own private, transaction-specific cache, which may contain database changes that have not yet been committed */
    private final Cache<K, V> _privateCache;

    /** Whether the cache has been cleared. Once clear() is invoked, the shared cache is ignored. */
    private boolean _hasBeenCleared = false;

    public TransactionCache(Cache<K, V> sharedCache, Cache<K, V> privateCache, DatabaseCache<K, V> databaseCache, Transaction transaction)
    {
        _privateCache = privateCache;
        _sharedCache = sharedCache;
        _databaseCache = databaseCache;
        _transaction = transaction;
    }

    @Override
    public V get(@NotNull K key)
    {
        return get(key, null, null);
    }

    @Override
    public V get(@NotNull K key, Object arg, @Nullable CacheLoader<K, V> loader)
    {
        // No locks or synchronization below because this code is always single-threaded (unlike BlockingCache)

        V v = _privateCache.get(key);

        if (v == REMOVED_MARKER)
        {
            v = null; // Entry has been removed from private cache, so treat as missing
        }
        else if (null == v && !_hasBeenCleared)
        {
            try
            {
                // Entry has not been modified in the private cache, so read through to shared cache
                v = _sharedCache.get(key, null, (key1, argument) -> {
                    throw new MissingCacheEntryException();
                });
                // Shared cache has an entry for this key, so return it and don't invoke the loader
                if (null == v)
                    v = NULL_MARKER; // Cached miss in shared cache; use null marker to skip loading. Issue 47234
            }
            catch (MissingCacheEntryException e)
            {
                // Missing from private & shared cache; fall through to private cache load/put
            }
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
        _transaction.addCommitTask(new CacheKeyRemovalCommitTask(key), DbScope.CommitTaskOption.POSTCOMMIT);
        _privateCache.put(key, REMOVED_MARKER);
    }

    @Override
    public int removeUsingFilter(Filter<K> filter)
    {
        _transaction.addCommitTask(new CachePrefixRemovalCommitTask(filter), DbScope.CommitTaskOption.POSTCOMMIT);
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
        _transaction.addCommitTask(new CacheClearingCommitTask(), DbScope.CommitTaskOption.POSTCOMMIT);
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

    private class CacheClearingCommitTask implements Runnable
    {
        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheClearingCommitTask that = (CacheClearingCommitTask) o;

            return getCache().equals(that.getCache());
        }

        private Cache<K, V> getCache()
        {
            return _databaseCache.getCache();
        }

        @Override
        public int hashCode()
        {
            return getCache().hashCode();
        }

        @Override
        public void run()
        {
            getCache().clear();
        }
    }

    private abstract class AbstractCacheRemovalCommitTask<ObjectType> implements Runnable
    {
        protected final ObjectType object;

        public AbstractCacheRemovalCommitTask(ObjectType object)
        {
            this.object = object;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AbstractCacheRemovalCommitTask<ObjectType> that = (AbstractCacheRemovalCommitTask<ObjectType>) o;

            if (!getCache().equals(that.getCache())) return false;
            return Objects.equals(object, that.object);
        }

        protected Cache<K, V> getCache()
        {
            return _databaseCache.getCache();
        }

        @Override
        public int hashCode()
        {
            int result = getCache().hashCode();
            result = 31 * result + (object != null ? object.hashCode() : 0);
            return result;
        }
    }

    private class CacheKeyRemovalCommitTask extends AbstractCacheRemovalCommitTask<K>
    {
        public CacheKeyRemovalCommitTask(K key)
        {
            super(key);
        }

        @Override
        public void run()
        {
            getCache().remove(object);
        }
    }

    private class CachePrefixRemovalCommitTask extends AbstractCacheRemovalCommitTask<Filter<K>>
    {
        public CachePrefixRemovalCommitTask(Filter<K> filter)
        {
            super(filter);
        }

        @Override
        public void run()
        {
            getCache().removeUsingFilter(object);
        }
    }

    // Not currently used... consider adding this on every put to proactively populate shared cache after commit
    private class CacheReloadCommitTask implements Runnable
    {
        private final K _key;
        private final Object _arg;
        private final CacheLoader<K, V> _loader;

        public CacheReloadCommitTask(K key, Object arg, CacheLoader<K, V> loader)
        {
            _key = key;
            _arg = arg;
            _loader = loader;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheReloadCommitTask that = (CacheReloadCommitTask) o;

            if (!getCache().equals(that.getCache())) return false;
            return Objects.equals(_key, that._key);
        }

        protected Cache<K, V> getCache()
        {
            return _databaseCache.getCache();
        }

        @Override
        public int hashCode()
        {
            int result = getCache().hashCode();
            result = 31 * result + (_key != null ? _key.hashCode() : 0);
            return result;
        }

        @Override
        public void run()
        {
            V value = _loader.load(_key, _arg);
            getCache().put(_key, value);
        }
    }

    private static class MissingCacheEntryException extends RuntimeException
    {
    }
}
