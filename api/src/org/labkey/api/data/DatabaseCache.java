package org.labkey.api.data;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.labkey.api.util.TTLCacheMap;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
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

    TTLCacheMap<String, ValueType> ttlMap;
    DbScope scope;

    public DatabaseCache(DbScope scope, int maxSize, long defaultTimeToLive)
    {
        ttlMap = new TTLCacheMap<String,ValueType>(maxSize, defaultTimeToLive);
        this.scope = scope;
    }

    public DatabaseCache(DbScope scope, int maxSize)
    {
        this(scope,maxSize,-1);
    }

    public synchronized ValueType put(String key, ValueType value)
    {
        if (scope.isTransactionActive())
            return null;
        return ttlMap.put(key, value);
    }

    public synchronized ValueType put(String key, ValueType value, long timeToLive)
    {
        if (scope.isTransactionActive())
            return null;
        return ttlMap.put(key, value, timeToLive);
    }

    public synchronized ValueType get(String key)
    {
        if (scope.isTransactionActive())
        {
            // No-op if we're in the middle of a transaction as we're not seeing
            // the same view of the database as anyone else
            return null;
        }

        return ttlMap.get(key);
    }

    public synchronized void remove(String key)
    {
        if (scope.isTransactionActive())
            scope.addTransactedRemoval(this, key, false);
        else
            ttlMap.remove(key);
    }

    public synchronized void removeUsingPrefix(String prefix)
    {
        if (scope.isTransactionActive())
            scope.addTransactedRemoval(this, prefix, true);
        else
            ttlMap.removeUsingPrefix(prefix);
    }

    public synchronized void clear()
    {
        if (scope.isTransactionActive())
                scope.addTransactedRemoval(this);
        else
            ttlMap.clear();
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
            DatabaseCache<String> cache = new DatabaseCache<String>(scope, 10);

            // basic TTL testing

            // currently the TTLMapCache uses SoftReferences to values, so hold values here
            String[] values = new String[40];
            for (int i=0 ; i<values.length ; i++)
                values[i] = "value_" + i;

            for (int i=1 ; i<=20 ; i++)
            {
                cache.put("key_" + i, values[i]);
                assertTrue(cache.ttlMap.size() <= 10);
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

            assertTrue(cache.ttlMap.size() == 10);
            for (int i=11 ; i<=15 ; i++)
            {
                assertTrue(cache.get("key_" + i) == values[i]);
                assertTrue(cache.get("key_" + (i + 10)) == values[i+10]);
            }

            // transaction testing
            scope.beginTransaction();
            assertTrue(scope.isTransactionActive());
            assertTrue(null == cache.get("key_" + 11));
            cache.remove("key_" + 11);

            // imitate another thread: toggle transaction and test
            scope.setOverrideTransactionActive(Boolean.FALSE);
            assertTrue(cache.get("key_11") == values[11]);
            scope.setOverrideTransactionActive(null);

            scope.commitTransaction();
            assertTrue(null == cache.get("key_11"));

            cache.removeUsingPrefix("key");
            assert cache.ttlMap.size() == 0;

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

            public void setOverrideTransactionActive(Boolean override)
            {
                overrideTransactionActive = override;
            }
        }
    }

    public void setDebugName(String name)
    {
        ttlMap.setDebugName(name);
    }
}