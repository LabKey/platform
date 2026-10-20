/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.labkey.api.cache.Wrapper;
import org.labkey.api.data.DbScope.TransactionImpl;
import org.labkey.api.util.logging.LogHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements a thread-safe, transaction-aware cache by deferring to a TransactionCache when transactions are in progress.
 * No synchronization is necessary in this class since the underlying caches are thread-safe, and the transaction cache
 * creation is single-threaded since the Transaction is thread local.
 */
public class DatabaseCache<K, V> implements Cache<K, V>
{
    private final @NotNull Class<K> _keyClass;
    private final Cache<K, V> _sharedCache;
    private final DbScope _scope;

    // Use the factory methods that return a BlockingDatabaseCache instead
    private DatabaseCache(@NotNull Class<K> keyClass, DbScope scope, int maxSize, long defaultTimeToLive, String debugName)
    {
        _keyClass = keyClass;
        _sharedCache = createSharedCache(maxSize, defaultTimeToLive, debugName);
        _scope = scope;
    }

    /** Uses a cache implementation specialized for keyClass, if one is registered */
    public static <K, V> BlockingCache<K, V> get(@NotNull Class<K> keyClass, DbScope scope, int maxSize, long defaultTimeToLive, String debugName, @Nullable CacheLoader<K, V> cacheLoader)
    {
        return new BlockingDatabaseCache<>(new DatabaseCache<>(keyClass, scope, maxSize, defaultTimeToLive, debugName), cacheLoader);
    }

    public static <K, V> BlockingCache<K, V> get(@NotNull Class<K> keyClass, DbScope scope, int maxSize, String debugName, @Nullable CacheLoader<K, V> cacheLoader)
    {
        // TODO: UNLIMITED default TTL seems aggressive, but that's what we've used for years...
        return get(keyClass, scope, maxSize, CacheManager.UNLIMITED, debugName, cacheLoader);
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
            LOG.debug("Just loaded: {} ({})", key, getTrackingCache().getDebugName());
            TransactionImpl t = _databaseCache.getCurrentTransaction();

            if (null != t)
                CacheReplayCommitTask.get(t).reload(_databaseCache, key, getTrackingCache().getLimit(), () -> get(key, argument, loader));

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
        return CacheManager.getCache(_keyClass, maxSize, defaultTimeToLive, debugName);
    }

    @Override
    public final Cache<K, V> createTemporaryCache()
    {
        return createTemporaryCache(getTrackingCache());
    }

