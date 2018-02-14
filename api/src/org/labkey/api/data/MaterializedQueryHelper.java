/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.cache.CacheListener;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.MemTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.labkey.api.test.TestWhen.When.BVT;

/**
 *  Helper for creating materialized SQL into temp-tables with invalidation
 */
public class MaterializedQueryHelper implements CacheListener, AutoCloseable
{
    private class Materialized
    {
        final long created;
        final String cacheKey;
        final String fromSql;

        final ArrayList<Invalidator> invalidators = new ArrayList<>(3);

        Materialized(String cacheKey, long created, String sql)
        {
            this.created = created;
            this.cacheKey = cacheKey;
            this.fromSql = sql;
        }

        void addUpToDateQuery(SQLFragment uptodate)
        {
            if (null != uptodate)
                this.invalidators.add(new SqlInvalidator(uptodate));
        }

        void addMaxTimeToCache(long max)
        {
            if (max != CacheManager.UNLIMITED)
                this.invalidators.add(new TimeInvalidator(max));
        }

        void addInvalidator(Supplier<String> sup)
        {
            if (null != sup)
                this.invalidators.add(new SupplierInvalidator(sup));
        }

        void reset()
        {
            long now = HeartBeat.currentTimeMillis();
            for (Invalidator i : invalidators)
                i.stillValid(now);
        }
    }

    enum CacheCheck
    {
        OK,             // looks fine
        COALESCE,       // invalid, but within coalesce/delay window
                        // a) treat COALESCE==OK,
                        // b) treat COALESCE==INVALID,
                        // c) return cached value but invalidate cache
                        // d) return cached value and reload in background
        INVALID         // invalid
    }

    private abstract class Invalidator
    {
        int coalesceDelay = 0;

        CacheCheck checkValid(long createdTime)
        {
            boolean valid = stillValid(createdTime);
            if (valid)
                return CacheCheck.OK;
            if (coalesceDelay == 0 || createdTime+coalesceDelay < HeartBeat.currentTimeMillis())
                return CacheCheck.INVALID;
            return CacheCheck.COALESCE;
        }
        abstract boolean stillValid(long createdTime);
    }


    private class SqlInvalidator extends Invalidator
    {
        final SQLFragment upToDateSql;
        final AtomicReference<String> result = new AtomicReference<>();

        SqlInvalidator(SQLFragment sql)
        {
            this.upToDateSql = sql;
        }

        /* Has anything changed since last time stillValid() was called, this method must keep its own state */
        @Override
        boolean stillValid(long createdTime)
        {
            String prevResult = result.get();
            String newResult = new SqlSelector(scope, uptodateQuery).getObject(String.class);
            if (StringUtils.equals(prevResult,newResult))
                return true;
            result.set(newResult);
            return false;
        }
    }


    private class TimeInvalidator extends Invalidator
    {
        final long maxTime;

        TimeInvalidator(long t)
        {
            this.maxTime = t;
        }

        @Override
        boolean stillValid(long createdTime)
        {
            return maxTime != -1 && createdTime + maxTime > HeartBeat.currentTimeMillis();
        }
    }


    private class SupplierInvalidator extends Invalidator
    {
        final Supplier<String> supplier;
        final AtomicReference<String> result = new AtomicReference<>();

        SupplierInvalidator(Supplier<String> sup)
        {
            this.supplier = sup;
        }

        @Override
        boolean stillValid(long createdTime)
        {
            String prevResult = result.get();
            String newResult = supplier.get();
            if (StringUtils.equals(prevResult,newResult))
                return true;
            result.set(newResult);
            return false;
        }
    }



    private String makeKey(DbScope.Transaction t, Container c)
    {
        return (null == t ? "-" : t.getId()) + "/" + (null==c ? "-" : c.getRowId());
    }

