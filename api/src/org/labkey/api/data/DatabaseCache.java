/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.collections.TTLCacheMap;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * User: matthewb
 * Date: Dec 12, 2006
 * Time: 9:54:06 AM
 *
 * Not a map, uses TTLCacheMap to implement a transaction aware cache
 *
 * @see DbScope
 */
public class DatabaseCache<ValueType>
{
    // for convenience of subclasses
    protected final static long HOUR = TTLCacheMap.HOUR;
    protected final static long MINUTE = TTLCacheMap.MINUTE;
    protected final static long SECOND = TTLCacheMap.SECOND;

    private final TTLCacheMap<String, ValueType> _sharedMap;
    private final DbScope _scope;

    public DatabaseCache(DbScope scope, int maxSize, long defaultTimeToLive, String debugName)
    {
        _sharedMap = createSharedCacheMap(maxSize, defaultTimeToLive, debugName);
        _scope = scope;
    }

    public DatabaseCache(DbScope scope, int maxSize, String debugName)
    {
        this(scope, maxSize, -1, debugName);
    }

    protected TTLCacheMap<String, ValueType> createSharedCacheMap(int maxSize, long defaultTimeToLive, String debugName)
    {
        return new TTLCacheMap<String, ValueType>(maxSize, defaultTimeToLive, debugName);
    }

    private TTLCacheMap<String, ValueType> getMap()
    {
        DbScope.Transaction t = _scope.getCurrentTransaction();

        if (null != t)
        {
            TTLCacheMap<String, ValueType> map = t.getCache(this);

            if (null == map)
            {
                map = new TransactionCacheMap<String, ValueType>(_sharedMap);
                t.addCache(this, map);
            }

            return map;
        }
        else
        {
            return _sharedMap;
        }
    }

    public synchronized ValueType put(String key, ValueType value)
    {
        return getMap().put(key, value);
    }

    public synchronized ValueType put(String key, ValueType value, long timeToLive)
    {
        return getMap().put(key, value, timeToLive);
    }

    public synchronized ValueType get(String key)
    {
        return getMap().get(key);
    }


    public synchronized void remove(final String key)
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

        getMap().remove(key);
    }


    public synchronized void removeUsingPrefix(final String prefix)
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

        getMap().removeUsingPrefix(prefix);
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

        getMap().clear();
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


        public void testDbCache() throws Exception
        {
            MyScope scope = new MyScope();

            // Don't let the test cache add to KNOWN_CACHES, otherwise we'll leak a TTLCacheMap for each invocation
            DatabaseCache<String> cache = new DatabaseCache<String>(scope, 10, "Test Cache") {
                @Override
                protected TTLCacheMap<String, String> createSharedCacheMap(int maxSize, long defaultTimeToLive, String debugName)
                {
                    return new TTLCacheMap<String, String>(maxSize, defaultTimeToLive, debugName) {
                        @Override
                        protected void addToKnownCacheMaps()
                        {
                        }
                    };
                }
            };

            // basic TTL testing

            // currently the TTLMapCache uses SoftReferences to values, so hold values here
            String[] values = new String[40];
            for (int i=0 ; i<values.length ; i++)
                values[i] = "value_" + i;

            for (int i=1 ; i<=20 ; i++)
            {
                cache.put("key_" + i, values[i]);
                assertTrue(cache.getMap().size() <= 10);
            }
            // access in reverse order
            for (int i=10 ; i>=1 ; i--)
            {
                assertTrue(null == cache.get("key_" + i));
                assertTrue(cache.get("key_" + (i+10)) == values[i+10]);
            }
            // add 5 more (should kick out 16-20 which are now LRU)
            for (int i=21 ; i<=25 ; i++)
                cache.put("key_" + i, values[i]);

            assertTrue(cache.getMap().size() == 10);
            for (int i=11 ; i<=15 ; i++)
            {
                assertTrue(cache.get("key_" + i) == values[i]);
                assertTrue(cache.get("key_" + (i + 10)) == values[i+10]);
            }

            // transaction testing
            scope.beginTransaction();
            assertTrue(scope.isTransactionActive());

            if (TransactionCacheMap.ENABLE_READ_THROUGH)
            {
                // Test read-through transaction cache
                assertTrue(cache.get("key_11") == values[11]);
            }

            cache.remove("key_" + 11);
            assertTrue(null == cache.get("key_11"));

            // imitate another thread: toggle transaction and test
            scope.setOverrideTransactionActive(Boolean.FALSE);
            assertTrue(cache.get("key_11") == values[11]);
            scope.setOverrideTransactionActive(null);

            scope.commitTransaction();
            // Test that remove got applied to shared cache
            assertTrue(null == cache.get("key_11"));

            cache.removeUsingPrefix("key");
            assert cache.getMap().size() == 0;

            scope.closeConnection();
        }


        protected void tearDown() throws Exception
        {
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }


        static class MyScope extends DbScope
        {
            Boolean overrideTransactionActive = null;
            Transaction overrideTransaction = null;
            
            MyScope()
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
                    return overrideTransactionActive;
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

            public void setOverrideTransactionActive(Boolean override)
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