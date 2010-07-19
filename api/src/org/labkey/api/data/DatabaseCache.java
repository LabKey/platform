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

package org.labkey.api.data;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.Stats;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.util.Filter;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: matthewb
 * Date: Dec 12, 2006
 * Time: 9:54:06 AM
 *
 * Not a map, uses a StringKeyCache to implement a transaction aware cache
 *
 * @see org.labkey.api.data.DbScope
 */
public class DatabaseCache<ValueType> implements StringKeyCache<ValueType>
{
    private final StringKeyCache<ValueType> _sharedCache;
    private final DbScope _scope;

    public DatabaseCache(DbScope scope, int maxSize, long defaultTimeToLive, String debugName)
    {
        _sharedCache = createSharedCache(maxSize, defaultTimeToLive, debugName);
        _scope = scope;
    }

    public DatabaseCache(DbScope scope, int maxSize, String debugName)
    {
        this(scope, maxSize, -1, debugName);
    }

    protected StringKeyCache<ValueType> createSharedCache(int maxSize, long defaultTimeToLive, String debugName)
    {
        return CacheManager.getStringKeyCache(maxSize, defaultTimeToLive, debugName);
    }

    private StringKeyCache<ValueType> getCache()
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            StringKeyCache<ValueType> map = t.getCache(this);

            if (null == map)
            {
                map = new TransactionCache<ValueType>(_sharedCache);
                t.addCache(this, map);
            }

            return map;
        }
        else
        {
            return _sharedCache;
        }
    }

    public synchronized ValueType put(String key, ValueType value)
    {
        return getCache().put(key, value);
    }

    public synchronized ValueType put(String key, ValueType value, long timeToLive)
    {
        return getCache().put(key, value, timeToLive);
    }

    public synchronized ValueType get(String key)
    {
        return getCache().get(key);
    }


    public synchronized ValueType remove(final String key)
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new Runnable() {
                public void run()
                {
                    DatabaseCache.this.remove(key);
                }
            });
        }

        return getCache().remove(key);
    }


    public synchronized int removeUsingPrefix(final String prefix)
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new Runnable() {
                 public void run()
                 {
                     DatabaseCache.this.removeUsingPrefix(prefix);
                 }
            });
        }

        return getCache().removeUsingPrefix(prefix);
    }


    public synchronized void clear()
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            t.addCommitTask(new Runnable() {
                public void run()
                {
                    DatabaseCache.this.clear();
                }
            });
        }

        getCache().clear();
    }


    @Override     // TODO: Is this right?
    public int removeUsingFilter(Filter<String> filter)
    {
        return _sharedCache.removeUsingFilter(filter);
    }

    @Override
    public int size()
    {
        return _sharedCache.size();
    }

    @Override
    public long getDefaultExpires()
    {
        return _sharedCache.getDefaultExpires();
    }

    @Override
    public String getDebugName()
    {
        return _sharedCache.getDebugName();
    }

    @Override
    public int getLimit()
    {
        return _sharedCache.getLimit();
    }

    @Override
    public CacheType getCacheType()
    {
        return _sharedCache.getCacheType();
    }

    @Override
    public void close()
    {
        _sharedCache.close();
    }

    @Override
    public Stats getStats()
    {
        return _sharedCache.getStats();
    }

    @Override
    public Stats getTransactionStats()
    {
        return _sharedCache.getTransactionStats();
    }

    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        @SuppressWarnings({"StringEquality"})
        public void testDbCache() throws Exception
        {
            MyScope scope = new MyScope();

            // Shared cache needs to be a temporary cache, otherwise we'll leak a cache on every invocation
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

            for (int i = 1; i <= 20; i++)
            {
                cache.put("key_" + i, values[i]);
                assertTrue(cache.getCache().size() <= 10);
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
            switch (cache.getCacheType())
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

            assertTrue(cache.getCache().size() == 10);
            correctCount = 0;

            for (int i = 11; i <= 15; i++)
            {
                if (cache.get("key_" + i) == values[i])
                    correctCount++;

                if (cache.get("key_" + (i + 10)) == values[i + 10])
                    correctCount++;
            }

            // As above, this test isn't very useful for a NonDeterministicLRU cache.
            switch (cache.getCacheType())
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
            scope.beginTransaction();
            assertTrue(scope.isTransactionActive());

            // Test read-through transaction cache
            assertTrue(cache.get("key_11") == values[11]);

            cache.remove("key_11");
            assertTrue(null == cache.get("key_11"));

            // imitate another thread: toggle transaction and test
            scope.setOverrideTransactionActive(Boolean.FALSE);
            assertTrue(cache.get("key_11") == values[11]);
            scope.setOverrideTransactionActive(null);

            scope.commitTransaction();
            // Test that remove got applied to shared cache
            assertTrue(null == cache.get("key_11"));

            cache.removeUsingPrefix("key");
            assert cache.getCache().size() == 0;

            scope.closeConnection();
        }


        protected void tearDown() throws Exception
        {
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }


        private static class MyScope extends DbScope
        {
            private Boolean overrideTransactionActive = null;
            private Transaction overrideTransaction = null;
            
            private MyScope()
            {
                super();
            }

            @Override
            protected Connection _getConnection(Logger log) throws SQLException
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
            public void releaseConnection(Connection conn) throws SQLException
            {
            }


            public boolean isTransactionActive()
            {
                if (null != overrideTransactionActive)
                    return overrideTransactionActive.booleanValue();
                return super.isTransactionActive();
            }

            @Override
            public Transaction getCurrentTransaction()
            {
                if (null != overrideTransactionActive)
                {
                    return overrideTransaction;
                }

                return super.getCurrentTransaction();
            }

            private void setOverrideTransactionActive(Boolean override)
            {
                overrideTransactionActive = override;

                if (null == overrideTransactionActive || !overrideTransactionActive.booleanValue())
                {
                    overrideTransaction = null;
                }
                else
                {
                    overrideTransaction = new Transaction(null);
                }
            }
        }
    }
}