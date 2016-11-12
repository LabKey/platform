/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.cache.StringKeyCacheWrapper;
import org.labkey.api.cache.Tracking;
import org.labkey.api.cache.TrackingCache;
import org.labkey.api.cache.TransactionCache;
import org.labkey.api.util.Filter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * User: matthewb
 * Date: Dec 12, 2006
 * Time: 9:54:06 AM
 *
 * Implements a thread-safe, transaction-aware cache by deferring to a TransactionCache when transactions are in progress.
 *
 * No synchronization is necessary in this class since the underlying caches are thread-safe, and the transaction cache
 * creation is single-threaded since the Transaction is thread local.
 *
 * @see org.labkey.api.data.DbScope
 */
public class DatabaseCache<ValueType> implements StringKeyCache<ValueType>
{
    protected final StringKeyCache<ValueType> _sharedCache;
    private final DbScope _scope;

    public DatabaseCache(DbScope scope, int maxSize, long defaultTimeToLive, String debugName)
    {
        _sharedCache = createSharedCache(maxSize, defaultTimeToLive, debugName);
        _scope = scope;
    }

    public DatabaseCache(DbScope scope, int maxSize, String debugName)
    {
        // TODO: UNLIMITED default TTL seems aggressive, but that's what we've used for years...
        this(scope, maxSize, CacheManager.UNLIMITED, debugName);
    }

    protected StringKeyCache<ValueType> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
    {
        return CacheManager.getStringKeyCache(maxSize, defaultTimeToLive, debugName);
    }

    protected StringKeyCache<ValueType> createTemporaryCache(StringKeyCache<ValueType> sharedCache)
    {
        Tracking tracking = sharedCache.getTrackingCache();
        return CacheManager.getTemporaryCache(tracking.getLimit(), tracking.getDefaultExpires(), "transaction cache: " + tracking.getDebugName(), tracking.getStats());
    }

    private StringKeyCache<ValueType> getCache()
    {
        DbScope.TransactionImpl t = _scope.getCurrentTransactionImpl();

        if (null != t)
        {
            StringKeyCache<ValueType> transactionCache = t.getCache(this);

            if (null == transactionCache)
            {
                transactionCache = new StringKeyCacheWrapper<>(new TransactionCache<>(_sharedCache, createTemporaryCache(_sharedCache)));
                t.addCache(this, transactionCache);
            }

            return transactionCache;
        }
        else
        {
            return _sharedCache;
        }
    }

    public void put(String key, ValueType value)
    {
        getCache().put(key, value);
    }

    public void put(String key, ValueType value, long timeToLive)
    {
        getCache().put(key, value, timeToLive);
    }

    public ValueType get(String key)
    {
        return getCache().get(key);
    }

    @Override
    public ValueType get(String key, @Nullable Object arg, CacheLoader<String, ValueType> loader)
    {
        return getCache().get(key, arg, loader);
    }

