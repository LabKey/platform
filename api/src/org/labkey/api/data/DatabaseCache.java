/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.TrackingCache;
import org.labkey.api.cache.TransactionCache;
import org.labkey.api.cache.Wrapper;
import org.labkey.api.data.DbScope.TransactionImpl;
import org.labkey.api.util.Filter;
import org.labkey.api.util.logging.LogHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;

/**
 * Implements a thread-safe, transaction-aware cache by deferring to a TransactionCache when transactions are in progress.
 * No synchronization is necessary in this class since the underlying caches are thread-safe, and the transaction cache
 * creation is single-threaded since the Transaction is thread local.
 */
public class DatabaseCache<K, V> implements Cache<K, V>
{
    private final Cache<K, V> _sharedCache;
    private final DbScope _scope;

    @Deprecated // Use the factory methods that return a BlockingDatabaseCache instead
    public DatabaseCache(DbScope scope, int maxSize, long defaultTimeToLive, String debugName)
    {
        _sharedCache = createSharedCache(maxSize, defaultTimeToLive, debugName);
        _scope = scope;
    }

    @Deprecated // Use the factory methods that return a BlockingDatabaseCache instead
    public DatabaseCache(DbScope scope, int maxSize, String debugName)
    {
        // TODO: UNLIMITED default TTL seems aggressive, but that's what we've used for years...
        this(scope, maxSize, CacheManager.UNLIMITED, debugName);
    }

    public static <K, V> BlockingCache<K, V> get(DbScope scope, int maxSize, long defaultTimeToLive, String debugName, @Nullable CacheLoader<K, V> cacheLoader)
    {
        return new BlockingDatabaseCache<>(new DatabaseCache<>(scope, maxSize, defaultTimeToLive, debugName), cacheLoader);
    }

    public static <K, V> BlockingCache<K, V> get(DbScope scope, int maxSize, String debugName, @Nullable CacheLoader<K, V> cacheLoader)
    {
        return new BlockingDatabaseCache<>(new DatabaseCache<>(scope, maxSize, debugName), cacheLoader);
    }

    /**
     * A DatabaseCache wrapped by a BlockingCache that knows how to replay load() events that take place on a
     * transaction's private cache into the shared cache after successful commit. This can result in a big performance
     * improvement, particularly for operations that always take place inside a transaction.
     */
    private static class BlockingDatabaseCache<K, V> extends BlockingCache<K, V>
    {
        private static final Logger LOG = LogHelper.getLogger(BlockingDatabaseCache.class, "BlockingDatabaseCache loads");

        private final DatabaseCache<K, Wrapper<V>> _databaseCache;

        public BlockingDatabaseCache(DatabaseCache<K, Wrapper<V>> cache, @Nullable CacheLoader<K, V> loader)
        {
            super(cache, loader);
            _databaseCache = cache;
        }

        @Override
        protected V load(@NotNull K key, @Nullable Object argument, CacheLoader<K, V> loader)
        {
            V value = super.load(key, argument, loader);
            LOG.debug("Just loaded: " + key + " (" + getTrackingCache().getDebugName() + ")");
            TransactionImpl t = _databaseCache.getCurrentTransaction();

            if (null != t)
            {
                t.addCommitTask(new CacheReloadCommitTask<>(this, key, argument, loader), DbScope.CommitTaskOption.POSTCOMMIT);
            }

            return value;
        }

        @Override
        public String toString()
        {
            return "BlockingDatabaseCache over \"" + _databaseCache._sharedCache.toString() + "\"";
        }
    }

    protected Cache<K, V> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
    {
        return CacheManager.getCache(maxSize, defaultTimeToLive, debugName);
    }

    @Override
    public final Cache<K, V> createTemporaryCache()
    {
        return createTemporaryCache(getTrackingCache());
    }

    private Cache<K, V> createTemporaryCache(TrackingCache<K, V> trackingCache)
    {
        return CacheManager.getTemporaryCache(trackingCache.getLimit(), trackingCache.getDefaultExpires(), "transaction cache: " + trackingCache.getDebugName(), trackingCache.getTransactionStats());
    }