    final String prefix;
    final DbScope scope;
    final SQLFragment selectQuery;
    final SQLFragment uptodateQuery;
    final Supplier<String> supplier;
    final List<String> indexes = new ArrayList<>();
    final long maxTimeToCache;
    final boolean perContainer;
    final LinkedHashMap<String,Materialized> map = new LinkedHashMap<String,Materialized>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Materialized> eldest)
        {
            if (maxTimeToCache == 0) return false;
            if (maxTimeToCache == -1) return true;
            return eldest.getValue().created + maxTimeToCache < HeartBeat.currentTimeMillis();
        }
    };
    // DEBUG variables
    final AtomicInteger countGetFromSql = new AtomicInteger();
    final AtomicInteger countSelectInto = new AtomicInteger();
    final AtomicLong lastUsed = new AtomicLong(HeartBeat.currentTimeMillis());

    boolean closed = false;

    private MaterializedQueryHelper(String prefix, DbScope scope, SQLFragment select, @Nullable SQLFragment uptodate, Supplier<String> supplier, @Nullable Collection<String> indexes, long maxTimeToCache,
                                    boolean perContainer)
    {
        this.prefix = StringUtils.defaultString(prefix,"mat");
        this.scope = scope;
        this.selectQuery = select;
        this.uptodateQuery = uptodate;
        this.supplier = supplier;
        this.maxTimeToCache = maxTimeToCache;
        this.perContainer = perContainer;
        if (null != indexes)
            this.indexes.addAll(indexes);
        if (perContainer)
            throw new UnsupportedOperationException("NYI");
        assert MemTracker.get().put(this);
    }


    /** this is only expected to be used for test cases */
    @Override
    public void close()
    {
        CacheManager.removeListener(this);
        closed = true;
    }


    @Override
    public void clearCaches()
    {
        map.clear();
    }

    /**
     * NOTE: invalidating within a transaction, may NOT force re-materialize for subsequent call within the transaction
     * because it could re-use the global cached result.
     *
     * TODO: if that's a problem we could put a marker into the cache
     */
    public synchronized void uncache(final Container c)
    {
        final String txCacheKey = makeKey(scope.getCurrentTransaction(), c);
        map.remove(txCacheKey);
        if (scope.isTransactionActive())
            scope.getCurrentTransaction().addCommitTask(() -> map.remove(makeKey(null,c)), DbScope.CommitTaskOption.POSTCOMMIT);
    }


    Set<Integer> pending = null;

    /** call uncache() first to force reload */
    public synchronized void cacheInBackground(final Container c)
    {
        if (closed)
            throw new IllegalStateException();
        if (null != c)
            throw new UnsupportedOperationException();

        if (pending == null)
            pending = new HashSet<>();
        if (!pending.add(null==c ? 0 : c.getRowId()))
            return;
        JobRunner.getDefault().execute(() ->
        {
            _runCacheInBackground(c);
        });
    }


    private synchronized void _runCacheInBackground(Container c)
    {
        pending.remove(null==c ? 0 : c.getRowId());
        if (pending.isEmpty())
            pending = null;
        getFromSql(null, c);
    }


    // this is a method so you can subclass MaterializedQueryHelper
    protected String getUpToDateKey()
    {
        if (null != uptodateQuery)
            return new SqlSelector(scope, uptodateQuery).getObject(String.class);
        return null;
    }

    public SQLFragment getFromSql(String tableAlias, Container c)
    {
        if (null == selectQuery)
            throw new IllegalStateException("Must specify source query in constructor or in getFromSql()");
        return getFromSql(selectQuery, tableAlias, c);
    }

    /* NOTE: we do not want to hold synchronized(this) while doing any SQL operations */
    public SQLFragment getFromSql(@NotNull SQLFragment selectQuery, String tableAlias, Container c)
    {
        Materialized materialized = null;
        final String txCacheKey = makeKey(scope.getCurrentTransaction(), c);
        final long now = HeartBeat.currentTimeMillis();

        synchronized (this)
        {
            if (closed)
                throw new IllegalStateException();
            if (null != c)
                throw new UnsupportedOperationException();

            this.countGetFromSql.incrementAndGet();

            if (scope.isTransactionActive())
                materialized = map.get(txCacheKey);

            if (null == materialized)
                materialized = map.get(makeKey(null, c));
        }

        if (null != materialized)
        {
            boolean replace = false;
            for (Invalidator i : materialized.invalidators)
            {
                CacheCheck cc = i.checkValid(materialized.created);
                if (cc != CacheCheck.OK)
                    replace = true;
            }
            if (replace)
            {
                synchronized (this)
                {
                    map.remove(materialized.cacheKey);
                    materialized = null;
                }
            }
        }

        if (null == materialized)
        {
            countSelectInto.incrementAndGet();
            DbSchema temp = DbSchema.getTemp();
            String name = prefix + "_" + GUID.makeHash();
            materialized = new Materialized(txCacheKey, now, "\"" + temp.getName() + "\".\"" + name + "\"");
            materialized.addMaxTimeToCache(this.maxTimeToCache);
            materialized.addUpToDateQuery(this.uptodateQuery);
            materialized.addInvalidator(this.supplier);
            // CONSIDER: copy over old validators (if previous materialized) so we don't need to reset
            materialized.reset();

            TempTableTracker.track(name, materialized);

            SQLFragment selectInto = new SQLFragment("SELECT * INTO \"" + temp.getName() + "\".\"" + name + "\"\nFROM (\n");
            selectInto.append(selectQuery);
            selectInto.append("\n) _sql_");
            new SqlExecutor(scope).execute(selectInto);
            for (String index : indexes)
            {
                new SqlExecutor(scope).execute(StringUtils.replace(index,"${NAME}",name));
            }

            synchronized (this)
            {
                map.put(materialized.cacheKey, materialized);
            }
        }

        if (scope.isTransactionActive())
        {
            scope.getCurrentTransaction().addCommitTask(() -> map.remove(txCacheKey), DbScope.CommitTaskOption.POSTCOMMIT);
        }

        lastUsed.set(HeartBeat.currentTimeMillis());
        SQLFragment sqlf = new SQLFragment(materialized.fromSql);
        if (!StringUtils.isBlank(tableAlias))
            sqlf.append(" " ).append(tableAlias);
        sqlf.addTempToken(materialized);
        return sqlf;
    }


    /**
     *  To be consistent with CacheManager maxTimeToCache==0 means UNLIMITED, so we use maxTimeToCache==-1 to mean no caching, just materialize and return
     *
     *  NOTE: the uptodate query should be very fast WRT the select query, the caller should use "uncache" as the primary means of making
     *  sure the materialized table is kept consistent.
     *
     *  If you are not putting your MQH in a Cache, you may want to do
     *    CacheManager.addListener(mqh);
     *
     *  that will hook up your MQH to the global cache clear event
     */

    @Deprecated // use Builder
    public static MaterializedQueryHelper create(String prefix, DbScope scope, SQLFragment select, @Nullable SQLFragment uptodate, Collection<String> indexes, long maxTimeToCache)
    {
        return new MaterializedQueryHelper(prefix, scope, select, uptodate, null, indexes, maxTimeToCache, false);
    }


    @Deprecated // use Builder
    public static MaterializedQueryHelper create(String prefix, DbScope scope, SQLFragment select, Supplier<String> uptodate, Collection<String> indexes, long maxTimeToCache)
    {
        return new MaterializedQueryHelper(prefix, scope, select, null, uptodate, indexes, maxTimeToCache, false);
    }


    public static class Builder implements org.labkey.api.data.Builder<MaterializedQueryHelper>
    {
        final String prefix;
        final DbScope scope;
        final SQLFragment select;
        long max = CacheManager.UNLIMITED;
        SQLFragment uptodate = null;
        Supplier supplier = null;
        Collection<String> indexes = new ArrayList<>();

        public Builder(String prefix, DbScope scope, SQLFragment select)
        {
            this.prefix = prefix;
            this.scope = scope;
            this.select = select;
        }

        public Builder upToDateSql(SQLFragment uptodate)
        {
            this.uptodate = uptodate;
            return this;
        }

        public Builder maxTimeToCache(long max)
        {
            this.max = max;
            return this;
        }

        public Builder addInvalidCheck(Supplier<String> supplier)
        {
            this.supplier = supplier;
            return this;
        }

        public Builder addIndex(String index)
        {
            this.indexes.add(index);
            return this;
        }

        @Override
        public MaterializedQueryHelper build()
        {
            return new MaterializedQueryHelper(prefix, scope, select,  uptodate, supplier, indexes, max, false);

        }
    }


    @TestWhen(BVT)
    public static class TestCase extends Assert
    {
        @Before
        public void setup()
        {
            DbSchema temp = DbSchema.getTemp();
            temp.dropTableIfExists("MQH_TESTCASE");
            new SqlExecutor(temp).execute("CREATE TABLE temp.MQH_TESTCASE (x INT)");
        }

        @After
        public void cleanup()
        {
            DbSchema temp = DbSchema.getTemp();
            new SqlExecutor(temp).execute("DROP TABLE temp.MQH_TESTCASE");
        }

        @Test
        public void testSimple()
        {
            DbSchema temp = DbSchema.getTemp();
            DbScope s = temp.getScope();
            SQLFragment select = new SQLFragment("SELECT * FROM temp.MQH_TESTCASE");
            SQLFragment uptodate = new SQLFragment("SELECT COALESCE(CAST(SUM(x) AS VARCHAR(40)),'-') FROM temp.MQH_TESTCASE");
            try (MaterializedQueryHelper  mqh = MaterializedQueryHelper.create("test", s,select,uptodate,null,CacheManager.UNLIMITED))
            {
                SQLFragment emptyA = mqh.getFromSql("_", null);
                SQLFragment emptyB = mqh.getFromSql("_", null);
                assertEquals(emptyA,emptyB);
                new SqlExecutor(temp).execute("INSERT INTO temp.MQH_TESTCASE (x) VALUES (1)");
                SQLFragment one = mqh.getFromSql("_", null);
                assertNotEquals(emptyA, one);
            }
        }

        @Test
        public void testThreads() throws Exception
        {
            final AtomicReference<Exception> error = new AtomicReference<>();
            final AtomicInteger queries = new AtomicInteger();
            final long stop = System.currentTimeMillis() + 5000;
            final Random r = new Random();

            DbSchema temp = DbSchema.getTemp();
            DbScope s = temp.getScope();
            SQLFragment select = new SQLFragment("SELECT x, x*x as y FROM temp.MQH_TESTCASE");
            SQLFragment uptodate = new SQLFragment("SELECT COALESCE(CAST(SUM(x) AS VARCHAR(40)),'-') FROM temp.MQH_TESTCASE");
            try (final MaterializedQueryHelper  mqh = MaterializedQueryHelper.create("test", s,select,uptodate,null,CacheManager.UNLIMITED))
            {
                Runnable inserter = () ->
                {
                    try
                    {
                        while (System.currentTimeMillis() < stop)
                        {
                            new SqlExecutor(temp).execute("INSERT INTO temp.MQH_TESTCASE (x) VALUES (" + r.nextInt()%1000 + ")");
                            queries.incrementAndGet();
                            try { Thread.sleep(100); } catch (InterruptedException x) {/* */}
                        }
                    }
                    catch (Exception x)
                    {
                        error.set(x);
                    }
                };
                Runnable reader = () ->
                {
                    try
                    {
                        while (System.currentTimeMillis() < stop)
                        {
                            new SqlSelector(temp, new SQLFragment("SELECT * FROM ").append(mqh.getFromSql("_", null))).getMapCollection();
                            queries.incrementAndGet();
                        }
                    }
                    catch (Exception x)
                    {
                        error.set(x);
                    }
                };

                List<Thread> list = new ArrayList<>();
                _run(list, inserter);
                for (int i=1 ; i<3 ; i++)
                    _run(list, reader);
                _join(list);
                Exception x = error.get();
                if (null != x)
                    throw x;
            }
        }

        private void _run(List<Thread> list, Runnable r)
        {
            Thread t = new Thread(r);
            list.add(t);
            t.start();
        }
        private void _join(List<Thread> list)
        {
            list.forEach((t) -> {try{t.join();}catch(InterruptedException x){/* */}});
        }
    }
}