    public void remove(final String key)
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new CacheKeyRemovalCommitTask(key), DbScope.CommitTaskOption.POSTCOMMIT);
        }

        getCache().remove(key);
    }

    private class CacheKeyRemovalCommitTask extends AbstractCacheRemovalCommitTask
    {
        public CacheKeyRemovalCommitTask(String key)
        {
            super(key);
        }

        @Override
        public void run()
        {
            getCache().remove(_key);
        }
    }

    private abstract class AbstractCacheRemovalCommitTask implements Runnable
    {
        protected final String _key;

        public AbstractCacheRemovalCommitTask(String key)
        {
            _key = key;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AbstractCacheRemovalCommitTask that = (AbstractCacheRemovalCommitTask) o;

            if (!getCache().equals(that.getCache())) return false;
            return !(_key != null ? !_key.equals(that._key) : that._key != null);
        }

        private StringKeyCache<ValueType> getCache()
        {
            return DatabaseCache.this.getCache();
        }

        @Override
        public int hashCode()
        {
            int result = getCache().hashCode();
            result = 31 * result + (_key != null ? _key.hashCode() : 0);
            return result;
        }
    }



    public int removeUsingPrefix(final String prefix)
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new CachePrefixRemovalCommitTask(prefix), DbScope.CommitTaskOption.POSTCOMMIT);
        }

        return getCache().removeUsingPrefix(prefix);
    }


    public void clear()
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new CacheClearingCommitTask(), DbScope.CommitTaskOption.POSTCOMMIT);
        }

        getCache().clear();
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

        private StringKeyCache<ValueType> getCache()
        {
            return DatabaseCache.this.getCache();
        }

        @Override
        public int hashCode()
        {
            return getCache().hashCode();
        }

        public void run()
        {
            getCache().clear();
        }
    }


    @Override     // TODO: Is this right?
    public int removeUsingFilter(Filter<String> filter)
    {
        return _sharedCache.removeUsingFilter(filter);
    }

    @Override
    public Set<String> getKeys()
    {
        return _sharedCache.getKeys();
    }

    @Override
    public void close()
    {
        _sharedCache.close();
    }

    @Override
    public TrackingCache getTrackingCache()
    {
        return _sharedCache.getTrackingCache();
    }

    public static class TestCase extends Assert
    {
        @SuppressWarnings({"StringEquality"})
        @Test
        public void testDbCache() throws Exception
        {
            MyScope scope = new MyScope();

            // Shared cache needs to be a temporary cache, otherwise we'll leak a cache on every invocation because of KNOWN_CACHES
            DatabaseCache<String> cache = new DatabaseCache<String>(scope, 10, "Test Cache") {
                @Override
                protected StringKeyCache<String> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
                {
                    return CacheManager.getTemporaryCache(maxSize, defaultTimeToLive, debugName, null);
                }
            };

            // basic cache testing

            // Hold values so we can test equality below
            String[] values = new String[40];

            for (int i = 0; i < values.length; i++)
                values[i] = "value_" + i;

            TrackingCache trackingCache = cache.getCache().getTrackingCache();

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
                case DeterministicLRU:
                    assertEquals("Count was " + correctCount, correctCount, 20);
                    break;
                case NonDeterministicLRU:
                    assertTrue("Count was " + correctCount, correctCount > 11);
                    break;
                default:
                    fail("Unknown cache type");
            }

            // add 5 more (if deterministic, should kick out 16-20 which are now LRU)
            for (int i = 21; i <= 25; i++)
                cache.put("key_" + i, values[i]);

            assertTrue(trackingCache.size() == 10);
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
                case DeterministicLRU:
                    assertEquals("Count was " + correctCount, correctCount, 10);
                    break;
                case NonDeterministicLRU:
                    assertTrue("Count was " + correctCount, correctCount > 4);

                    // Make sure key_11 is in the cache
                    cache.put("key_11", values[11]);
                    assertTrue(cache.get("key_11") == values[11]);
                    break;
                default:
                    fail("Unknown cache type");
            }

            // transaction testing
            try(DbScope.Transaction transaction = scope.beginTransaction())
            {
                assertTrue(scope.isTransactionActive());

                // Test read-through transaction cache
                assertTrue(cache.get("key_11") == values[11]);

                cache.remove("key_11");
                assertTrue(null == cache.get("key_11"));

                // imitate another thread: toggle transaction and test
                scope.setOverrideTransactionActive(Boolean.FALSE);
                assertTrue(cache.get("key_11") == values[11]);
                scope.setOverrideTransactionActive(null);

                // This should close the transaction caches
                transaction.commit();
                // Test that remove got applied to shared cache
                assertTrue(null == cache.get("key_11"));

                cache.removeUsingPrefix("key");
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
            protected ConnectionWrapper _getConnection(Logger log) throws SQLException
            {
                return new ConnectionWrapper(null, null, null, log)
                {
                    public void setAutoCommit(boolean autoCommit) throws SQLException
                    {
                    }

                    public void commit() throws SQLException
                    {
                    }

                    public void rollback() throws SQLException
                    {
                    }
                };
            }

            @Override
            public void releaseConnection(Connection conn)
            {
            }


            public boolean isTransactionActive()
            {
                if (null != overrideTransactionActive)
                    return overrideTransactionActive;
                return super.isTransactionActive();
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

    private class CachePrefixRemovalCommitTask extends AbstractCacheRemovalCommitTask
    {
        public CachePrefixRemovalCommitTask(String prefix)
        {
            super(prefix);
        }

        @Override
        public void run()
        {
            getCache().removeUsingPrefix(_key);
        }
    }
}
