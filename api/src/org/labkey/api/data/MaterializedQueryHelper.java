/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.labkey.api.action.SpringActionController;
import org.labkey.api.cache.CacheListener;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.UnexpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.labkey.api.test.TestWhen.When.BVT;

/**
 *  Helper for creating materialized SQL into temp-tables with invalidation
 */
public class MaterializedQueryHelper implements CacheListener, AutoCloseable
{
    public static class Materialized
    {
        MaterializedQueryHelper _mqh;
        private final long _created;
        private final String _cacheKey;
        private final String _fromSql;
        private final String _tableName;
        private final ArrayList<Invalidator> _invalidators = new ArrayList<>(3);

        private final Lock _loadingLock = new ReentrantLock();
        enum LoadingState { BEFORELOAD, LOADING, LOADED, ERROR };
        private final AtomicReference<LoadingState> _loadingState = new AtomicReference<>(LoadingState.BEFORELOAD);
        private RuntimeException _loadException = null;


        public Materialized(MaterializedQueryHelper parent, String tableName, String cacheKey, long created, String sql)
        {
            _mqh = parent;
            _created = created;
            _cacheKey = cacheKey;
            _tableName = tableName;
            _fromSql = sql;
        }

        public void addUpToDateQuery(SQLFragment uptodate)
        {
            if (null != uptodate)
                _invalidators.add(new SqlInvalidator(_mqh, uptodate));
        }

        public void addMaxTimeToCache(long max)
        {
            if (max != CacheManager.UNLIMITED)
                _invalidators.add(new TimeInvalidator(max));
        }

        public void addInvalidator(Supplier<String> sup)
        {
            if (null != sup)
                _invalidators.add(new SupplierInvalidator(sup));
        }

        public void reset()
        {
            long now = HeartBeat.currentTimeMillis();
            for (Invalidator i : _invalidators)
                i.stillValid(now);
        }


        /** return false if we did not acquire the loadingLock */
        boolean load(SQLFragment selectQuery, boolean isSelectInto)
        {
            boolean lockAcquired = false;
            try
            {
                try
                {
                    if (!(lockAcquired = _loadingLock.tryLock(5, TimeUnit.MINUTES)))
                        return false;
                }
                catch (InterruptedException x)
                {
                    throw UnexpectedException.wrap(x);
                }

                if (LoadingState.LOADED == _loadingState.get())
                    return true;
                if (null != _loadException)
                    throw _loadException;

                assert LoadingState.LOADING != _loadingState.get();
                _loadingState.set(LoadingState.LOADING);

                DbSchema temp = DbSchema.getTemp();
                TempTableTracker.track(_tableName, this);

                SQLFragment selectInto;
                if (isSelectInto)
                {
                    String sql = selectQuery.getSQL().replace("${NAME}", _tableName);
                    List<Object> params = selectQuery.getParams();
                    selectInto = new SQLFragment(sql,params);
                }
                else
                {
                    selectInto = new SQLFragment("SELECT * INTO ").appendIdentifier("\"" + temp.getName() + "\"").append(".").appendIdentifier("\"" + _tableName + "\"").append("\nFROM (\n");
                    selectInto.append(selectQuery);
                    selectInto.append("\n) _sql_");
                }
                new SqlExecutor(_mqh._scope).execute(selectInto);

                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    for (String index : _mqh._indexes)
                    {
                        new SqlExecutor(_mqh._scope).execute(StringUtils.replace(index, "${NAME}", _tableName));
                    }
                }

                _loadingState.set(LoadingState.LOADED);
                return true;
            }
            catch (RuntimeException rex)
            {
                _loadException = rex;
                _loadingState.set(LoadingState.ERROR);
                throw _loadException;
            }
            finally
            {
                if (lockAcquired)
                    _loadingLock.unlock();
            }
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

    public static abstract class Invalidator
    {
        private int _coalesceDelay = 0;

        public CacheCheck checkValid(long createdTime)
        {
            boolean valid = stillValid(createdTime);
            if (valid)
                return CacheCheck.OK;
            if (_coalesceDelay == 0 || createdTime + _coalesceDelay < HeartBeat.currentTimeMillis())
                return CacheCheck.INVALID;
            return CacheCheck.COALESCE;
        }
        public abstract boolean stillValid(long createdTime);
    }