    private Cache<K, V> createTemporaryCache(TrackingCache<K, V> trackingCache)
    {
        return CacheManager.getTemporaryCache(_keyClass, trackingCache.getLimit(), trackingCache.getDefaultExpires(), "transaction cache: " + trackingCache.getDebugName(), trackingCache.getTransactionStats());
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
    public boolean remove(@NotNull final K key)
    {
        return getCache().remove(key);
    }

    @Override
    public void clear()
    {
        getCache().clear();
    }

    @Override
    public int removeUsingFilter(Predicate<K> filter)
    {
        return getCache().removeUsingFilter(filter);
    }

    @Override
    public Stream<K> getKeys()
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

    public static class TestCase extends Assert
    {
        public static class TempDatabaseCache<V> extends DatabaseCache<String, V>
        {
            public TempDatabaseCache(DbScope scope, int maxSize, String debugName)
            {
                super(String.class, scope, maxSize, CacheManager.UNLIMITED, debugName);
            }

            // Shared cache needs to be a temporary cache, otherwise we'll leak a cache on every invocation because of KNOWN_CACHES
            @Override
            protected Cache<String, V> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
            {
                return CacheManager.getTemporaryCache(String.class, maxSize, defaultTimeToLive, debugName, null);
            }
        }

        @SuppressWarnings({"StringEquality"})
        @Test
        public void testDatabaseCache()
        {
            MyScope scope = new MyScope();

            // basic cache testing

            // Hold values so we can test equality below
            String[] values = new String[40];

            for (int i = 0; i < values.length; i++)
                values[i] = "value_" + i;

            try (DatabaseCache<String, String> cache = new TempDatabaseCache<>(scope, 10, "Test Cache"))
            {
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
                    case DeterministicLRU -> assertEquals("Count was " + correctCount, 20, correctCount);
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
                    case DeterministicLRU -> assertEquals("Count was " + correctCount, 10, correctCount);
                    case NonDeterministicLRU ->
                    {
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
                    TransactionCache<String, String> tCache = (TransactionCache<String, String>) cache.getCache();
                    Cache<String, String> privateCache = tCache._privateCache;
                    Cache<String, String> sharedCache = tCache._sharedCache;
                    assertEquals(10, tCache.getKeys().count());
                    assertEquals(10, sharedCache.getKeys().count());
                    assertEquals(0, privateCache.getKeys().count());

                    // Test read-through transaction cache
                    assertSame(cache.get("key_11"), values[11]);
                    // Nothing should change after a read
                    assertEquals(10, sharedCache.getKeys().count());
                    assertEquals(10, tCache.getKeys().count());
                    assertEquals(0, privateCache.getKeys().count());

                    cache.remove("key_11");
                    assertNull(cache.get("key_11"));
                    assertEquals(10, sharedCache.getKeys().count());
                    assertEquals(10, tCache.getKeys().count()); // Unique keys are still 10
                    assertEquals(1, privateCache.getKeys().count()); // But private cache should now have a remove entry

                    for (int i = 30; i < 35; i++)
                    {
                        cache.put("key_" + i, values[i]);
                    }
                    assertEquals(10, sharedCache.getKeys().count());
                    assertEquals(15, tCache.getKeys().count()); // 15 unique now
                    assertEquals(6, privateCache.getKeys().count());

                    // Remove all the even keys
                    cache.removeUsingFilter(key -> Integer.valueOf(key.substring(4)) % 2 == 0);
                    assertNull(cache.get("key_12"));
                    assertNull(cache.get("key_22"));
                    assertNull(cache.get("key_24"));
                    assertNull(cache.get("key_30"));
                    assertNotNull(privateCache.get("key_30"));
                    assertNull(cache.get("key_34"));
                    assertNotNull(privateCache.get("key_34"));

                    // imitate another thread: toggle transaction and test
                    scope.setOverrideTransactionActive(Boolean.FALSE);
                    assertSame(cache.get("key_11"), values[11]);
                    assertNull(cache.get("key_31"));
                    assertNull(cache.get("key_33"));
                    scope.setOverrideTransactionActive(null);

                    // This should close the transaction caches
                    transaction.commit();

                    // Test that remove got applied to shared cache
                    assertNull(cache.get("key_11"));
                    // Test that even keys got removed from the shared cache
                    cache.getKeys()
                        .filter(key -> Integer.valueOf(key.substring(4)) % 2 == 0)
                        .findAny()
                        .ifPresent(key -> fail("Found an even key: " + key));
                    // No test for puts since DatabaseCache doesn't replay them (that's solely a BlockingDatabaseCache thing)

                    cache.removeUsingFilter(new Cache.StringPrefixFilter("key"));
                    assert trackingCache.size() == 0;
                }
            }
        }

        @Test
        public void testDatabaseCacheTransactionLimit()
        {
            final int maxSize = 10;
            MyScope scope = new MyScope();

            DatabaseCache<String, Wrapper<Integer>> dbCache = new TempDatabaseCache<>(scope, maxSize, "Test Cache");
            TestCacheLoader loader = new TestCacheLoader();

            try (BlockingDatabaseCache<String, Integer> cache = new BlockingDatabaseCache<>(dbCache, loader); DbScope.Transaction transaction = scope.beginTransaction())
            {
                for (int i = 1; i <= 15; i++)
                {
                    cache.get("key_" + i);

                    // Try another cache miss for this key to ensure another reload is not added
                    if (i == 5)
                        cache.get("key_" + i);

                    // One replay task holds every reload, capped at the cache size
                    assertEquals(1, DbScope.CommitTaskOption.POSTCOMMIT.getQueue(scope.getCurrentTransactionImpl()).size());
                    assertEquals(Math.min(i, maxSize), CacheReplayCommitTask.get(transaction).size());
                }
                transaction.commit();

                // The capped reloads ran after commit and populated the shared cache
                assertEquals(15 + maxSize, loader._loads);
                assertEquals(Set.of("key_1", "key_2", "key_3", "key_4", "key_5", "key_6", "key_7", "key_8", "key_9", "key_10"), sharedKeys(dbCache));
            }
        }

        @Test
        public void testReplayPrunesReloadsUndoneInTheTransaction()
        {
            MyScope scope = new MyScope();
            DatabaseCache<String, Wrapper<Integer>> dbCache = new TempDatabaseCache<>(scope, 10, "Test Cache");
            TestCacheLoader loader = new TestCacheLoader();

            try (BlockingDatabaseCache<String, Integer> cache = new BlockingDatabaseCache<>(dbCache, loader))
            {
                cache.get("x");

                try (DbScope.Transaction transaction = scope.beginTransaction())
                {
                    cache.get("a");
                    cache.get("b");
                    cache.remove("a");
                    assertEquals(2, CacheReplayCommitTask.get(transaction).size()); // Reload b, remove a

                    cache.clear();
                    cache.get("c");
                    assertEquals(2, CacheReplayCommitTask.get(transaction).size()); // Clear, reload c
                    assertEquals(Set.of("x"), sharedKeys(dbCache));

                    transaction.commit();
                }

                // The clear evicted x and b, and only c's reload ran
                assertEquals(Set.of("c"), sharedKeys(dbCache));
                assertEquals(5, loader._loads);
            }
        }

        @Test
        public void testRollbackLeavesSharedCacheUntouched()
        {
            MyScope scope = new MyScope();
            DatabaseCache<String, Wrapper<Integer>> dbCache = new TempDatabaseCache<>(scope, 10, "Test Cache");
            TestCacheLoader loader = new TestCacheLoader();

            try (BlockingDatabaseCache<String, Integer> cache = new BlockingDatabaseCache<>(dbCache, loader))
            {
                cache.get("x");
                cache.get("y");

                try (DbScope.Transaction ignored = scope.beginTransaction())
                {
                    cache.get("a");
                    cache.remove("x");
                    cache.removeUsingFilter(key -> key.equals("y"));
                    cache.clear();
                    cache.get("b");
                }

                assertEquals(Set.of("x", "y"), sharedKeys(dbCache));
                assertEquals(4, loader._loads);
            }
        }

        private static Set<String> sharedKeys(DatabaseCache<String, ?> dbCache)
        {
            return dbCache._sharedCache.getKeys().collect(Collectors.toSet());
        }

        private static class TestCacheLoader implements CacheLoader<String, Integer>
        {
            private int _loads = 0;

            @Override
            public Integer load(@NotNull String test, @Nullable Object argument)
            {
                assertNotNull(test);
                _loads++;
                return 0;
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
