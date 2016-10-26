package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
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
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.test.TestWhen.When.BVT;

/**
 *  Helper for creating materialized SQL into temp-tables with invalidation
 */
public class MaterializedQueryHelper implements CacheListener, AutoCloseable
{
    static private class Materialized
    {
        final long created;
        final String cacheKey;
        final String uptodateKey;
        final String fromSql;

        Materialized(String cacheKey, long created, String uptodate, String sql)
        {
            this.created = created;
            this.cacheKey = cacheKey;
            this.uptodateKey = uptodate;
            this.fromSql = sql;
        }
    }


    /**
     *  To be consistent with CacheManager maxTimeToCache==0 means UNLIMITED, so we use maxTimeToCache==-1 to mean no caching, just materialize and return
     *
     *  NOTE: the uptodate query should be very fast WRT the select query, the caller should use "uncache" as the primary means of making
     *  sure the materialized table is kept consistent.
     */
    public static MaterializedQueryHelper create(DbScope scope, SQLFragment select, @Nullable SQLFragment uptodate, Collection<String> indexes, long maxTimeToCache)
    {
        return new MaterializedQueryHelper(scope, select, uptodate, indexes, maxTimeToCache, false);
    }


/*  TODO, we're probably going to want this
    public static MaterializedQueryHelper createPerContainer(DbScope scope, SQLFragment select, @Nullable SQLFragment uptodate, long maxTimeToCache)
    {
        return new MaterializedQueryHelper(scope, select, uptodate, maxTimeToCache, true);
    }
*/

    private String makeKey(DbScope.Transaction t, Container c)
    {
        return (null == t ? "-" : t.getId()) + "/" + (null==c ? "-" : c.getRowId());
    }

    final DbScope scope;
    final SQLFragment selectQuery;
    final SQLFragment uptodateQuery;
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

    boolean closed = false;

    private MaterializedQueryHelper(DbScope scope, SQLFragment select, @Nullable SQLFragment uptodate, @Nullable Collection<String> indexes, long maxTimeToCache, boolean perContainer)
    {
        this.scope = scope;
        this.selectQuery = select;
        this.uptodateQuery = uptodate;
        this.maxTimeToCache = maxTimeToCache;
        this.perContainer = perContainer;
        if (null != indexes)
            this.indexes.addAll(indexes);
        if (perContainer)
            throw new UnsupportedOperationException("NYI");

        CacheManager.addListener(this);
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
     * TODO: if that's a problem we could put a maker into the cache
     *
     * @param c
     */
    public synchronized void uncache(final Container c)
    {
        final String txCacheKey = makeKey(scope.getCurrentTransaction(), c);
        map.remove(txCacheKey);
        if (scope.isTransactionActive())
            scope.getCurrentTransaction().addCommitTask(() -> map.remove(makeKey(null,c)));
    }


    // this is a method so you can subclass MaterializedQueryHelper
    protected String getUpToDateKey()
    {
        if (null != uptodateQuery)
            return new SqlSelector(scope, uptodateQuery).getObject(String.class);
        return null;
    }


    public synchronized SQLFragment getMaterializedTable(Container c)
    {
        if (closed)
            throw new IllegalStateException();
        if (null != c)
            throw new UnsupportedOperationException();

        final long now = HeartBeat.currentTimeMillis();
        final String txCacheKey = makeKey(scope.getCurrentTransaction(), c);
        Materialized materialized = null;

        if (scope.isTransactionActive())
            materialized = map.get(txCacheKey);

        if (null == materialized)
            materialized = map.get(makeKey(null,c));

        String uptodateKey = getUpToDateKey();

        if (null != materialized)
        {
            boolean old = maxTimeToCache == -1 || (maxTimeToCache != 0 && materialized.created + maxTimeToCache < now);
            boolean changed = null!=uptodateKey && !materialized.uptodateKey.equals(uptodateKey);
            if (old || changed)
            {
                map.remove(materialized.cacheKey);
                materialized = null;
            }
        }

        if (null == materialized)
        {
            DbSchema temp = DbSchema.getTemp();
            String name = "mat_" + GUID.makeHash();
            materialized = new Materialized(txCacheKey, now, uptodateKey, "SELECT * FROM \"" + temp.getName() + "\".\"" + name + "\"");
            TempTableTracker.track(name, materialized);

            SQLFragment selectInto = new SQLFragment("SELECT * INTO \"" + temp.getName() + "\".\"" + name + "\"\nFROM (\n");
            selectInto.append(selectQuery);
            selectInto.append("\n) _sql_");
            new SqlExecutor(scope).execute(selectInto);
            for (String index : indexes)
            {
                new SqlExecutor(scope).execute(StringUtils.replace(index,"${NAME}",name));
            }

            map.put(materialized.cacheKey, materialized);
        }

        if (scope.isTransactionActive())
        {
            scope.getCurrentTransaction().addCommitTask(() -> map.remove(txCacheKey));
        }

        SQLFragment sqlf = new SQLFragment(materialized.fromSql);
        sqlf.addTempToken(materialized);
        return sqlf;
    }


    @TestWhen(BVT)
    public static class TestCase extends Assert
    {
        @Before
        public void setup()
        {
            DbSchema temp = DbSchema.getTemp();
            try
            {
                new SqlExecutor(temp).execute("DROP TABLE temp.MQH_TESTCASE");
            }
            catch (DataAccessException x)
            {
                // pass
            }
            new SqlExecutor(temp).execute("CREATE TABLE temp.MQH_TESTCASE (x INT)");
        }

        @After
        public void cleanup()
        {
            DbSchema temp = DbSchema.getTemp();
            new SqlExecutor(temp).execute("DROP TABLE temp.MQH_TESTCASE");
        }

        @Test
        public void test()
        {
            DbSchema temp = DbSchema.getTemp();
            DbScope s = temp.getScope();
            SQLFragment select = new SQLFragment("SELECT * FROM temp.MQH_TESTCASE");
            SQLFragment uptodate = new SQLFragment("SELECT COALESCE(CAST(SUM(x) AS VARCHAR(40)),'-') FROM temp.MQH_TESTCASE");
            try (MaterializedQueryHelper  mqh = MaterializedQueryHelper.create(s,select,uptodate,null,CacheManager.UNLIMITED))
            {
                SQLFragment emptyA = mqh.getMaterializedTable(null);
                SQLFragment emptyB = mqh.getMaterializedTable(null);
                assertEquals(emptyA,emptyB);
                new SqlExecutor(temp).execute("INSERT INTO temp.MQH_TESTCASE (x) VALUES (1)");
                SQLFragment one = mqh.getMaterializedTable(null);
                assertNotEquals(emptyA, one);
            }
        }
    }
}