    private static class SqlInvalidator extends Invalidator
    {
        final MaterializedQueryHelper mqh;
        final SQLFragment upToDateSql;
        final AtomicReference<String> result = new AtomicReference<>();

        SqlInvalidator(MaterializedQueryHelper mqh, SQLFragment sql)
        {
            this.mqh = mqh;
            this.upToDateSql = sql;
        }

        /* Has anything changed since last time stillValid() was called, this method must keep its own state */
        @Override
        public boolean stillValid(long createdTime)
        {
            String prevResult = result.get();
            String newResult = new SqlSelector(mqh._scope, mqh._uptodateQuery).getObject(String.class);
            if (StringUtils.equals(prevResult,newResult))
                return true;
            result.set(newResult);
            return false;
        }
    }


    private static class TimeInvalidator extends Invalidator
    {
        private final long _maxTime;

        TimeInvalidator(long t)
        {
            _maxTime = t;
        }

        @Override
        public boolean stillValid(long createdTime)
        {
            return _maxTime != -1 && createdTime + _maxTime > HeartBeat.currentTimeMillis();
        }
    }


    public static class SupplierInvalidator extends Invalidator
    {
        private final Supplier<String> _supplier;
        private final AtomicReference<String> _result = new AtomicReference<>();

        public SupplierInvalidator(Supplier<String> sup)
        {
            _supplier = sup;
        }

        @Override
        public boolean stillValid(long createdTime)
        {
            String prevResult = _result.get();
            String newResult = _supplier.get();
            if (StringUtils.equals(prevResult,newResult))
                return true;
            _result.set(newResult);
            return false;
        }
    }


    private String makeKey(DbScope.Transaction t)
    {
        return (null == t ? "-" : t.getId());
    }