    protected @Nullable TransactionImpl getCurrentTransaction()
    {
        return _scope.getCurrentTransactionImpl();
    }

    public Cache<K, V> getCache()
    {
        TransactionImpl t = getCurrentTransaction();

        if (null != t)
        {
            Cache<K, V> transactionCache = t.getCache(this);

            if (null == transactionCache)
            {
                transactionCache = new TransactionCache<>(_sharedCache, createTemporaryCache(_sharedCache.getTrackingCache()), this, t);
                t.addCache(this, transactionCache);
            }

            return transactionCache;
        }
        else
        {
            return _sharedCache;
        }
    }

    @Override
    public void put(@NotNull K key, V value)
    {
        getCache().put(key, value);
    }

    @Override
    public void put(@NotNull K key, V value, long timeToLive)
    {
        getCache().put(key, value, timeToLive);
    }

    @Override
    public V get(@NotNull K key)
    {
        return getCache().get(key);
    }

    @Override
    public V get(@NotNull K key, @Nullable Object arg, CacheLoader<K, V> loader)
    {
        return getCache().get(key, arg, loader);
    }

    @Override
    public void remove(@NotNull final K key)
    {
        getCache().remove(key);
    }

    @Override
    public void clear()
    {
        getCache().clear();
    }

    @Override
    public int removeUsingFilter(Filter<K> filter)
    {
        return getCache().removeUsingFilter(filter);
    }

    @Override
    public Set<K> getKeys()
    {
        return _sharedCache.getKeys();
    }

    @Override
    public void close()
    {
        _sharedCache.close();
    }

    @Override
    public TrackingCache<K, V> getTrackingCache()
    {
        return _sharedCache.getTrackingCache();
    }

    @Override
    public String toString()
    {
        return "DatabaseCache over \"" + _sharedCache.toString() + "\"";
    }

    // This is added as a commit task when load operations take place inside a transaction, ensuring that they are
    // re-played on successful commit.
    public static class CacheReloadCommitTask<K, V> implements Runnable
    {
        private final Cache<K, V> _cache;
        private final K _key;
        private final Object _arg;
        private final CacheLoader<K, V> _loader;

        public CacheReloadCommitTask(Cache<K, V> cache, K key, Object arg, CacheLoader<K, V> loader)
        {
            _cache = cache;
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
            return _cache;
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
            getCache().get(_key, _arg, _loader);
        }
    }

    public static class TestCase extends Assert
    {
        @SuppressWarnings({"StringEquality"})
        @Test
        public void testDatabaseCache()
        {
            MyScope scope = new MyScope();

            // Shared cache needs to be a temporary cache, otherwise we'll leak a cache on every invocation because of KNOWN_CACHES
            DatabaseCache<String, String> cache = new DatabaseCache<>(scope, 10, "Test Cache")
            {
                @Override
                protected Cache<String, String> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
                {
                    return CacheManager.getTemporaryCache(maxSize, defaultTimeToLive, debugName, null);
                }
            };

            // basic cache testing

            // Hold values so we can test equality below
            String[] values = new String[40];

            for (int i = 0; i < values.length; i++)
                values[i] = "value_" + i;

            TrackingCache<String, String> trackingCache = cache.getCache().getTrackingCache();

            for (int i = 1; i <= 20; i++)
            {
                cache.put("key_" + i, values[i]);
                assertTrue(trackingCache.size() <= 10);
            }

            int correctCount = 0;

            // access in reverse order
            for (int i = 10; i >= 1; i--)
            {
                if (null == cache.get("key_" + i))
                    correctCount++;

                if (cache.get("key_" + (i + 10)) == values[i + 10])
                    correctCount++;
            }

            // A DeterministicLRU cache guarantees that the least recently used element is always kicked out when capacity
            // is reached. A NonDeterministicLRU cache (e.g., an Ehcache implementation) attempts to kick out the least
            // recently used element, but provides no guarantee since it uses sampling for performance reasons. This test
            // is not very useful for a NonDeterministicLRU cache. Adjust the check below if the test fails.
            switch (trackingCache.getCacheType())
            {
                case DeterministicLRU -> assertEquals("Count was " + correctCount, correctCount, 20);
                case NonDeterministicLRU -> assertTrue("Count was " + correctCount, correctCount > 11);
                default -> fail("Unknown cache type");
            }

            // add 5 more (if deterministic, should kick out 16-20 which are now LRU)
            for (int i = 21; i <= 25; i++)
                cache.put("key_" + i, values[i]);

            assertEquals(10, trackingCache.size());
            correctCount = 0;

            for (int i = 11; i <= 15; i++)
            {
                if (cache.get("key_" + i) == values[i])
                    correctCount++;

                if (cache.get("key_" + (i + 10)) == values[i + 10])
                    correctCount++;
            }

            // As above, this test isn't very useful for a NonDeterministicLRU cache.
            switch (trackingCache.getCacheType())
            {
                case DeterministicLRU -> assertEquals("Count was " + correctCount, correctCount, 10);
                case NonDeterministicLRU -> {
                    assertTrue("Count was " + correctCount, correctCount > 4);

                    // Make sure key_11 is in the cache
                    cache.put("key_11", values[11]);
                    assertSame(cache.get("key_11"), values[11]);
                }
                default -> fail("Unknown cache type");
            }

            // transaction testing
            try (DbScope.Transaction transaction = scope.beginTransaction())
            {
                assertTrue(scope.isTransactionActive());

                // Test read-through transaction cache
                assertSame(cache.get("key_11"), values[11]);

                cache.remove("key_11");
                assertNull(cache.get("key_11"));

                // imitate another thread: toggle transaction and test
                scope.setOverrideTransactionActive(Boolean.FALSE);
                assertSame(cache.get("key_11"), values[11]);
                scope.setOverrideTransactionActive(null);

                // This should close the transaction caches
                transaction.commit();
                // Test that remove got applied to shared cache
                assertNull(cache.get("key_11"));

                cache.removeUsingFilter(new Cache.StringPrefixFilter("key"));
                assert trackingCache.size() == 0;

                // This should close the (temporary) shared cache
                cache.close();
            }
        }

        private static class MyScope extends DbScope
        {
            private Boolean overrideTransactionActive = null;
            private TransactionImpl overrideTransaction = null;
            
            private MyScope()
            {
                super();
            }

            @Override
            public ConnectionWrapper getPooledConnection(ConnectionType type, Logger log)
            {
                return new ConnectionWrapper(null, null, null, type, log)
                {
                    @Override
                    public void setAutoCommit(boolean autoCommit)
                    {
                    }

                    @Override
                    public void commit()
                    {
                    }

                    @Override
                    public void rollback()
                    {
                    }

                    @Override  // MyScope's isTransactionActive() override doesn't play nice with ConnectionType validation... so just call internalClose directly
                    public void close() throws SQLException
                    {
                        internalClose();
                    }
                };
            }

            @Override
            public void releaseConnection(Connection conn)
            {
            }

            @Override
            public boolean isTransactionActive()
            {
                return Objects.requireNonNullElseGet(overrideTransactionActive, super::isTransactionActive);
            }

            @Override
            public @Nullable TransactionImpl getCurrentTransactionImpl()
            {
                if (null != overrideTransactionActive)
                {
                    return overrideTransaction;
                }

                return super.getCurrentTransactionImpl();
            }

            private void setOverrideTransactionActive(@Nullable Boolean override)
            {
                overrideTransactionActive = override;

                if (null == overrideTransactionActive || !overrideTransactionActive)
                {
                    overrideTransaction = null;
                }
                else
                {
                    overrideTransaction = new TransactionImpl(null, DbScope.NORMAL_TRANSACTION_KIND);
                }
            }
        }
    }
}