    protected final String _prefix;
    protected final DbScope _scope;
    private final SQLFragment _selectQuery;
    private final boolean _isSelectIntoSql;
    protected final SQLFragment _uptodateQuery;
    protected final Supplier<String> _supplier;
    private final List<String> _indexes = new ArrayList<>();
    protected final long _maxTimeToCache;
    private final Map<String, Materialized> _map = Collections.synchronizedMap(new LinkedHashMap<>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Materialized> eldest)
        {
            if (_maxTimeToCache == 0) return false;
            if (_maxTimeToCache == -1) return true;
            return eldest.getValue()._created + _maxTimeToCache < HeartBeat.currentTimeMillis();
        }
    });
    private final ReentrantLock materializeLock = new ReentrantLock();

    // DEBUG variables
    private final AtomicInteger _countGetFromSql = new AtomicInteger();
    private final AtomicInteger _countSelectInto = new AtomicInteger();
    private final AtomicLong _lastUsed = new AtomicLong(HeartBeat.currentTimeMillis());

    private boolean _closed = false;

    protected MaterializedQueryHelper(String prefix, DbScope scope, SQLFragment select, @Nullable SQLFragment uptodate, Supplier<String> supplier, @Nullable Collection<String> indexes, long maxTimeToCache,
                                    boolean isSelectIntoSql)
    {
        _prefix = StringUtils.defaultString(prefix,"mat");
        _scope = scope;
        _selectQuery = select;
        _uptodateQuery = uptodate;
        _supplier = supplier;
        _maxTimeToCache = maxTimeToCache;
        if (null != indexes)
            _indexes.addAll(indexes);
        _isSelectIntoSql = isSelectIntoSql;
        assert MemTracker.get().put(this);
    }


    /** this is only expected to be used for test cases */
    @Override
    public void close()
    {
        CacheManager.removeListener(this);
        _closed = true;
    }


    @Override
    public void clearCaches()
    {
        _map.clear();
    }

    /**
     * NOTE: invalidating within a transaction, may NOT force re-materialize for subsequent call within the transaction
     * because it could re-use the global cached result.
     *
     * TODO: if that's a problem we could put a marker into the cache
     */
    public synchronized void uncache(final Container c)
    {
        assert null == c;
        try
        {
            materializeLock.lock();
            final String txCacheKey = makeKey(_scope.getCurrentTransaction());
            _map.remove(txCacheKey);
            if (_scope.isTransactionActive())
                _scope.getCurrentTransaction().addCommitTask(() -> _map.remove(makeKey(null)), DbScope.CommitTaskOption.POSTCOMMIT);
        }
        finally
        {
            materializeLock.unlock();
        }
    }


    private Set<Integer> _pending = null;

    // this is a method so you can subclass MaterializedQueryHelper
    protected String getUpToDateKey()
    {
        if (null != _uptodateQuery)
            return new SqlSelector(_scope, _uptodateQuery).getObject(String.class);
        return null;
    }

    public SQLFragment getFromSql(String tableAlias)
    {
        if (null == _selectQuery)
            throw new IllegalStateException("Must specify source query in constructor or in getFromSql()");
        return getFromSql(_selectQuery, _isSelectIntoSql, tableAlias);
    }

    public SQLFragment getViewSourceSql()
    {
        return new SQLFragment(_selectQuery);
    }


    public int upsert(SQLFragment sqlf)
    {
        // We want to avoid materializing the table if it has never been used.
        if (null == _map.get(makeKey(null)))
        {
            var tx = _scope.getCurrentTransaction();
            if (null == tx || null == _map.get(makeKey(tx)))
                return -1;
        }
        // We also don't want to execute the update if it will be materialized by the next user (e.g. after invalidation check)
        Materialized m = getMaterialized(true);
        if (Materialized.LoadingState.BEFORELOAD == m._loadingState.get())
            return -1;

        // execute incremental update
        SQLFragment copy = new SQLFragment(sqlf);
        copy.setSqlUnsafe(sqlf.getSQL().replace("${NAME}", m._tableName));
        try (var ignored = SpringActionController.ignoreSqlUpdates())
        {
            return new SqlExecutor(_scope).execute(copy);
        }
    }


    /* used by FLow directly for some reason */
    public SQLFragment getFromSql(@NotNull SQLFragment selectQuery, String tableAlias)
    {
        return getFromSql(selectQuery, false, tableAlias);
    }


    public SQLFragment getFromSql(@NotNull SQLFragment selectQuery, boolean isSelectInto, String tableAlias)
    {
        Materialized materialized = getMaterializedAndLoad(selectQuery, isSelectInto);

        // CONSIDER: should we set materialized into LOADING status (with locks and everything???).  That may be overkill;
        incrementalUpdateBeforeSelect(materialized);

        _lastUsed.set(HeartBeat.currentTimeMillis());
        SQLFragment sqlf = new SQLFragment(materialized._fromSql);
        if (!StringUtils.isBlank(tableAlias))
            sqlf.append(" ").append(tableAlias);
        sqlf.addTempToken(materialized);
        _countGetFromSql.incrementAndGet();
        return sqlf;
    }


    protected void incrementalUpdateBeforeSelect(Materialized m)
    {
    }


    /**
     * A Materialized represents a particular instance of materialized view (stored in a temp table).
     * We want to avoid two threads materializing the same view.  This is why we synchronize first creating the
     * Materialized and then executing the SELECT INTO.  This is conceptually simple.  The tricky part is error
     * handling.  When something goes wrong fall-back on using non-shared Materialized objects (and therefore non-shared temp
     * tables) and toss out the cached Materialized if appropriate.
     */
    @NotNull
    private Materialized getMaterialized(boolean forWrite)
    {
        if (_closed)
            throw new IllegalStateException();

        Materialized materialized = null;
        String txCacheKey = makeKey(_scope.getCurrentTransaction());
        boolean hasLock = false;

        try
        {
            try
            {
                hasLock = materializeLock.tryLock(1, TimeUnit.MINUTES);
            }
            catch (InterruptedException x)
            {
                throw UnexpectedException.wrap(x);
            }

            // If we fail to get a lock, it could be due to contention on other threads.  We will
            // skip using the cache and create a new one-time-use Materialized object.

            if (hasLock)
            {
                if (!_scope.isTransactionActive())
                {
                    materialized = _map.get(makeKey(null));
                    if (null != materialized && Materialized.LoadingState.ERROR == materialized._loadingState.get())
                        materialized = null;
                }
                else
                {
                    materialized = _map.get(txCacheKey);
                    if (null != materialized && Materialized.LoadingState.ERROR == materialized._loadingState.get())
                        materialized = null;
                    if (null == materialized && !forWrite)
                        materialized = _map.get(makeKey(null));
                    if (null != materialized && Materialized.LoadingState.ERROR == materialized._loadingState.get())
                        materialized = null;
                }

                if (null != materialized)
                {
                    boolean replace = false;
                    for (Invalidator i : materialized._invalidators)
                    {
                        CacheCheck cc = i.checkValid(materialized._created);
                        if (cc != CacheCheck.OK)
                            replace = true;
                    }
                    if (replace)
                        materialized = null;
                }
            }

            if (null == materialized)
            {
                materialized = createMaterialized(txCacheKey);
                if (hasLock)
                    _map.put(materialized._cacheKey, materialized);
            }

            return materialized;
        }
        finally
        {
            if (hasLock)
                materializeLock.unlock();
        }
    }


    private Materialized getMaterializedAndLoad(SQLFragment selectQuery, boolean isSelectIntoSql)
    {
        Materialized materialized = getMaterialized(false);

        if (materialized.load(selectQuery, isSelectIntoSql))
            return materialized;

        // If there was a problem (but no thrown exception), try one more time from scratch;
        materialized = createMaterialized(materialized._cacheKey);
        if (materialized.load(selectQuery, isSelectIntoSql))
            return materialized;

        // load() shouldn't get here.  Materialized is not shared, so there should be no way to get a timeout.
        throw new IllegalStateException("Failed to create materialized table");
    }

    protected Materialized createMaterialized(String txCacheKey)
    {
        DbSchema temp = DbSchema.getTemp();
        String name = _prefix + "_" + GUID.makeHash();
        Materialized materialized = new Materialized(this, name, txCacheKey, HeartBeat.currentTimeMillis(), "\"" + temp.getName() + "\".\"" + name + "\"");
        initMaterialized(materialized);
        return materialized;
    }

    protected void initMaterialized(Materialized materialized)
    {
        materialized.addMaxTimeToCache(_maxTimeToCache);
        materialized.addUpToDateQuery(_uptodateQuery);
        materialized.addInvalidator(_supplier);
        materialized.reset();
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
        protected final String _prefix;
        protected final DbScope _scope;
        protected final SQLFragment _select;

        protected boolean _isSelectInto = false;
        protected long _max = CacheManager.UNLIMITED;
        protected SQLFragment _uptodate = null;
        protected Supplier<String> _supplier = null;
        protected Collection<String> _indexes = new ArrayList<>();

        public Builder(String prefix, DbScope scope, SQLFragment select)
        {
            _prefix = prefix;
            _scope = scope;
            _select = select;
        }

        /** This property indicates that the SQLFragment is formatted as a SELECT INTO query (rather than a simple SELECT) */
        public Builder setIsSelectInto(boolean b)
        {
            _isSelectInto = b;
            return this;
        }

        public Builder upToDateSql(SQLFragment uptodate)
        {
            _uptodate = uptodate;
            return this;
        }

        public Builder maxTimeToCache(long max)
        {
            _max = max;
            return this;
        }

        public Builder addInvalidCheck(Supplier<String> supplier)
        {
            _supplier = supplier;
            return this;
        }

        public Builder addIndex(String index)
        {
            _indexes.add(index);
            return this;
        }

        @Override
        public MaterializedQueryHelper build()
        {
            return new MaterializedQueryHelper(_prefix, _scope, _select, _uptodate, _supplier, _indexes, _max, _isSelectInto);
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
                SQLFragment emptyA = mqh.getFromSql("_");
                SQLFragment emptyB = mqh.getFromSql("_");
                assertEquals(emptyA,emptyB);
                new SqlExecutor(temp).execute("INSERT INTO temp.MQH_TESTCASE (x) VALUES (1)");
                SQLFragment one = mqh.getFromSql("_");
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
                            new SqlSelector(temp, new SQLFragment("SELECT * FROM ").append(mqh.getFromSql("_"))).getMapCollection();
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
